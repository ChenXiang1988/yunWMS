package com.yunwms.master.interfaces.controller.assembler;

import com.yunwms.master.application.dto.CreatedOwner;
import com.yunwms.master.application.dto.OwnerDetail;
import com.yunwms.master.application.dto.OwnerPage;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.interfaces.controller.response.CreatedOwnerResponse;
import com.yunwms.master.interfaces.controller.response.OwnerDetailResponse;
import com.yunwms.master.interfaces.controller.response.OwnerListItemResponse;
import com.yunwms.web.api.PageResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 货主用例结果到响应体的转换。
 *
 * <p>「标识以字符串传输」「状态以枚举名传输」「地址六字段铺平」「详情把摘要铺平」都属于接口层约定，
 * 不属于用例：用例只认数字标识与枚举，是否序列化成字符串、响应体是嵌套还是扁平由契约决定。
 * 转换集中在这里，三个端点各写一遍时最先走形的是状态名——某一处写成中文标签也不会报错。
 */
@Component
public class OwnerResponseAssembler {

    /**
     * 组装建档响应。
     *
     * @param createdOwner 建档结果
     * @return 可直接序列化的响应体
     */
    public CreatedOwnerResponse toResponse(CreatedOwner createdOwner) {
        return new CreatedOwnerResponse(Long.toString(createdOwner.id()), createdOwner.code());
    }

    /**
     * 组装分页响应。
     *
     * @param page 一页货主
     * @return 分页响应体，{@code records} 恒不为 {@code null}
     */
    public PageResult<OwnerListItemResponse> toPageResponse(OwnerPage page) {
        List<OwnerListItemResponse> records = page.records().stream()
                .map(OwnerResponseAssembler::toListItem)
                .toList();
        return new PageResult<>(page.pageNo(), page.pageSize(), page.total(), records);
    }

    /**
     * 组装详情响应。
     *
     * <p>把 {@link OwnerDetail} 里的摘要与地址都铺平成一层字段：详情与列表行在 HTTP 上是两个并列对象，
     * 前端按同一套字段名渲染，嵌一层只会让取值路径变成两种。
     *
     * @param detail 货主详情
     * @return 详情响应体
     */
    public OwnerDetailResponse toDetailResponse(OwnerDetail detail) {
        OwnerPage.Summary summary = detail.summary();
        OwnerAddress address = detail.address();
        return new OwnerDetailResponse(
                Long.toString(summary.id()),
                summary.code(),
                summary.name(),
                summary.ownerType(),
                summary.status().name(),
                summary.updatedAt(),
                detail.nameEn(),
                detail.shortName(),
                detail.externalCode(),
                detail.skuPrefix(),
                detail.settlementEntity(),
                address.country(),
                address.province(),
                address.city(),
                address.district(),
                address.postalCode(),
                address.addressLine(),
                detail.remark(),
                detail.createdAt());
    }

    private static OwnerListItemResponse toListItem(OwnerPage.Summary summary) {
        return new OwnerListItemResponse(
                Long.toString(summary.id()),
                summary.code(),
                summary.name(),
                summary.ownerType(),
                summary.status().name(),
                summary.updatedAt());
    }
}
