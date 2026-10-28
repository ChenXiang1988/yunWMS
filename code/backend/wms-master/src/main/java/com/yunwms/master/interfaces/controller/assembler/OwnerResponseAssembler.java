package com.yunwms.master.interfaces.controller.assembler;

import com.yunwms.master.application.dto.CreatedOwner;
import com.yunwms.master.application.dto.OwnerAssigneeView;
import com.yunwms.master.application.dto.OwnerDetail;
import com.yunwms.master.application.dto.OwnerPage;
import com.yunwms.master.application.dto.ProvisionedOwnerAccount;
import com.yunwms.master.application.dto.TerminationImpact;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.domain.model.OwnerContact;
import com.yunwms.master.interfaces.controller.response.CreatedOwnerResponse;
import com.yunwms.master.interfaces.controller.response.OwnerAssigneeResponse;
import com.yunwms.master.interfaces.controller.response.OwnerContactResponse;
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

    /**
     * 组装单条联系人的响应。
     *
     * <p>只把标识转成字符串，其余字段原样回带：用例返回的就是这次落库的值（它刻意不回读一次库），
     * 在这里再裁剪或转义，响应与库里的内容就会不一致，而审计留痕记的是落库值——
     * 排查「界面显示的和日志里的不一样」时没有任何一处能充当准绳。
     *
     * @param contact 联系人
     * @return 联系人响应体
     */
    public OwnerContactResponse toContactResponse(OwnerContact contact) {
        return new OwnerContactResponse(
                Long.toString(contact.id()),
                contact.name(),
                contact.roleType(),
                contact.phone(),
                contact.email(),
                contact.isPrimary(),
                contact.remark());
    }

    /**
     * 组装联系人列表响应。
     *
     * <p>保持用例返回的顺序（联系人标识升序，即登记先后），不在这里再排一次：顺序是仓储端口契约的一部分，
     * 两处各排一次时「同一毫秒登记的两条谁在前」会得到两个答案，而两处都「看起来对」。
     *
     * @param contacts 联系人列表，可为空列表
     * @return 联系人响应体列表，恒不为 {@code null}
     */
    public List<OwnerContactResponse> toContactListResponse(List<OwnerContact> contacts) {
        return contacts.stream()
                .map(this::toContactResponse)
                .toList();
    }

    /**
     * 组装货主负责客服的响应。
     *
     * <p><b>空角色必须转成 {@code null}，不能直接 {@code Long.toString}：</b>对 {@code null} 调用它得到的是
     * 字符串 {@code "null"}，前端会当成一名标识为 null 的员工渲染出来，而实际情况是「这个角色还没有人」。
     * 三种「空」在页面上是三件事，转换只在这一处做，避免每个消费点各自判一次。
     *
     * <p>{@code ownerId} 原样回带用例给的值（就是路径里那个），不在这里另取一次：它不参与任何判定，
     * 只是供前端丢弃切换货主后才返回的过期响应（理由见 {@code OwnerAssigneeResponse}）。
     *
     * @param view 负责客服的显示模型
     * @return 负责客服响应体；主、副负责人未配置时对应字段为 {@code null}
     */
    public OwnerAssigneeResponse toAssigneeResponse(OwnerAssigneeView view) {
        Long primaryUserId = view.primaryUserId();
        Long secondaryUserId = view.secondaryUserId();
        return new OwnerAssigneeResponse(
                Long.toString(view.ownerId()),
                primaryUserId == null ? null : Long.toString(primaryUserId),
                secondaryUserId == null ? null : Long.toString(secondaryUserId));
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
