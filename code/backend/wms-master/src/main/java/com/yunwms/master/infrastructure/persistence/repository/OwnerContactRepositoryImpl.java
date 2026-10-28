package com.yunwms.master.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.master.domain.model.OwnerContact;
import com.yunwms.master.domain.model.OwnerContactDraft;
import com.yunwms.master.domain.port.OwnerContactRepository;
import com.yunwms.master.infrastructure.persistence.entity.OwnerContactEntity;
import com.yunwms.master.infrastructure.persistence.mapper.OwnerContactMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link OwnerContactRepository} 的 MyBatis-Plus 实现。
 *
 * <p><b>读侧走 {@code BaseMapper} 与 Lambda Wrapper：</b>列表、按标识读、计数、按角色判存在都是
 * 单表等值查询，{@code docs/coding-java.md} 要求这类查询优先用 Wrapper；逻辑删除条件由
 * {@code application.yml} 的全局配置自动补上，这里不再手写一遍 {@code deleted_at IS NULL}
 * ——「已删除怎么表示」只能有一处定义，手写的那份会在软删除改列时被漏掉
 * （与 {@code OwnerAccountQueryImpl} 同一处理）。写侧相反，见下一条。
 *
 * <p><b>写侧走 XML：</b>{@link #update} 必须能把 {@code email} 与 {@code remark} 写成 {@code NULL}，
 * 而 {@code BaseMapper#updateById} 按「非空字段才进 SET」生成语句，于是「清空邮箱」会变成一次
 * 静默的空操作；{@link #softDelete} 与 {@link #clearPrimary} 还要同时写 {@code updated_at = now()}
 * 与 {@code updated_by}，也不是单条 {@code deleteById} / {@code updateById} 能表达的。三条语句都在
 * {@code mapper/master/OwnerContactMapper.xml} 里显式列出 SET 与 {@code deleted_at IS NULL}：
 * 手写 SQL 拿不到逻辑删除配置的补偿，两边各自把该条件写清楚才不会漏。
 *
 * <p><b>插入只写应用侧知道的列：</b>{@code created_at} / {@code updated_at} 交给库的
 * {@code DEFAULT now()}，应用侧只写 {@code created_by} / {@code updated_by}。应用侧再取一次时钟会
 * 引入两个可能相差几毫秒的时间来源，同一行的创建与修改时间就不再同源
 * （与 {@code OwnerRepositoryImpl#insert} 同一理由）。
 *
 * <p><b>重建不绕过创建期校验：</b>{@link #toDomain} 直接调用 {@link OwnerContact} 的紧凑构造器。
 * 因此库里若存在 {@code phone IS NULL} 的历史行，读取会当场抛
 * {@link IllegalArgumentException}，而不是返回一条没有电话的联系人——端口已在
 * {@code findByOwnerId} / {@code findById} 的注释里声明这是刻意的失败，这里不降级成「跳过该行」：
 * 悄悄少返回一条联系人，比报错更难被发现。
 */
@Repository
public class OwnerContactRepositoryImpl implements OwnerContactRepository {

    private final OwnerContactMapper ownerContactMapper;

    public OwnerContactRepositoryImpl(OwnerContactMapper ownerContactMapper) {
        this.ownerContactMapper = ownerContactMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>排序写进语句（{@code ORDER BY id ASC}）而不是在内存里排：{@code id} 升序等价于登记先后，
     * 是联系人页签唯一有意义的顺序；把排序放到数据库，调用方拿到的就是从第一行起就正确的列表。
     */
    @Override
    public List<OwnerContact> findByOwnerId(long ownerId) {
        return ownerContactMapper.selectList(Wrappers.<OwnerContactEntity>lambdaQuery()
                        .eq(OwnerContactEntity::getOwnerId, ownerId)
                        .orderByAsc(OwnerContactEntity::getId))
                .stream()
                .map(OwnerContactRepositoryImpl::toDomain)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code selectById} 自带逻辑删除条件：被软删除的联系人必须和「不存在」同口径。
     * 若这里能读到已删除的行，调用方就会把它当作有效记录去编辑，而所有写语句都带
     * {@code deleted_at IS NULL}——那次编辑既不生效也不报错。
     */
    @Override
    public Optional<OwnerContact> findById(long id) {
        return Optional.ofNullable(ownerContactMapper.selectById(id))
                .map(OwnerContactRepositoryImpl::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * <p>主键回填依赖 JDBC 的 generated keys；拿不到主键时当场失败，不返回 0 或 -1 充当标识：
     * 那会让调用方拿着一个不存在的 id 继续往下走，错误要到更远的链路才暴露
     * （与 {@code OwnerRepositoryImpl#insert} 同一处理）。
     *
     * <p>不在这里清旧的主联系人：端口已约定「先清旧主、再写新主」由用例按固定顺序调
     * {@link #clearPrimary}，两条规则只有一个落点。撞上 {@code ux_owner_contact_primary} 时
     * 由数据库原样上抛，由调用方翻译成业务错误码。
     */
    @Override
    public long insert(long ownerId, OwnerContactDraft draft, long operatorId) {
        OwnerContactEntity entity = toInsertEntity(ownerId, draft, operatorId);
        ownerContactMapper.insert(entity);
        Long id = entity.getId();
        if (id == null) {
            throw new IllegalStateException("联系人写入后未取回主键，货主 id=" + ownerId);
        }
        return id;
    }

    /**
     * {@inheritDoc}
     *
     * <p>影响行数不参与判定：调用方已经读过这一行，命中零行只可能是它在两次调用之间被软删除。
     * 那不是需要纠正的失败，也不是并发冲突（{@code docs/coding.md} 明确规定不得依据影响行数
     * 判断成功或冲突，这里的写法就是那条规则的落点）。
     *
     * <p>不在这里判「该货主是否已有别的主联系人」：那是 {@code ux_owner_contact_primary} 的职责，
     * 本方法连另一个主联系人的标识都没接收，也就没有「顺手清掉它」的能力。
     */
    @Override
    public void update(long id, OwnerContactDraft draft, long operatorId) {
        ownerContactMapper.updateContact(toUpdateEntity(id, draft, operatorId));
    }

    /**
     * {@inheritDoc}
     *
     * <p>这里必须看影响行数：删除是「这条记录从此不该再存在」的断言，而调用方刚读过它。
     * 命中零行只剩一种解释——它在两次调用之间被另一个请求删掉了，调用方要据此回 404。
     */
    @Override
    public boolean softDelete(long id, long operatorId) {
        return ownerContactMapper.softDelete(id, operatorId) > 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>不看影响行数：没有主联系人时命中零行正是本方法的正常输入（第一个主联系人也得先清一次
     * 才写），既不是失败也不是需要交代的情况——与 {@link #softDelete} 的判据正好相反，那边零行
     * 意味着「记录已经不在了」。
     */
    @Override
    public void clearPrimary(long ownerId, long operatorId) {
        ownerContactMapper.clearPrimary(ownerId, operatorId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>用计数语句而不是「取全量再 {@code size()}」：详情页签只要一个数字，为一个数字把整行
     * （含备注）读出来是白读。
     *
     * <p>筛选条件只有 {@code owner_id}，与 {@link #findByOwnerId} 完全一致：计数与列表必须来自
     * 同一份条件，否则页签上的条数会与它下面的列表对不上；未删除这一条由全局逻辑删除配置补，
     * 不在这里另写一遍。
     *
     * @throws IllegalStateException 计数没有返回结果，说明语句或结果映射被改坏；不返回 0 兜底
     */
    @Override
    public long countByOwnerId(long ownerId) {
        Long count = ownerContactMapper.selectCount(Wrappers.<OwnerContactEntity>lambdaQuery()
                .eq(OwnerContactEntity::getOwnerId, ownerId));
        if (count == null) {
            throw new IllegalStateException("联系人计数未返回结果，货主 id=" + ownerId);
        }
        return count;
    }

    /**
     * {@inheritDoc}
     *
     * <p>空白角色直接短路为 {@code false} 而不发查询：空角色不可能是 {@code CONTACT_ROLE} 的合法取值，
     * 也就不可能有命中行；交给 SQL 去比一个空白串只会让调用方以为「查过一次、确实没有」。
     */
    @Override
    public boolean existsByRole(long ownerId, String roleType) {
        if (roleType == null || roleType.isBlank()) {
            return false;
        }
        Long count = ownerContactMapper.selectCount(Wrappers.<OwnerContactEntity>lambdaQuery()
                .eq(OwnerContactEntity::getOwnerId, ownerId)
                .eq(OwnerContactEntity::getRoleType, roleType));
        if (count == null) {
            throw new IllegalStateException("联系人角色判定未返回结果，货主 id=" + ownerId);
        }
        return count > 0;
    }

    /**
     * 把创建入参搬成待插入的行。
     *
     * <p>与 {@link #toUpdateEntity} 分成两个名字而不是重载：两者的入参类型完全相同
     * （{@code long} 标识 + {@code OwnerContactDraft} + {@code long} 操作人），Java 无法重载，
     * 而方法名相同还会让「把联系人标识传给了 {@code ownerId}」这种错误照常编译。
     *
     * @param ownerId    所属货主标识
     * @param draft      已归一并校验过的字段值
     * @param operatorId 操作人标识，同时写入 {@code created_by} 与 {@code updated_by}
     * @return 待插入的行；{@code created_at} / {@code updated_at} / {@code deleted_at} 留空，
     *         由列默认值与「未删除」的初始状态负责
     */
    private static OwnerContactEntity toInsertEntity(long ownerId, OwnerContactDraft draft, long operatorId) {
        OwnerContactEntity entity = new OwnerContactEntity();
        entity.setOwnerId(ownerId);
        fillEditableFields(entity, draft);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        return entity;
    }

    /**
     * 把编辑入参搬成待更新的行。
     *
     * <p>只填标识、可编辑列与操作人：{@code owner_id}、{@code created_at} / {@code created_by}
     * 留空，而 {@code updateContact} 的 SET 列表里也没有它们——两层都不写，才能保证编辑改不到归属
     * 与创建痕迹。
     *
     * @param id         联系人标识
     * @param draft      覆盖后的字段值
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @return 待更新的行
     */
    private static OwnerContactEntity toUpdateEntity(long id, OwnerContactDraft draft, long operatorId) {
        OwnerContactEntity entity = new OwnerContactEntity();
        entity.setId(id);
        fillEditableFields(entity, draft);
        entity.setUpdatedBy(operatorId);
        return entity;
    }

    /**
     * 搬六个可编辑列，插入与更新共用。
     *
     * <p>两处逐字段转写的清单必须一致：漏一列的后果是「新增时电话存进去了，编辑一次就没了」，
     * 而这类缺陷不会被编译拦下。抽出来是因为两个真实调用点写的是同一份清单，
     * 而不是为了缩短方法。
     *
     * @param entity 目标行
     * @param draft  已归一并校验过的字段值；{@code email} / {@code remark} 为 {@code null} 时
     *               表示清空，XML 会把它写成 {@code NULL}
     */
    private static void fillEditableFields(OwnerContactEntity entity, OwnerContactDraft draft) {
        entity.setName(draft.name());
        entity.setRoleType(draft.roleType());
        entity.setPhone(draft.phone());
        entity.setEmail(draft.email());
        entity.setIsPrimary(draft.isPrimary());
        entity.setRemark(draft.remark());
    }

    /**
     * 把一行搬成领域模型。
     *
     * <p>{@code is_primary} 用 {@code Boolean.TRUE.equals} 取值而不是直接拆箱：列在库里是
     * {@code NOT NULL DEFAULT false}，取不到值说明投影漏列或数据库被绕过约束写坏。这一位不像
     * 姓名、角色、电话那样有业务后果（多一条「非主」联系人不会让谁看不到数据），映射成
     * 「非主」与列默认值一致；真正会伤到数据的必填项由 {@link OwnerContact} 的构造器当场拒绝。
     *
     * @param entity 数据库返回的行
     * @return 联系人
     * @throws IllegalArgumentException 姓名、角色或电话为空，或任一字段超出列宽，说明数据已损坏
     */
    private static OwnerContact toDomain(OwnerContactEntity entity) {
        return new OwnerContact(
                entity.getId(),
                entity.getOwnerId(),
                entity.getName(),
                entity.getRoleType(),
                entity.getPhone(),
                entity.getEmail(),
                Boolean.TRUE.equals(entity.getIsPrimary()),
                entity.getRemark());
    }
}
