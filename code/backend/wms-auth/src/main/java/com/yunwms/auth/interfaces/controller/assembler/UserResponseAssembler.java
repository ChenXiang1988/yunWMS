package com.yunwms.auth.interfaces.controller.assembler;

import com.yunwms.auth.application.dto.CreatedUser;
import com.yunwms.auth.application.dto.UserPage;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.model.UserSummary;
import com.yunwms.auth.interfaces.controller.response.CreatedUserResponse;
import com.yunwms.auth.interfaces.controller.response.UserDetailResponse;
import com.yunwms.auth.interfaces.controller.response.UserListItemResponse;
import com.yunwms.web.api.PageResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户用例结果到响应体的转换。
 *
 * <p>转换集中在这里，是因为「标识以字符串传输」「详情把摘要铺平成一个对象」都属于接口层约定，
 * 不属于用例：用例只认数字标识，是否序列化成字符串、响应体是嵌套还是扁平由契约决定。
 */
@Component
public class UserResponseAssembler {

    /**
     * 组装建号响应。
     *
     * @param createdUser 建号结果
     * @return 可直接序列化的响应体
     */
    public CreatedUserResponse toResponse(CreatedUser createdUser) {
        return new CreatedUserResponse(Long.toString(createdUser.id()), createdUser.loginName());
    }

    /**
     * 组装分页响应。
     *
     * @param page 一页用户
     * @return 分页响应体，{@code records} 恒不为 {@code null}
     */
    public PageResult<UserListItemResponse> toPageResponse(UserPage page) {
        List<UserListItemResponse> records = page.records().stream()
                .map(UserResponseAssembler::toListItem)
                .toList();
        return new PageResult<>(page.pageNo(), page.pageSize(), page.total(), records);
    }

    /**
     * 组装详情响应。
     *
     * @param profile 用户详情
     * @return 详情响应体
     */
    public UserDetailResponse toDetailResponse(UserProfile profile) {
        UserSummary summary = profile.summary();
        return new UserDetailResponse(
                Long.toString(summary.id()),
                summary.loginName(),
                summary.displayName(),
                summary.subjectType().name(),
                summary.ownerId() == null ? null : Long.toString(summary.ownerId()),
                summary.ownerName(),
                summary.status().name(),
                summary.roleCodes(),
                summary.lastLoginAt(),
                profile.email(),
                profile.wecomUserId(),
                profile.dataScopeType(),
                profile.createdAt(),
                profile.roleIds().stream().map(String::valueOf).toList());
    }

    private static UserListItemResponse toListItem(UserSummary summary) {
        return new UserListItemResponse(
                Long.toString(summary.id()),
                summary.loginName(),
                summary.displayName(),
                summary.subjectType().name(),
                summary.ownerId() == null ? null : Long.toString(summary.ownerId()),
                summary.ownerName(),
                summary.status().name(),
                summary.roleCodes(),
                summary.lastLoginAt());
    }
}
