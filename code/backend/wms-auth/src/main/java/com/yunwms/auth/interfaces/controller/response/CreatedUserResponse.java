package com.yunwms.auth.interfaces.controller.response;

/**
 * 建号成功的响应体。
 *
 * <p>标识以字符串传输：{@code Long} 超过 2^53 时 JS 会丢精度，等到出问题再改要动全部前端，
 * 而首日就按字符串发没有任何代价。
 *
 * @param id        新账号的用户标识
 * @param loginName 登录名，按落库值返回
 */
public record CreatedUserResponse(String id, String loginName) {
}
