package com.yunwms.master.domain.model;

/**
 * 待落库的新货主：建档用例与仓储端口之间的写入模型。
 *
 * <p><b>为什么三个字段缺席：</b>
 * <ul>
 *   <li>{@code id} 由库生成，{@link Owner} 的标识是「已落库」的既成事实；</li>
 *   <li>{@code code} 由取号引擎按编码规则生成（{@code OWNER} 规则，形如 {@code OWNER0001}），
 *       它是系统的输出而不是建档人的输入，因此由用例取号后交给仓储，不出现在本对象里；</li>
 *   <li>{@code status} 不是建档时的可选项：草稿与提交后「合作中」的区别属于建档流程（3.1b）
 *       与状态机（3.2），在它落地前由列默认值决定，本对象不预设。</li>
 * </ul>
 * 这与 {@code NewRole} 不承载 {@code builtin}、{@code status} 是同一个理由：创建入参里不该出现
 * 调用方无权决定的字段，否则接口层只要多绑一个字段就能把它改掉。
 *
 * <p>名称、客户类型与结算主体的非空判定与 {@link Owner} 共用同一份实现（
 * {@link Owner#requireAttributes}），SKU 前缀与地址的规则各自在 {@link SkuPrefix}、
 * {@link OwnerAddress} 里。本类因此不出现第三处定义。
 *
 * <p>本类不判「客户类型是否在字典里」「国家是否在字典里」「州省是否属于该国」「该地区是否要求邮编」：
 * 这些需要查字典，属 {@code OwnerDictionaryValidator}。
 *
 * @param name             客户名称，可重复
 * @param shortName        货主简称，可为 {@code null}（展示层回退到名称前 8 字）
 * @param nameEn           英文名称，可为 {@code null}
 * @param externalCode     外部系统编码，可为 {@code null}；未删除时唯一
 * @param skuPrefix        SKU 前缀，必填，全局唯一
 * @param ownerType        客户类型，取 {@code OWNER_TYPE} 字典的 value
 * @param settlementEntity 结算主体，开票与对账的抬头
 * @param address          注册地址段
 * @param remark           备注，可为 {@code null}
 */
public record NewOwner(
        String name,
        String shortName,
        String nameEn,
        String externalCode,
        SkuPrefix skuPrefix,
        String ownerType,
        String settlementEntity,
        OwnerAddress address,
        String remark) {

    /**
     * 校验创建期不变量。
     *
     * <p>这是最后一道防线：用例层会先判一次并给出可展示的业务错误，这里是防止绕过用例直接构造
     * （例如将来的批量导入、初始化脚本）把脏数据写下去。
     *
     * @throws IllegalArgumentException 名称为空、客户类型为空、结算主体为空、SKU 前缀缺失或地址缺失
     */
    public NewOwner {
        Owner.requireAttributes(name, ownerType, settlementEntity);
        if (skuPrefix == null) {
            throw new IllegalArgumentException("SKU 前缀不能为空");
        }
        if (address == null) {
            throw new IllegalArgumentException("注册地址不能为空");
        }
    }
}
