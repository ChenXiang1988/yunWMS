package com.yunwms.master.domain.model;

/**
 * 货主档案里可编辑的那部分：编辑用例与仓储端口之间的写入模型。
 *
 * <p><b>为什么它不含 {@code code}、{@code skuPrefix} 与 {@code status}：</b>这三项不是「档案属性」，
 * 改动它们属于另外的动作——编码是标识，改了就不是同一个货主；SKU 前缀是已生成 SKUID 的前四位，
 * 改它会让已贴出去的标签指向另一个货主的货；状态属状态机（3.2），只能按流转规则走。
 * 把它们排除在类型之外，编辑入口就不可能改到它们，而不是靠一句「这里不要写那三列」的注释提醒——
 * 注释挡不住下一次顺手加字段。
 *
 * <p>与 {@link NewOwner} 的分工：那个是「即将落库的货主」，多一个 SKU 前缀、少一个标识；
 * 这个是「已存在的货主的档案覆盖值」，不含前缀。两者共用同一条属性不变量
 * （{@link Owner#requireAttributes}）与同一个地址值对象，规则只有一份实现。
 *
 * <p>本类同样不判「客户类型是否在字典里」「国家是否在字典里」「州省是否属于该国」「该地区是否要求邮编」：
 * 那些要查字典，属 {@code OwnerDictionaryValidator}，与建档走的是同一个领域服务。
 *
 * @param name             客户名称，可重复
 * @param shortName        货主简称，可为 {@code null}（展示层回退到名称前 8 字）
 * @param nameEn           英文名称，可为 {@code null}
 * @param externalCode     外部系统编码，可为 {@code null}；未删除时唯一
 * @param ownerType        客户类型，取 {@code OWNER_TYPE} 字典的 value
 * @param settlementEntity 结算主体，开票与对账的抬头
 * @param address          注册地址段
 * @param remark           备注，可为 {@code null}
 */
public record OwnerProfile(
        String name,
        String shortName,
        String nameEn,
        String externalCode,
        String ownerType,
        String settlementEntity,
        OwnerAddress address,
        String remark) {

    /**
     * 校验档案属性的不变量。
     *
     * <p>与 {@link NewOwner} 一样是最后一道防线：用例层会先判一次并给出可展示的业务错误，
     * 这里拦的是绕过用例直接构造（批量导入、数据修复脚本）。地址的格式与「美国必填州省」
     * 由 {@link OwnerAddress} 判，本类不重复。
     *
     * @throws IllegalArgumentException 名称为空、客户类型为空、结算主体为空，或注册地址缺失
     */
    public OwnerProfile {
        Owner.requireAttributes(name, ownerType, settlementEntity);
        if (address == null) {
            throw new IllegalArgumentException("注册地址不能为空");
        }
    }
}
