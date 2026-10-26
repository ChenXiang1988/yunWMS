package com.yunwms.master.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 货主建档成功的响应体。
 *
 * <p>标识以字符串传输：{@code Long} 超过 2^53 时 JS 会丢精度，等到出问题再改要动全部前端，
 * 而首日就按字符串发没有任何代价（{@code docs/api-convention.md} 第五节）。
 *
 * <p>只回标识与编码，不回完整档案：前端刚提交的就是那份数据，原样回显没有信息量；
 * 需要最新值时取详情（3.1b-2）。
 *
 * @param id   新货主的标识，字符串形式的十进制整数
 * @param code 系统编码，形如 {@code OWNER0001}；由编码规则生成，只读，后续编辑不会再变
 */
public record CreatedOwnerResponse(
        @Schema(description = "新货主的标识，字符串形式的十进制整数；后续详情、编辑都用它定位",
                example = "5")
        String id,

        @Schema(description = "系统编码，形如 OWNER0001，由编码规则生成且不可修改；被占用时接口返回 40904，"
                + "此时重新提交即可", example = "OWNER0001")
        String code) {
}
