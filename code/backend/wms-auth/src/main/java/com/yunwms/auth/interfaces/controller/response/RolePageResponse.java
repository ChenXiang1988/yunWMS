package com.yunwms.auth.interfaces.controller.response;

import java.util.List;

/**
 * 分页响应的 {@code data}：{@code {pageNo, pageSize, total, records}}。
 *
 * <p>结构按 {@code docs/api-convention.md} 第四节的统一分页形态定义。这里定义在角色模块内
 * 而不是放进 {@code wms-web}：全仓目前还没有共享的分页响应类型，本批的路径白名单也不含
 * {@code wms-web}；等第二个分页接口出现时再上移为共享类型，那时改的是两个消费方而不是一个。
 *
 * @param pageNo   页码，从 1 开始
 * @param pageSize 每页条数
 * @param total    满足筛选条件的总数，不是本页条数
 * @param records  本页数据；没有数据时为空数组，不会返回 {@code null}
 */
public record RolePageResponse(int pageNo, int pageSize, long total, List<RoleListItemResponse> records) {
}
