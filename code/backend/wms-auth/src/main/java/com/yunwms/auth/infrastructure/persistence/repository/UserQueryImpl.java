package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.model.UserRoleGrant;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.model.UserSummary;
import com.yunwms.auth.domain.port.OwnerNameQuery;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;
import com.yunwms.auth.infrastructure.persistence.entity.UserRoleGrantRow;
import com.yunwms.auth.infrastructure.persistence.entity.UserSummaryRow;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link UserQuery} 的实现。
 *
 * <p>列表做多 <b>4 条</b> SQL：计数、分页、整页的角色授权，加上页内有货主账号时的一条货主名称
 * （整页都是内部员工时这条省掉）。条数与页内行数无关——逐行补角色与货主名称会让它变成
 * {@code 2 + 2n}，这是本类唯一需要守住的性质，由集成测试的语句计数断言看着。
 *
 * <p>详情 2 到 3 条：账号行（{@code selectById}，自带逻辑删除条件）、角色授权、货主名称。
 *
 * <p>状态与主体类型的中文说明不做在这里：{@code UserStatus#fromCode} / {@code SubjectType#fromCode}
 * 遇到未知编码直接抛异常。这是刻意的——库里出现 {@code 'FOO'} 说明数据被绕过 CHECK 写坏，
 * 静默回落到「启用」会让一个本该停用的账号继续可登录。
 */
@Repository
public class UserQueryImpl implements UserQuery {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final OwnerNameQuery ownerNameQuery;

    public UserQueryImpl(SysUserMapper sysUserMapper,
                         SysUserRoleMapper sysUserRoleMapper,
                         OwnerNameQuery ownerNameQuery) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.ownerNameQuery = ownerNameQuery;
    }

    @Override
    public long count(SubjectType subjectType, UserStatus status, String keyword) {
        return sysUserMapper.countByFilter(codeOf(subjectType), codeOf(status), keyword);
    }

    @Override
    public List<UserSummary> findPage(SubjectType subjectType, UserStatus status, String keyword,
                                      int limit, long offset) {
        List<UserSummaryRow> rows = sysUserMapper.selectSummaryPage(
                codeOf(subjectType), codeOf(status), keyword, limit, offset);
        if (rows.isEmpty()) {
            // 空页直接返回：再发两条 IN 查询去取「这一页的角色与货主」纯属浪费
            return List.of();
        }
        Map<Long, List<String>> roleCodesByUser = roleCodesByUser(
                rows.stream().map(UserSummaryRow::getId).toList());
        Map<Long, String> ownerNames = ownerNamesOf(
                rows.stream().map(UserSummaryRow::getOwnerId).filter(Objects::nonNull).toList());
        return rows.stream()
                .map(row -> new UserSummary(
                        row.getId(),
                        row.getLoginName(),
                        row.getDisplayName(),
                        SubjectType.fromCode(row.getSubjectType()),
                        row.getOwnerId(),
                        ownerNameOf(row.getOwnerId(), ownerNames),
                        UserStatus.fromCode(row.getStatus()),
                        roleCodesByUser.getOrDefault(row.getId(), List.of()),
                        row.getLastLoginAt()))
                .toList();
    }

    @Override
    public Optional<UserProfile> findById(long userId) {
        // selectById 会带上逻辑删除条件：被软删除的账号必须和「不存在」同口径
        SysUserEntity entity = sysUserMapper.selectById(userId);
        if (entity == null) {
            return Optional.empty();
        }
        List<UserRoleGrant> grants = grantsOf(List.of(userId));
        Map<Long, String> ownerNames = ownerNamesOf(
                entity.getOwnerId() == null ? List.of() : List.of(entity.getOwnerId()));
        UserSummary summary = new UserSummary(
                entity.getId(),
                entity.getLoginName(),
                entity.getDisplayName(),
                SubjectType.fromCode(entity.getSubjectType()),
                entity.getOwnerId(),
                ownerNameOf(entity.getOwnerId(), ownerNames),
                UserStatus.fromCode(entity.getStatus()),
                grants.stream().map(UserRoleGrant::roleCode).toList(),
                entity.getLastLoginAt());
        return Optional.of(new UserProfile(
                summary,
                entity.getEmail(),
                entity.getWecomUserId(),
                // 用户级覆盖值原样返回（可为 null = 继承角色默认）：数据范围的枚举与校验属于 2.3/2.4，
                // 本批如果在这里替它挑一个默认值，等于提前把「没配置」说成「配置成了某一个值」
                entity.getDataScopeType(),
                entity.getCreatedAt(),
                grants.stream().map(UserRoleGrant::roleId).toList()));
    }

    @Override
    public boolean existsById(long userId) {
        // 只数行而不取详情：selectCount 同样带逻辑删除条件，且不需要把整行读回来
        return sysUserMapper.selectCount(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getId, userId)) > 0;
    }

    /**
     * 取一页用户的角色编码，按用户分组。
     *
     * @param userIds 本页用户标识，调用方保证非空
     * @return 用户标识到角色编码列表的映射；没有角色的用户不出现在结果里
     */
    private Map<Long, List<String>> roleCodesByUser(Collection<Long> userIds) {
        Map<Long, List<String>> codesByUser = new LinkedHashMap<>();
        for (UserRoleGrant grant : grantsOf(userIds)) {
            codesByUser.computeIfAbsent(grant.userId(), key -> new ArrayList<>())
                    .add(grant.roleCode());
        }
        return codesByUser;
    }

    /**
     * 取一批用户的角色授权。
     *
     * @param userIds 用户标识，调用方保证非空
     * @return 授权行，按用户与角色排序号升序
     */
    private List<UserRoleGrant> grantsOf(Collection<Long> userIds) {
        return sysUserRoleMapper.selectGrantsByUserIds(userIds).stream()
                .map(UserQueryImpl::toGrant)
                .toList();
    }

    /**
     * 把货主标识换成名称。
     *
     * @param ownerIds 货主标识，可为空集合（内部员工不绑定货主）
     * @return 货主标识到名称的映射；入参为空时为空映射且不发查询
     */
    private Map<Long, String> ownerNamesOf(Collection<Long> ownerIds) {
        if (ownerIds.isEmpty()) {
            // 一页全是内部员工时不该为「取货主名称」付一次往返
            return Map.of();
        }
        return ownerNameQuery.findNamesByIds(ownerIds);
    }

    /**
     * 取某行的货主名称。
     *
     * <p>必须自己判 {@code null}：内部员工不绑定货主，而 {@code Map.of()} 这类不可变映射
     * 连 {@code get(null)} 都会抛 {@link NullPointerException}，不判会让一整页员工账号直接 500。
     *
     * @param ownerId    货主标识，内部员工为 {@code null}
     * @param ownerNames 货主标识到名称的映射
     * @return 货主名称；{@code ownerId} 为 {@code null} 或映射里没有该标识时为 {@code null}
     */
    private static String ownerNameOf(Long ownerId, Map<Long, String> ownerNames) {
        return ownerId == null ? null : ownerNames.get(ownerId);
    }

    private static UserRoleGrant toGrant(UserRoleGrantRow row) {
        return new UserRoleGrant(row.getUserId(), row.getRoleId(), row.getRoleCode());
    }

    private static String codeOf(SubjectType subjectType) {
        return subjectType == null ? null : subjectType.name();
    }

    private static String codeOf(UserStatus status) {
        return status == null ? null : status.name();
    }
}
