package com.yunwms.app.bridge;

import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.master.domain.port.StaffLookup;
import org.springframework.stereotype.Component;

/**
 * 把 {@code wms-master} 的「这个用户是不是在册内部员工」窄端口接到 {@code wms-auth} 的用户查询上。
 *
 * <p><b>为什么这个类在 {@code wms-app}：</b>员工档案在 {@code sys_user}，归 {@code wms-auth}；
 * 端口声明在 {@code wms-master}。两个业务模块互不引用（{@code docs/coding-project.md} 第二节，
 * 由 {@code ModuleBoundaryTest} 拦截），只有 {@code wms-app} 的运行期 classpath 同时包含两边。
 * 方向与 {@link OwnerAssigneeQueryAdapter}（auth 端口 ← app → master 查询）相反，道理相同。
 *
 * <p><b>为什么注入 {@link UserQuery} 而不是 {@code UserQueryService}：</b>后者的
 * {@code detail(long)} 用 {@code BizException} 表达「用户不存在」，而本端口的契约是「不存在返回
 * {@code false}」。用 {@code detail} 就得 catch 业务异常、再判断错误码是不是 40400——把协议层的
 * 错误码当成控制流，装配层不该持有这类知识。{@link UserQuery#findById} 直接返回 {@code Optional}，
 * 与端口契约同形；注入对端<b>域端口</b>而不是应用服务，与 {@link OwnerAccountLookupAdapter} 一致。
 *
 * <p><b>为什么不用登录链路的 {@code UserAccountRepository#findById}：</b>那份模型带着密码哈希，
 * 而且它服务的是认证链路；为了一次布尔判定把凭据搬进「能不能指定为负责人」这条路径没有收益。
 * 代价是本类每次判定要读 3 条语句（账号行、角色授权、数据范围明细，见
 * {@code UserQueryImpl#findById}）——它是指定负责人时的一次就地校验，不进列表页，这个代价换的是
 * 「谁的口径算数」只有一个答案。
 *
 * <p><b>逻辑删除与锁定都不在这里判：</b>{@link UserQuery#findById} 的 {@code selectById} 自带
 * 逻辑删除条件（已删除与不存在同口径），无需再写一次；{@code locked_until} 不进
 * {@code UserProfile}，而端口契约明确不看锁定（锁定是临时状态，不代表员工离职）。
 *
 * <p><b>不缓存、不兜底：</b>一次判定就是一次按主键的读。缓存会让「刚被停用的人还能被指定」在本请求里
 * 看不见；把异常降级成 {@code false} 则会把一次查询故障说成「员工不存在或已停用」，管理员于是去
 * 重新选人而不是报修（与 {@link OwnerAccountLookupAdapter} 同一取向）。
 */
@Component
public class StaffLookupAdapter implements StaffLookup {

    private final UserQuery userQuery;

    public StaffLookupAdapter(UserQuery userQuery) {
        this.userQuery = userQuery;
    }

    /**
     * {@inheritDoc}
     *
     * <p>两个条件必须同时成立：主体类型是内部员工（货主账号与员工同表，只看标识会让一个货主账号被
     * 当成可指定的负责人），且状态为启用（停用是长期状态，重新启用后这个人就能再被指定）。
     */
    @Override
    public boolean isActiveStaff(long userId) {
        return userQuery.findById(userId)
                .map(UserProfile::summary)
                .filter(summary -> summary.subjectType() == SubjectType.STAFF
                        && summary.status() == UserStatus.ACTIVE)
                .isPresent();
    }
}
