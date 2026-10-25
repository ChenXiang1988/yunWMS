package com.yunwms.auth.interfaces.controller.response;

/**
 * 建角色成功的响应体。
 *
 * <p>标识以字符串传输：{@code Long} 超过 2^53 时 JS 会丢精度，等到出问题再改要动全部前端，
 * 而首日就按字符串发没有任何代价。
 *
 * @param id   新角色的标识
 * @param code 角色编码，按落库值返回
 */
public record CreatedRoleResponse(String id, String code) {
}
