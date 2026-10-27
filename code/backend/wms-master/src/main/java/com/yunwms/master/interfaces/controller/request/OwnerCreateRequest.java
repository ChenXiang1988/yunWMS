package com.yunwms.master.interfaces.controller.request;

import com.yunwms.master.domain.model.OwnerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新建货主的请求体。
 *
 * <p>这里只声明单字段约束：必填与长度。长度上限取自 {@code owner} 表对应列的宽度，
 * 没有它们，超长输入会以数据库的 {@code 22001} 出现在 50000 上，而那是一次纯粹的入参错误。
 *
 * <p>SKU 前缀的格式（4 位、排除 {@code 0}/{@code O}/{@code 1}/{@code I}）不写在注解里：
 * 这条规则已经由 {@code SkuPrefix} 这一个类型持有，再写一遍正则就是两处定义，
 * 改一处忘一处时会表现成「注解放过了、领域拒绝」「领域放过了、库里 CHECK 拒绝」。
 * 把正则放在这里，还等于允许出现「注解版口径」这种只在 HTTP 入口生效的第二真源。
 *
 * <p>没有 {@code code}：系统编码由取号引擎生成。没有 {@code status}：建档后的状态由
 * {@code initialStatus} 这一次性决定，而不是一个可随时改的字段。多传这两个字段会被
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} 拒绝，而不是静默忽略。
 *
 * @param name             客户名称，可重复
 * @param nameEn           英文名称，可为空
 * @param shortName        货主简称，可为空
 * @param externalCode     外部系统编码，可为空；未删除时唯一
 * @param skuPrefix        SKU 前缀，必填；4 位大写字母或数字，不能包含 0、O、1、I
 * @param ownerType        客户类型：{@code BRAND} 品牌方，{@code FORWARDER} 货代同行
 * @param settlementEntity 结算主体
 * @param country          注册国家，2 位大写字母的国家代码
 * @param province         州省；国家为美国时必填
 * @param city             城市
 * @param district         区县，可为空
 * @param postalCode       邮政编码；无邮编的地区（港澳台）可为空
 * @param addressLine      详细地址
 * @param remark           备注，可为空
 * @param initialStatus    初始状态：{@code DRAFT} 保存草稿，{@code ACTIVE} 直接提交
 */
public record OwnerCreateRequest(
        @NotBlank(message = "客户名称不能为空")
        @Size(max = 100, message = "客户名称不能超过 100 个字符")
        @Schema(description = "客户名称，必填，最长 100 个字符；可与其他货主重名——名称不是标识，"
                + "系统编码才是", example = "深圳某某贸易")
        String name,

        @Size(max = 100, message = "英文名称不能超过 100 个字符")
        @Schema(description = "英文名称，最长 100 个字符，可为空", example = "Shenzhen ABC Trading")
        String nameEn,

        @Size(max = 20, message = "货主简称不能超过 20 个字符")
        @Schema(description = "货主简称，最长 20 个字符，可为空；用于 SKUID 标签，为空时展示层取名称前 8 字",
                example = "某某贸易")
        String shortName,

        @Size(max = 64, message = "外部码不能超过 64 个字符")
        @Schema(description = "外部系统编码，最长 64 个字符，可为空；在未删除的货主之间唯一，"
                + "被占用返回 40906", example = "EXT-001")
        String externalCode,

        @NotBlank(message = "SKU 前缀不能为空")
        @Size(max = 4, message = "SKU 前缀不能超过 4 个字符")
        @Schema(description = "SKU 前缀，必填：4 位大写字母或数字，且不能包含 0、O、1、I（这四个字符"
                + "在标签上易与其他字符认混）。系统拒绝含这些字符的输入，不做自动替换。全局唯一（含草稿），"
                + "已被占用返回 40905；创建后不可修改，也不会出现在编辑接口里", example = "AMZ2")
        String skuPrefix,

        @NotBlank(message = "客户类型不能为空")
        @Size(max = 20, message = "客户类型不能超过 20 个字符")
        @Schema(description = "客户类型，必填，取自 OWNER_TYPE 字典的启用项：BRAND 品牌方，"
                + "FORWARDER 货代同行；不在字典里返回 40000。仅用于筛选与统计，不改变数据模型",
                example = "BRAND")
        String ownerType,

        @NotBlank(message = "结算主体不能为空")
        @Size(max = 100, message = "结算主体不能超过 100 个字符")
        @Schema(description = "结算主体，必填，最长 100 个字符；开票与对账的抬头", example = "深圳某某贸易有限公司")
        String settlementEntity,

        @NotBlank(message = "注册国家不能为空")
        @Size(max = 2, message = "注册国家不能超过 2 个字符")
        @Schema(description = "注册国家，必填，2 位大写字母的 ISO 3166-1 alpha-2 代码，取自 COUNTRY 字典的"
                + "启用项；不在字典里返回 40000", example = "CN")
        String country,

        @Size(max = 64, message = "州省不能超过 64 个字符")
        @Schema(description = "州省，取 PROVINCE 字典中属于所选国家的启用项，不为空时必须属于该国（否则 40000）；"
                + "国家为 US 时必填", example = "CN-GD")
        String province,

        @NotBlank(message = "注册城市不能为空")
        @Size(max = 64, message = "注册城市不能超过 64 个字符")
        @Schema(description = "注册城市，必填，最长 64 个字符", example = "深圳")
        String city,

        @Size(max = 64, message = "区县不能超过 64 个字符")
        @Schema(description = "区县，最长 64 个字符，可为空", example = "南山区")
        String district,

        @Size(max = 20, message = "邮政编码不能超过 20 个字符")
        @Schema(description = "邮政编码，最长 20 个字符；港澳台等无邮编地区可为空，其余地区必填，"
                + "缺失返回 40000", example = "518000")
        String postalCode,

        @NotBlank(message = "详细地址不能为空")
        @Size(max = 255, message = "详细地址不能超过 255 个字符")
        @Schema(description = "详细地址，必填，最长 255 个字符", example = "科技园路 1 号")
        String addressLine,

        @Size(max = 500, message = "备注不能超过 500 个字符")
        @Schema(description = "备注，最长 500 个字符，可为空", example = "")
        String remark,

        @NotNull(message = "初始状态不能为空")
        @Schema(description = "初始状态，必填，只接受 DRAFT（保存草稿，不生成主账号、不出现在其他模块的货主下拉中）"
                + "与 ACTIVE（直接提交，合作中）；POTENTIAL / SUSPENDED / TERMINATED 只能由状态流转产生，"
                + "此处传入返回 40000", example = "ACTIVE")
        OwnerStatus initialStatus) {
}
