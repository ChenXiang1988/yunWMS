package com.yunwms.auth.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 新增字典项成功的响应体。
 *
 * <p>标识以字符串传输：{@code Long} 超过 2^53 时 JS 会丢精度，等到出问题再改要动全部前端，
 * 而首日就按字符串发没有任何代价（与 {@link CreatedRoleResponse}、{@link CreatedUserResponse} 同一口径）。
 *
 * <p>只回 {@code id}，不回带刚建好的整行：前端提交的就是它屏幕上那份数据，原样回显没有信息量；
 * 它真正缺的是库生成的主键，用来在列表里定位新行。需要最新值（例如排序号被并发改过）时调
 * {@code GET /api/console/dicts/{dictType}/items} 重新取。
 *
 * @param id 新字典项的标识
 */
public record CreatedDictItemResponse(
        @Schema(description = "新字典项的标识，字符串形式的十进制整数；后续编辑、启停、删除都用它定位")
        String id) {
}
