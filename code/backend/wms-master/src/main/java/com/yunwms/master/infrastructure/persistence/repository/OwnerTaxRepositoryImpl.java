package com.yunwms.master.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.master.domain.model.OwnerTaxDraft;
import com.yunwms.master.domain.model.OwnerTaxRegistration;
import com.yunwms.master.domain.port.OwnerTaxRepository;
import com.yunwms.master.infrastructure.persistence.entity.OwnerTaxEntity;
import com.yunwms.master.infrastructure.persistence.mapper.OwnerTaxMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link OwnerTaxRepository} 的 MyBatis-Plus 实现。
 *
 * <p><b>读侧走 {@code BaseMapper} 与 Lambda Wrapper：</b>按货主取列表与按标识读都是单表等值查询，
 * {@code docs/coding-java.md} 要求这类查询优先用 Wrapper；逻辑删除条件由 {@code application.yml} 的
 * 全局配置自动补上，这里不再手写一遍 {@code deleted_at IS NULL}——「已删除怎么表示」只能有一处
 * 定义，手写的那份会在软删除改列时被漏掉（与 {@code OwnerContactRepositoryImpl} 同一处理）。
 * 写侧相反，见下一条。
 *
 * <p><b>写侧走 XML：</b>{@link #update} 必须能把 {@code province} 写成 {@code NULL}（国家级税号
 * 换登记层级时），而 {@code BaseMapper#updateById} 按「非空字段才进 SET」生成语句，于是「清空州省」
 * 会变成一次静默的空操作；{@link #softDelete} 与 {@link #clearDefault} 还要同时写
 * {@code updated_at = now()} 与 {@code updated_by}，也不是单条 {@code deleteById} / {@code updateById}
 * 能表达的。三条语句都在 {@code mapper/master/OwnerTaxMapper.xml} 里显式列出 SET 与
 * {@code deleted_at IS NULL}：手写 SQL 拿不到逻辑删除配置的补偿，两边各自把该条件写清楚才不会漏。
 *
 * <p><b>插入只写应用侧知道的列：</b>{@code created_at} / {@code updated_at} 交给库的
 * {@code DEFAULT now()}，应用侧只写 {@code created_by} / {@code updated_by}。应用侧再取一次时钟会
 * 引入两个可能相差几毫秒的时间来源，同一行的创建与修改时间就不再同源
 * （与 {@code OwnerRepositoryImpl#insert} 同一理由）。
 *
 * <p><b>重建不绕过创建期校验：</b>{@link #toDomain} 直接调用 {@link OwnerTaxRegistration} 的紧凑
 * 构造器。因此库里若存在国家码不合法的历史行，读取会当场抛 {@link IllegalArgumentException}，
 * 而不是返回一条国家码不成立的税号——端口已在 {@code findByOwnerId} / {@code findById} 的注释里
 * 声明这是刻意的失败，这里不降级成「跳过该行」：悄悄少返回一条税号，比报错更难被发现。
 */
@Repository
public class OwnerTaxRepositoryImpl implements OwnerTaxRepository {

    private final OwnerTaxMapper ownerTaxMapper;

    public OwnerTaxRepositoryImpl(OwnerTaxMapper ownerTaxMapper) {
        this.ownerTaxMapper = ownerTaxMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>排序写进语句而不是在内存里排：三列的顺序由数据库给出，调用方拿到的就是从第一行起就正确的
     * 列表。三次 {@code orderByAsc} 依次拼接成 {@code country, province, tax_type}，
     * 不补 {@code id} 兜底——{@code ux_owner_tax_unique} 已保证同一货主下这三列的组合唯一，
     * 排序键本身就是全序。
     */
    @Override
    public List<OwnerTaxRegistration> findByOwnerId(long ownerId) {
        return ownerTaxMapper.selectList(Wrappers.<OwnerTaxEntity>lambdaQuery()
                        .eq(OwnerTaxEntity::getOwnerId, ownerId)
                        .orderByAsc(OwnerTaxEntity::getCountry)
                        .orderByAsc(OwnerTaxEntity::getProvince)
                        .orderByAsc(OwnerTaxEntity::getTaxType))
                .stream()
                .map(OwnerTaxRepositoryImpl::toDomain)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code selectById} 自带逻辑删除条件：被软删除的税号必须和「不存在」同口径。
     * 若这里能读到已删除的行，调用方就会把它当作有效记录去编辑，而所有写语句都带
     * {@code deleted_at IS NULL}——那次编辑既不生效也不报错。
     */
    @Override
    public Optional<OwnerTaxRegistration> findById(long id) {
        return Optional.ofNullable(ownerTaxMapper.selectById(id))
                .map(OwnerTaxRepositoryImpl::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * <p>主键回填依赖 JDBC 的 generated keys；拿不到主键时当场失败，不返回 0 或 -1 充当标识：
     * 那会让调用方拿着一个不存在的 id 继续往下走，错误要到更远的链路才暴露
     * （与 {@code OwnerRepositoryImpl#insert} 同一处理）。
     *
     * <p>不在这里清旧的默认税号：端口已约定「先清旧默认、再写新默认」由用例按固定顺序调
     * {@link #clearDefault}，两条规则只有一个落点。撞上 {@code ux_owner_tax_default} 或
     * {@code ux_owner_tax_unique} 时由数据库原样上抛，由调用方翻译成业务错误码。
     */
    @Override
    public long insert(long ownerId, OwnerTaxDraft draft, long operatorId) {
        OwnerTaxEntity entity = toInsertEntity(ownerId, draft, operatorId);
        ownerTaxMapper.insert(entity);
        Long id = entity.getId();
        if (id == null) {
            throw new IllegalStateException("税号写入后未取回主键，货主 id=" + ownerId);
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
     * <p>不在这里判「该货主在该国是否已有别的默认税号」：那是 {@code ux_owner_tax_default} 的职责，
     * 本方法连另一个默认税号的标识都没接收，也就没有「顺手清掉它」的能力。
     */
    @Override
    public void update(long id, OwnerTaxDraft draft, long operatorId) {
        ownerTaxMapper.updateTax(toUpdateEntity(id, draft, operatorId));
    }

    /**
     * {@inheritDoc}
     *
     * <p>这里必须看影响行数：删除是「这条记录从此不该再存在」的断言，而调用方刚读过它。
     * 命中零行只剩一种解释——它在两次调用之间被另一个请求删掉了，调用方要据此回 404。
     */
    @Override
    public boolean softDelete(long id, long operatorId) {
        return ownerTaxMapper.softDelete(id, operatorId) > 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>不看影响行数：没有默认税号时命中零行正是本方法的正常输入（第一个默认税号也得先清一次
     * 才写），既不是失败也不是需要交代的情况——与 {@link #softDelete} 的判据正好相反，
     * 那边零行意味着「记录已经不在了」。
     */
    @Override
    public void clearDefault(long ownerId, String country, long operatorId) {
        ownerTaxMapper.clearDefault(ownerId, country, operatorId);
    }

    /**
     * 把创建入参搬成待插入的行。
     *
     * <p>与 {@link #toUpdateEntity} 分成两个名字而不是重载：两者的入参类型完全相同
     * （{@code long} 标识 + {@link OwnerTaxDraft} + {@code long} 操作人），Java 无法重载，
     * 而方法名相同还会让「把税号标识传给了 {@code ownerId}」这种错误照常编译。
     *
     * <p>归属、国家、税号类型只在这里写：插入是它们唯一一次落库，之后的更新语句不碰这三列，
     * 因此「哪一国的哪种税号」在行的生命周期里不会漂移。
     *
     * @param ownerId    所属货主标识
     * @param draft      已归一并校验过的字段值
     * @param operatorId 操作人标识，同时写入 {@code created_by} 与 {@code updated_by}
     * @return 待插入的行；{@code created_at} / {@code updated_at} / {@code deleted_at} 留空，
     *         由列默认值与「未删除」的初始状态负责
     */
    private static OwnerTaxEntity toInsertEntity(long ownerId, OwnerTaxDraft draft, long operatorId) {
        OwnerTaxEntity entity = new OwnerTaxEntity();
        entity.setOwnerId(ownerId);
        entity.setCountry(draft.country());
        entity.setTaxType(draft.taxType());
        fillEditableFields(entity, draft);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        return entity;
    }

    /**
     * 把编辑入参搬成待更新的行。
     *
     * <p>只填标识、可编辑列与操作人：{@code owner_id}、{@code country}、{@code tax_type} 以及
     * {@code created_at} / {@code created_by} 留空，而 {@code updateTax} 的 SET 列表里也没有它们
     * ——两层都不写，才能保证编辑改不到身份与创建痕迹。draft 里的 {@code country} 与
     * {@code taxType} 因此只起了校验作用，不落库。
     *
     * @param id         税号标识
     * @param draft      覆盖后的字段值
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @return 待更新的行
     */
    private static OwnerTaxEntity toUpdateEntity(long id, OwnerTaxDraft draft, long operatorId) {
        OwnerTaxEntity entity = new OwnerTaxEntity();
        entity.setId(id);
        fillEditableFields(entity, draft);
        entity.setUpdatedBy(operatorId);
        return entity;
    }

    /**
     * 搬三个可编辑列，插入与更新共用。
     *
     * <p>两处逐字段转写的清单必须一致：漏一列的后果是「新增时州省存进去了，编辑一次就没了」，
     * 而这类缺陷不会被编译拦下。抽出来是因为两个真实调用点写的是同一份清单，
     * 而不是为了缩短方法。
     *
     * @param entity 目标行
     * @param draft  已归一并校验过的字段值；{@code province} 为 {@code null} 时表示清空，
     *               XML 会把它写成 {@code NULL}
     */
    private static void fillEditableFields(OwnerTaxEntity entity, OwnerTaxDraft draft) {
        entity.setProvince(draft.province());
        entity.setTaxNumber(draft.taxNumber());
        entity.setIsDefault(draft.isDefault());
    }

    /**
     * 把一行搬成领域模型。
     *
     * <p>{@code is_default} 取不到值时当场失败，不映射成 {@code false}：列在库里是
     * {@code NOT NULL DEFAULT false}，取到 {@code null} 说明投影漏列或该行被绕过约束写坏。
     * 降级成「非默认」会让这条税号从「该国默认」变成「不是默认」，页签上看不出来，
     * 下游按默认取号时却会取错或取不到——那类错误不会在写入处暴露，只会在一张单据的税号栏里出现。
     * 端口已在 {@code findByOwnerId} / {@code findById} 声明读取可能因数据损坏而失败，
     * 这里不降级成跳过该行。
     *
     * @param entity 数据库返回的行
     * @return 税号
     * @throws IllegalArgumentException 国家码或必填字段不合法、或超出列宽，说明数据已损坏
     * @throws IllegalStateException    {@code is_default} 未取到值，说明投影漏列或该行已损坏
     */
    private static OwnerTaxRegistration toDomain(OwnerTaxEntity entity) {
        if (entity.getIsDefault() == null) {
            throw new IllegalStateException("税号的 is_default 未取到值，税号 id=" + entity.getId());
        }
        return new OwnerTaxRegistration(
                entity.getId(),
                entity.getOwnerId(),
                entity.getCountry(),
                entity.getProvince(),
                entity.getTaxType(),
                entity.getTaxNumber(),
                entity.getIsDefault());
    }
}
