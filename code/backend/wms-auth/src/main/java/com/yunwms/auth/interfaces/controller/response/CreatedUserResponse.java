package com.yunwms.auth.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 建号成功的响应体。
 *
 * <p>标识以字符串传输：{@code Long} 超过 2^53 时 JS 会丢精度，等到出问题再改要动全部前端，
 * 而首日就按字符串发没有任何代价。
 *
 * @param id        新账号的用户标识
 * @param loginName 登录名，按落库值返回
 */
public record CreatedUserResponse(
        @Schema(description = "新账号的用户标识，字符串形式的十进制整数；后续详情、编辑、启停、分配角色都用它定位，"
                + "同时用于跳转到详情页")
        String id,

        @Schema(description = "新账号的登录名，按落库值返回（未做大小写改写）；与请求里的登录名一致，便于前端核对")
        String loginName) {
}
