package com.yunwms.app.bridge;

import com.yunwms.auth.application.command.CreateUserCommand;
import com.yunwms.auth.application.dto.CreatedUser;
import com.yunwms.auth.application.service.UserCreationService;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.master.domain.port.OwnerAccountProvisioner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把 {@code wms-master} 的「为货主建主账号」窄端口接到 {@code wms-auth} 的建号用例上。
 *
 * <p><b>为什么这个类在 {@code wms-app}：</b>账号与建号用例都在 {@code wms-auth}，而
 * {@code auth} 与 {@code master} 互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截）。只有 {@code wms-app} 同时认识两边，与
 * {@link OwnerAccountCounterAdapter} 同一路子。
 *
 * <p><b>为什么委托 {@link UserCreationService} 而不是自己插一行：</b>密码策略、主体与货主的匹配、
 * 邮箱必填口径、{@code must_change_password}、登录名唯一冲突到 40901 的翻译、两张表同事务，
 * 全都是「一条账号怎样才能落库」的一部分，所有者是那个用例。在这里重写一份，等于让同一个问题
 * 有两个答案，而其中一个必然先过期。
 *
 * <p><b>{@code roleIds} 必须为空列表——这是硬约束，不是暂缺：</b>鉴权侧
 * {@code UserPermissionQueryImpl} 见到用户持有任意 {@code builtin = true} 且启用的角色，就动态展开
 * {@code sys_menu} 的全部权限点、不读 {@code sys_role_menu}。给货主账号种一个内置角色，等于让它
 * 当场变成平台超管：能开别的货主、能改角色、能看全部数据。货主侧的角色设计属 3.4 / 4.4，
 * 在这一步之前货主账号一个角色都不该有。改这一行之前先确认那两件事已经落地。
 *
 * <p><b>{@code email} 与 {@code wecomUserId} 传 {@code null}：</b>规格要求账号本身不保存手机号
 * （手机号属联系人档案，3.5），邮箱同属联系信息；{@code ck_user_email_required} 只约束内部员工，
 * 货主账号可以为空。企业微信 userid 只用于员工的消息推送，货主账号用不到。
 *
 * <p>异常一律透出：对端的业务异常（40901 登录名被占用、40000 密码不合规）已经是调用方能据以行动的
 * 结论，在这里重新包装只会丢掉错误码。
 */
@Component
public class OwnerAccountProvisionerAdapter implements OwnerAccountProvisioner {

    private final UserCreationService userCreationService;

    public OwnerAccountProvisionerAdapter(UserCreationService userCreationService) {
        this.userCreationService = userCreationService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>落库结果固定为启用状态、首次登录需改密——这是建号用例的固有语义，本类不提供开关。
     */
    @Override
    public long createMainAccount(long ownerId, String loginName, String displayName, String initialPassword) {
        CreatedUser created = userCreationService.create(new CreateUserCommand(
                loginName,
                displayName,
                SubjectType.OWNER,
                ownerId,
                null,
                null,
                initialPassword,
                // 空列表是硬约束：内置角色会让货主账号越权成平台超管，理由见类注释
                List.of()));
        return created.id();
    }
}
