package com.yunwms.master.application.command;

/**
 * 编辑货主档案的入参：全量替换可编辑字段，{@code null} 表示清空。
 *
 * <p>没有 {@code code}、{@code skuPrefix}、{@code status} 与 {@code initialStatus}：
 * 编码是标识、前缀绑定已生成的 SKUID、状态属状态机（3.2），四者都不是「改档案」能碰到的东西。
 * 请求体里不出现它们，多传会被 {@code FAIL_ON_UNKNOWN_PROPERTIES} 拒绝，而不是静默忽略——
 * 忽略会让前端以为改生效了。
 *
 * @param ownerId          被编辑的货主标识
 * @param name             客户名称，可重复
 * @param shortName        货主简称，可为空
 * @param nameEn           英文名称，可为空
 * @param externalCode     外部系统编码，可为空；未删除时唯一
 * @param ownerType        客户类型，取 {@code OWNER_TYPE} 字典的 value
 * @param settlementEntity 结算主体
 * @param country          注册国家，2 位大写字母的 ISO 3166-1 alpha-2 代码
 * @param province         州省，取 {@code PROVINCE} 字典的 value；美国必填
 * @param city             城市
 * @param district         区县，可为空
 * @param postalCode       邮政编码，可为空；是否必填按地区判定
 * @param addressLine      详细地址
 * @param remark           备注，可为空
 */
public record UpdateOwnerCommand(
        long ownerId,
        String name,
        String shortName,
        String nameEn,
        String externalCode,
        String ownerType,
        String settlementEntity,
        String country,
        String province,
        String city,
        String district,
        String postalCode,
        String addressLine,
        String remark) {
}
