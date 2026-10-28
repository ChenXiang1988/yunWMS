package com.yunwms.master.interfaces.controller.assembler;

import com.yunwms.master.application.dto.CreatedOwner;
import com.yunwms.master.application.dto.OwnerDetail;
import com.yunwms.master.application.dto.OwnerPage;
import com.yunwms.master.application.dto.ProvisionedOwnerAccount;
import com.yunwms.master.application.dto.TerminationImpact;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.interfaces.controller.response.CreatedOwnerResponse;
import com.yunwms.master.interfaces.controller.response.OwnerDetailResponse;
import com.yunwms.master.interfaces.controller.response.OwnerListItemResponse;
import com.yunwms.master.interfaces.controller.response.OwnerTerminationImpactResponse;
import com.yunwms.master.interfaces.controller.response.ProvisionedOwnerAccountResponse;
import com.yunwms.web.api.PageResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 货主用例结果到响应体的转换。
 *
 * <p>「标识以字符串传输」「状态以枚举名传输」「地址六字段铺平」「详情把摘要铺平」都属于接口层约定，
 * 不属于用例：用例只认数字标识与枚举，是否序列化成字符串、响应体是嵌套还是扁平由契约决定。
 * 转换集中在这里，多个端点各写一遍时最先走形的是状态名——某一处写成中文标签也不会报错。
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

    /**
     * 组装终止影响清单响应。
     *
     * <p>不做任何换算：计数就是计数。这里唯一可能走形的地方是把两个字段接反——
     * 数值类型相同，接反了不会报错，只会在界面上显示成「影响 2 个商品与 3 个账号」。
     *
     * @param impact 影响清单
     * @return 影响清单响应体
     */
    public OwnerTerminationImpactResponse toTerminationImpactResponse(TerminationImpact impact) {
        return new OwnerTerminationImpactResponse(impact.skuCount(), impact.accountCount());
    }

    /**
     * 组装开通主账号响应。
     *
     * <p>只把标识转成字符串，不做其他加工：登录名就是货主编码，任何「美化」（补前缀、转大写）
     * 都会让响应里的值与账号表里真正的登录名不一致，而管理员是要拿它去登录框里核对的。
     *
     * @param account 开通结果
     * @return 开通响应体
     */
    public ProvisionedOwnerAccountResponse toProvisionedAccountResponse(ProvisionedOwnerAccount account) {
        return new ProvisionedOwnerAccountResponse(Long.toString(account.accountId()), account.loginName());
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
