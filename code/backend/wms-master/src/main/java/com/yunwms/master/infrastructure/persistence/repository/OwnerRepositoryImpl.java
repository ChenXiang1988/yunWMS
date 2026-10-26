package com.yunwms.master.infrastructure.persistence.repository;

import com.yunwms.master.domain.model.NewOwner;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.domain.model.OwnerProfile;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.model.SkuPrefix;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.master.infrastructure.persistence.entity.OwnerEntity;
import com.yunwms.master.infrastructure.persistence.mapper.OwnerMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link OwnerRepository} 的 MyBatis-Plus 实现。
 *
 * <p><b>写入：</b>单行插入用 {@code BaseMapper#insert}，不需要 XML。{@code created_at} 与
 * {@code updated_at} 不在应用侧取值——两列在库内都有 {@code DEFAULT now()}，交给数据库可以保证
 * 同一行的创建与修改时间同源；应用侧再取一次时钟只会引入两个可能相差几毫秒的来源。
 * {@code status} 则必须由应用侧写入：它是建档入口的决定（草稿 / 直接提交），交给列默认值
 * 会让第二条入口落到错误的状态上，理由见端口上的说明。
 *
 * <p><b>更新：</b>走 XML 的 {@code updateProfile} 而不是 {@code updateById}——后者会略过空字段，
 * 使「清空外部码」变成一次静默的空操作。SET 列表里没有编码、SKU 前缀与状态，
 * 编辑因此改不到它们。
 *
 * <p><b>重建：</b>{@link #toDomain} 把行搬成聚合，聚合的构造器会拒绝残缺数据
 * （状态、前缀、地址缺失，或名称、结算主体为空）。因此读投影漏列不会变成「读出来一个字段全空
 * 但看起来正常」的货主，而是当场失败——这段映射是「库里读到的事实」唯一的入口，
 * 它不绕过任何创建期校验。
 *
 * <p>状态、前缀与地址的中文说明不做在这里：{@link OwnerStatus#fromCode} 遇到未知编码直接抛异常。
 * 这是刻意的——库里出现 {@code 'FOO'} 说明数据被绕过 CHECK 写坏，静默回落会让一个已终止的货主
 * 以草稿身份回到列表里。
 */
@Repository
public class OwnerRepositoryImpl implements OwnerRepository {

    private final OwnerMapper ownerMapper;

    public OwnerRepositoryImpl(OwnerMapper ownerMapper) {
        this.ownerMapper = ownerMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>主键回填依赖 JDBC 的 generated keys；拿不到主键时当场失败，不返回 0 或 -1 充当标识：
     * 那会让调用方拿着一个不存在的 id 继续往下走，错误要到更远的链路才暴露。
     *
     * <p>初始状态为 {@code null} 时先失败：MyBatis-Plus 默认只拼非空字段，空值会被整列略过，
     * 于是插入悄悄落到列默认值 {@code 'DRAFT'}——「保存草稿」看起来正常，「直接提交」则会
     * 以草稿落库而没有任何报错。这类静默回落比一次明确的失败昂贵得多。
     */
    @Override
    public long insert(NewOwner owner, String code, OwnerStatus initialStatus, long operatorId) {
        if (initialStatus == null) {
            throw new IllegalArgumentException("货主初始状态不能为空，编码为：" + code);
        }
        OwnerEntity entity = toEntity(owner, code, initialStatus, operatorId);
        ownerMapper.insert(entity);
        Long id = entity.getId();
        if (id == null) {
            throw new IllegalStateException("货主建档后未取回主键，编码为：" + code);
        }
        return id;
    }

    /**
     * {@inheritDoc}
     *
     * <p>影响行数不参与判定：调用方已经读过这一行，命中零行只可能是它在两次调用之间被软删除。
     * 那不是需要纠正的失败，也不是并发冲突——{@code docs/coding.md} 明确规定不得依据影响行数
     * 判断成功或冲突，这里的写法就是那条规则的落点。
     */
    @Override
    public void update(long id, OwnerProfile profile, long operatorId) {
        ownerMapper.updateProfile(toEntity(id, profile, operatorId));
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code selectById} 自带逻辑删除条件：被软删除的货主必须和「不存在」同口径。
     */
    @Override
    public Optional<Owner> findById(long id) {
        OwnerEntity entity = ownerMapper.selectById(id);
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public List<Owner> findPage(OwnerStatus status, String ownerType, String keyword, int limit, long offset) {
        return ownerMapper.selectPageByFilter(codeOf(status), ownerType, keyword, limit, offset).stream()
                .map(OwnerRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public long count(OwnerStatus status, String ownerType, String keyword) {
        return ownerMapper.countByFilter(codeOf(status), ownerType, keyword);
    }

    @Override
    public boolean existsBySkuPrefix(String skuPrefix) {
        return ownerMapper.existsBySkuPrefix(skuPrefix);
    }

    /**
     * {@inheritDoc}
     *
     * <p>空外部码直接短路为「未占用」而不发查询：外部码本就允许为空，而库里不存在
     * {@code external_code = NULL} 的匹配——把这件事交给 SQL 只会让调用方以为空值查过一次。
     */
    @Override
    public boolean existsByExternalCode(String externalCode) {
        if (externalCode == null) {
            return false;
        }
        return ownerMapper.existsByExternalCode(externalCode);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code null} 结果转成空 {@link Optional}：本方法只在插入已经失败之后被调用，
     * 返回 {@code null} 会让调用方在拼提示时踩空指针——而那时的空指针会盖掉真正要展示的冲突原因。
     */
    @Override
    public Optional<String> findCodeBySkuPrefix(String skuPrefix) {
        return Optional.ofNullable(ownerMapper.selectCodeBySkuPrefix(skuPrefix));
    }

    /**
     * 把创建入参搬成表映射对象。
     *
     * @param owner         待落库的货主
     * @param code          系统编码，由取号端口取得
     * @param initialStatus 初始状态，由建档用例决定
     * @param operatorId    操作人标识
     * @return 待插入的行
     */
    private static OwnerEntity toEntity(NewOwner owner, String code, OwnerStatus initialStatus, long operatorId) {
        OwnerAddress address = owner.address();
        OwnerEntity entity = new OwnerEntity();
        entity.setCode(code);
        entity.setName(owner.name());
        entity.setShortName(owner.shortName());
        entity.setNameEn(owner.nameEn());
        entity.setExternalCode(owner.externalCode());
        // 前缀以值对象的原文落库：值对象已保证格式，这里不再转换大小写或替换字符
        entity.setSkuPrefix(owner.skuPrefix().value());
        entity.setOwnerType(owner.ownerType());
        entity.setSettlementEntity(owner.settlementEntity());
        // 枚举按 name() 落库，与 ck_owner_status 的取值集合一致；不取 ordinal()，那会把枚举顺序变成数据格式
        entity.setStatus(initialStatus.name());
        entity.setCountry(address.country());
        entity.setProvince(address.province());
        entity.setCity(address.city());
        entity.setDistrict(address.district());
        entity.setPostalCode(address.postalCode());
        entity.setAddressLine(address.addressLine());
        entity.setRemark(owner.remark());
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        return entity;
    }

    /**
     * 把编辑入参搬成待更新的行。
     *
     * <p>只填标识、可编辑列与操作人：{@code code}、{@code sku_prefix}、{@code status} 留空，
     * 而 {@code updateProfile} 的 SET 列表里也没有它们——两层都不写，才能保证编辑改不到这三列。
     *
     * @param id         货主标识
     * @param profile    覆盖后的档案属性
     * @param operatorId 操作人标识
     * @return 待更新的行
     */
    private static OwnerEntity toEntity(long id, OwnerProfile profile, long operatorId) {
        OwnerAddress address = profile.address();
        OwnerEntity entity = new OwnerEntity();
        entity.setId(id);
        entity.setName(profile.name());
        entity.setShortName(profile.shortName());
        entity.setNameEn(profile.nameEn());
        entity.setExternalCode(profile.externalCode());
        entity.setOwnerType(profile.ownerType());
        entity.setSettlementEntity(profile.settlementEntity());
        entity.setCountry(address.country());
        entity.setProvince(address.province());
        entity.setCity(address.city());
        entity.setDistrict(address.district());
        entity.setPostalCode(address.postalCode());
        entity.setAddressLine(address.addressLine());
        entity.setRemark(profile.remark());
        entity.setUpdatedBy(operatorId);
        return entity;
    }

    /**
     * 把一行搬成聚合。
     *
     * @param entity 数据库返回的行
     * @return 货主聚合
     * @throws IllegalArgumentException 行里的状态编码未知、前缀格式不合规或必填项为空，说明数据已损坏
     */
    private static Owner toDomain(OwnerEntity entity) {
        return new Owner(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getShortName(),
                entity.getNameEn(),
                entity.getExternalCode(),
                new SkuPrefix(entity.getSkuPrefix()),
                entity.getOwnerType(),
                entity.getSettlementEntity(),
                OwnerStatus.fromCode(entity.getStatus()),
                new OwnerAddress(
                        entity.getCountry(),
                        entity.getProvince(),
                        entity.getCity(),
                        entity.getDistrict(),
                        entity.getPostalCode(),
                        entity.getAddressLine()),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }

    private static String codeOf(OwnerStatus status) {
        return status == null ? null : status.name();
    }
}
