package com.yunwms.master.domain.port;

import java.util.OptionalLong;

/**
 * 按登录名反查账号归属的货主，供「开通主账号」用例判重。
 *
 * <p><b>为什么本模块只声明端口、不实现：</b>登录名与归属落在 {@code sys_user}，它属于 {@code wms-auth}；
 * {@code auth} 与 {@code master} 互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截）。只有 {@code wms-app} 的运行期 classpath 同时包含两边，
 * 所以实现在 {@code wms-app} 的 {@code bridge} 包里，与 {@link OwnerAccountCounter} 同一路子。
 *
 * <p><b>为什么与 {@link OwnerAccountCounter} 分成两个端口：</b>两者回答的不是同一个问题，
 * 调用方也不同。计数服务终止影响清单（只读，管理端看得到），反查服务开号（写入口，
 * 决定这次提交要继续还是被拒）。合并成一个端口，等于让「能看影响清单」的用例也拿得到开号所需的判定能力，
 * 而它们之间没有任何共用逻辑。
 *
 * <p><b>它不是判重依据：</b>同一瞬间的两次反查之间可能有人建了同名账号。真正的判重由
 * {@code ux_sys_user_login_name} 给出，本端口只用来把冲突说清楚——「你已经开过了」与
 * 「这个登录名被别人占了」是两条不同的处置建议，合成一条管理员就分不清该刷新页面还是该找人。
 *
 * <p>已逻辑删除的账号不计入，与 {@link OwnerAccountCounter} 同一口径。
 */
public interface OwnerAccountLookup {

    /**
     * 按登录名反查归属货主。
     *
     * <p>登录名全局唯一（{@code ux_sys_user_login_name} 建在 {@code lower(login_name)} 上），
     * 故最多一条。
     *
     * @param loginName 登录名，调用方保证非空
     * @return 归属货主标识；登录名不存在、已逻辑删除或该账号不归属任何货主（内部员工）时为 empty
     */
    OptionalLong findOwnerIdByLoginName(String loginName);
}
