package com.yunwms.auth.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 建角色成功的响应体。
 *
 * <p>标识以字符串传输：{@code Long} 超过 2^53 时 JS 会丢精度，等到出问题再改要动全部前端，
 * 而首日就按字符串发没有任何代价。
 *
 * @param id   新角色的标识
 * @param code 角色编码，按落库值返回
 */
public record CreatedRoleResponse(
        @Schema(description = "新角色的标识，字符串形式的十进制整数；后续详情、编辑、启停、删除都用它定位")
        String id,

        @Schema(description = "新角色编码，按落库值返回；程序按编码引用角色，创建后不可修改")
        String code) {
}
