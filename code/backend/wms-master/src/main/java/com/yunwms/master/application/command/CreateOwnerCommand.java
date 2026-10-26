package com.yunwms.master.application.command;

import com.yunwms.master.domain.model.OwnerStatus;

/**
 * 新建货主的入参。
 *
 * <p>携带建档所需的原始值，不做任何清洗与拼装：去空白、空串按未填处理、SKU 前缀的格式判定
 * 都属于用例层与领域模型，与建档的校验顺序同处一处。放在协议层会让「接口能建出什么货主」
 * 取决于调用方是不是 HTTP。
 *
 * <p>地址的六个字段平铺而不收成值对象：入参在通过校验之前不能构造 {@code OwnerAddress}，
 * 否则非法地址会在协议层抛出领域异常，落到全局异常处理就是 50000，而它其实是一次入参错误。
 *
 * <p>没有 {@code code}：系统编码由取号引擎生成，是系统的输出而不是建档人的输入。
 * 没有 {@code status}：落草稿还是直接提交由 {@code initialStatus} 决定，两者的区别是「这次点的是哪个按钮」，
 * 不是货主的一个属性。
 *
 * @param name             客户名称，可重复
 * @param shortName        货主简称，可为空
 * @param nameEn           英文名称，可为空
 * @param externalCode     外部系统编码，可为空；未删除时唯一
 * @param skuPrefix        SKU 前缀，必填，4 位大写字母或数字且不含 0、O、1、I
 * @param ownerType        客户类型，取 {@code OWNER_TYPE} 字典的 value
 * @param settlementEntity 结算主体
 * @param country          注册国家，2 位大写字母的 ISO 3166-1 alpha-2 代码
 * @param province         州省，取 {@code PROVINCE} 字典的 value；美国必填
 * @param city             城市
 * @param district         区县，可为空
 * @param postalCode       邮政编码，可为空；是否必填按地区判定
 * @param addressLine      详细地址
 * @param remark           备注，可为空
 * @param initialStatus    初始状态，只接受 {@code DRAFT}（保存草稿）或 {@code ACTIVE}（直接提交）
 */
public record CreateOwnerCommand(
        String name,
        String shortName,
        String nameEn,
        String externalCode,
        String skuPrefix,
        String ownerType,
        String settlementEntity,
        String country,
        String province,
        String city,
        String district,
        String postalCode,
        String addressLine,
        String remark,
        OwnerStatus initialStatus) {
}
