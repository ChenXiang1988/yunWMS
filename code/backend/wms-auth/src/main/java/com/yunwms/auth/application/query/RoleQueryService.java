package com.yunwms.auth.application.query;

import com.yunwms.auth.application.dto.RoleDetail;
import com.yunwms.auth.application.dto.RolePage;
import com.yunwms.auth.domain.model.DataScopeSetting;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.domain.model.RoleSummary;
import com.yunwms.auth.domain.port.DataScopeQuery;
import com.yunwms.auth.domain.port.RoleQuery;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色列表与详情的读取用例。
 *
 * <p>与 {@link com.yunwms.auth.application.service.RoleCreationService} 分开：读侧不需要事务语义，
 * 也不写审计字段；混在一个类里会让「这个类的每个方法是不是都在改数据」变成一个要看方法体
 * 才能回答的问题。
 *
 * <p>本类只做三件事：把分页参数判在合法范围内、把关键字归一（全空白按不筛选）、
 * 把「角色不存在」转成 404。查询本身全部下沉到 {@link RoleQuery}：聚合计数写在 SQL 里
 * 才能保证列表页的 SQL 条数不随角色数量增长。
 */
@Service
public class RoleQueryService {

    /** 每页条数上限。与 {@code docs/api-convention.md} 第四节一致：超出必须拒绝，不得静默截断。 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 每页条数默认值，与接口约定里的示例一致。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int DEFAULT_PAGE_NO = 1;

    private final RoleQuery roleQuery;
    private final DataScopeQuery dataScopeQuery;

    public RoleQueryService(RoleQuery roleQuery, DataScopeQuery dataScopeQuery) {
        this.roleQuery = roleQuery;
        this.dataScopeQuery = dataScopeQuery;
    }

    /**
     * 取一页角色。
     *
     * <p>分页参数的范围判定写在这里而不是接口层的 {@code @Min} / {@code @Max}：方法参数校验
     * 失败抛的是 {@code ConstraintViolationException}，而 {@code GlobalExceptionHandler} 里没有
     * 它的分支，会被兜底成 50000。本批不改 {@code wms-web}（不在路径白名单内），
     * 因此在这里抛 {@code BizException}，让错误码稳定落在 40000。
     *
     * @param pageNo   页码；为 {@code null} 时取 1
     * @param pageSize 每页条数；为 {@code null} 时取 20
     * @param keyword  按编码或名称模糊匹配；为 {@code null} 或全空白表示不筛选
     * @param status   角色状态；{@code null} 表示不筛选
     * @return 一页角色，含总数
     * @throws BizException 页码小于 1、每页条数不在 1 到 200 之间（40000）
     */
    @Transactional(readOnly = true)
    public RolePage page(Integer pageNo, Integer pageSize, String keyword, RoleStatus status) {
        int resolvedPageNo = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageNo < 1) {
            throw BizException.badRequest("页码必须从 1 开始");
        }
        if (resolvedPageSize < 1 || resolvedPageSize > MAX_PAGE_SIZE) {
            // 拒绝而不是截断：静默截成 200 时前端会把「只看到 200 条」当成「一共 200 条」
            throw BizException.badRequest("每页条数必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }

        String normalizedKeyword = stripToNull(keyword);
        long total = roleQuery.count(normalizedKeyword, status);
        long offset = (long) (resolvedPageNo - 1) * resolvedPageSize;
        List<RoleSummary> records = roleQuery.findPage(normalizedKeyword, status, resolvedPageSize, offset);
        return new RolePage(resolvedPageNo, resolvedPageSize, total, records);
    }

    /**
     * 取单个角色的详情。
     *
     * <p>三次查询（角色摘要、勾选的菜单、数据范围）而不是一次联表：每次都是单表条件查询，
     * 联表要处理「一个角色多个菜单」「一个角色多个货主」的行展开，读起来比分开读取难，
     * 省下的往返在个位数角色上无意义。
     *
     * <p>数据范围的类型与明细各自从 {@link DataScopeQuery} 取，而不是给 {@code RoleQuery} 的
     * 摘要投影加一列：列表页不需要这两份数据，加进摘要会让每次列表查询都多读与列表无关的列。
     *
     * @param roleId 角色标识
     * @return 角色摘要、勾选的菜单及权限点标识与数据范围配置
     * @throws BizException 角色不存在或已逻辑删除（40400）
     */
    @Transactional(readOnly = true)
    public RoleDetail detail(long roleId) {
        RoleSummary summary = roleQuery.findById(roleId)
                .orElseThrow(() -> BizException.notFound("角色不存在"));
        // 摘要刚读到、范围却读不到，只可能是这一行在两次查询之间被删除：按 404 处理，
        // 与「角色不存在」同一口径，而不是抛 500
        DataScopeSetting dataScope = dataScopeQuery.findRoleSetting(roleId)
                .orElseThrow(() -> BizException.notFound("角色不存在"));
        // 明细按标识升序回显：集合本身无序，直接透传会让同一个角色两次请求的响应顺序不同
        return new RoleDetail(summary, roleQuery.findMenuIds(roleId), dataScope.type(),
                dataScope.ownerIds().stream().sorted().toList());
    }

    /**
     * 去掉两侧空白，空串按未填处理。
     *
     * <p>不裁空白时，一个只有空格的关键字会变成 {@code LIKE '% %'}，把编码与名称里恰好带空格的
     * 记录筛出来，而管理员的本意是「不筛选」。
     *
     * @param value 原始值，可为 {@code null}
     * @return 裁剪后的值；{@code null}、空串或全空白时为 {@code null}
     */
    private static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
