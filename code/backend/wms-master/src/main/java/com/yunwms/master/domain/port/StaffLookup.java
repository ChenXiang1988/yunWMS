package com.yunwms.master.domain.port;

/**
 * 「这个用户是不是一名在册内部员工」的窄端口，实现在 {@code wms-app} 的 {@code bridge} 包。
 *
 * <p><b>为什么本模块只声明端口：</b>员工在 {@code sys_user}，归 {@code wms-auth}；两个业务模块
 * 互不引用（{@code docs/coding-project.md} 第二节，由 {@code ModuleBoundaryTest} 拦截），
 * 只有 {@code wms-app} 的运行期 classpath 同时包含两边。与 {@link OwnerAccountProvisioner} /
 * {@link OwnerAccountLookup} 同一路子。
 *
 * <p><b>为什么只回答一个是/否，不返回员工档案：</b>指定负责人只需要「这个人能不能被指定」；
 * 负责人的姓名、邮箱、角色由需要展示它们的用例各自声明端口。把用户查询复制一份进来，
 * 等于让本模块也能读用户档案，而这份能力迟早会超出「配置负责人」的用途。
 *
 * <p><b>不做「按姓名搜员工」的变体：</b>那是选人控件的数据源，属于 {@code wms-auth} 的用户查询能力。
 * 本端口被 {@link OwnerAssigneeRepository#replace} 的前置校验消费，只收标识，不收关键字。
 */
public interface StaffLookup {

    /**
     * 该用户是否存在且处于启用状态的内部员工。
     *
     * <p><b>「员工」是 {@code sys_user} 里 {@code subject_type = STAFF} 且未逻辑删除的行：</b>
     * 货主账号与员工同表，只按 {@code id} 查会让一个货主账号被当成可指定的负责人；
     * 已逻辑删除的行与「不存在」同口径，因为它已经不能登录，也就不能承担负责人。
     *
     * <p><b>不存在返回 {@code false}，不抛异常：</b>「查无此人」是这里正常的输入之一
     * （前端传错标识、员工刚被删除），把它抛成异常，等于让端口替调用方决定这该是 404 还是 400。
     * 调用方要把它翻译成「员工不存在或已停用」这一类业务错误——两者对管理员是同一句处置建议
     * （重新选一个人），因此本端口不区分，也就没有第二个返回值。
     *
     * <p><b>只看 {@code status = 'ACTIVE'}，不看 {@code locked_until}：</b>锁定是临时状态
     * （连续登录失败触发，到期自动解除）。把锁定算成「不是员工」，同一个人就会今天不能被指定、
     * 明天又可以，而提示里说的是「不存在或已停用」——一句错话。停用才是长期状态。
     *
     * <p><b>这是写入前的一次就地校验，不是并发保证：</b>校验通过之后、写入之前，这名员工可能被停用。
     * 真正守住「停用前必须转交」的是停用用例对 {@link OwnerAssigneeRepository#countByUserId} 的判定；
     * 本端口只为「刚填的那个人根本不是在册员工」这类明显错误给出即时反馈。
     *
     * @param userId 用户标识
     * @return 存在、未逻辑删除、主体类型为内部员工且状态为启用时返回 {@code true}；
     *         其余情形（不存在、已删除、货主账号、已停用）返回 {@code false}
     */
    boolean isActiveStaff(long userId);
}
