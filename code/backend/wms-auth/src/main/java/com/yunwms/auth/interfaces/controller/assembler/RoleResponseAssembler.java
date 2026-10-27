package com.yunwms.auth.interfaces.controller.assembler;

import com.yunwms.auth.application.dto.CreatedRole;
import com.yunwms.auth.application.dto.RoleDetail;
import com.yunwms.auth.application.dto.RolePage;
import com.yunwms.auth.domain.model.RoleSummary;
import com.yunwms.auth.interfaces.controller.response.CreatedRoleResponse;
import com.yunwms.auth.interfaces.controller.response.RoleDetailResponse;
import com.yunwms.auth.interfaces.controller.response.RoleListItemResponse;
import com.yunwms.web.api.PageResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 角色用例结果到响应体的转换。
 *
 * <p>转换集中在这里，是因为「标识以字符串传输」「详情把摘要铺平成一个对象」都属于接口层约定，
 * 不属于用例：用例只认数字标识，是否序列化成字符串、响应体是嵌套还是扁平由契约决定。
 */
@Component
public class RoleResponseAssembler {

    /**
     * 组装建角色响应。
     *
     * @param createdRole 建角色结果
     * @return 可直接序列化的响应体
     */
    public CreatedRoleResponse toCreatedResponse(CreatedRole createdRole) {
        return new CreatedRoleResponse(Long.toString(createdRole.id()), createdRole.code());
    }

    /**
     * 组装分页响应。
     *
     * @param page 一页角色
     * @return 分页响应体，{@code records} 恒不为 {@code null}
     */
    public PageResult<RoleListItemResponse> toPageResponse(RolePage page) {
        List<RoleListItemResponse> records = page.records().stream()
                .map(RoleResponseAssembler::toListItem)
                .toList();
        return new PageResult<>(page.pageNo(), page.pageSize(), page.total(), records);
    }

    /**
     * 组装详情响应。
     *
     * <p>把 {@link RoleDetail} 里的摘要铺平成一层字段：详情与列表行在 HTTP 上是两个并列对象，
     * 前端按同一套字段名渲染，嵌一层只会让取值路径变成两种。
     *
     * @param detail 角色详情
     * @return 详情响应体
     */
    public RoleDetailResponse toDetailResponse(RoleDetail detail) {
        RoleSummary summary = detail.summary();
        List<String> menuIds = detail.menuIds().stream()
                .map(String::valueOf)
                .toList();
        List<String> ownerIds = detail.ownerIds().stream()
                .map(String::valueOf)
                .toList();
        return new RoleDetailResponse(
                Long.toString(summary.id()),
                summary.code(),
                summary.name(),
                summary.builtin(),
                summary.status().name(),
                summary.userCount(),
                summary.permissionCount(),
                summary.remark(),
                menuIds,
                detail.dataScopeType().name(),
                ownerIds);
    }

    private static RoleListItemResponse toListItem(RoleSummary summary) {
        return new RoleListItemResponse(
                Long.toString(summary.id()),
                summary.code(),
                summary.name(),
                summary.builtin(),
                summary.status().name(),
                summary.userCount(),
                summary.permissionCount(),
                summary.remark());
    }
}
