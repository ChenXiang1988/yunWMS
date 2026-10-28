package com.yunwms.master.domain.port;

/**
 * 为货主创建主账号，实现在 {@code wms-app} 的 {@code bridge} 包。
 *
 * <p><b>为什么本模块只声明端口：</b>账号落在 {@code sys_user}，建号用例在 {@code wms-auth}；
 * 两个业务模块互不引用（{@code docs/coding-project.md} 第二节），只有 {@code wms-app} 同时认识两边，
 * 与 {@link OwnerCodeGenerator} / {@link DictLookup} 同一路子。
 *
 * <p><b>校验不在本端口重做：</b>主体与货主是否匹配、初始密码是否合规、登录名是否已被占用，
 * 都由 {@code wms-auth} 的建号用例负责——它是「一条账号怎样才能落库」的唯一所有者。
 * 这里只做委托：登录名冲突会以 {@code 40901} 抛出，密码不合规会以 {@code 40000} 抛出，
 * 两个错误码属于协议层，由对端用例决定，本端口不翻译也不改写。
 *
 * <p><b>实现 MUST 不分配任何角色（硬约束）：</b>鉴权侧见到用户持有任意 {@code builtin = true}
 * 且启用的角色，就动态展开 {@code sys_menu} 的全部权限点、不读 {@code sys_role_menu}。
 * 给货主账号种一个内置角色，等于让它当场变成平台超管——它能开别的货主、能改角色、能看全部数据。
 * 货主侧的角色设计属 3.4 / 4.4，在这一步之前货主账号一个角色都不该有。
 *
 * <p>明文密码只在调用栈里传递，不落日志、不进异常消息：它的最终形态是 {@code sys_user.password_hash}。
 */
public interface OwnerAccountProvisioner {

    /**
     * 创建一条货主主账号。
     *
     * @param ownerId         账号绑定的货主标识，创建后不可变更
     * @param loginName       登录名，由调用方按货主系统编码给出；全局唯一且不区分大小写
     * @param displayName     展示名，已由调用方归一化并保证非空
     * @param initialPassword 初始密码明文，需满足密码策略；建号后账号被标记为首次登录需修改
     * @return 新建账号的标识
     * @throws RuntimeException 失败时抛出对端用例的业务异常，由调用方原样透出：当前会话无效（40100）、
     *                          密码不合规或货主不存在（40000）、登录名已被占用（40901）。
     *                          错误码属于协议层，本端口不定义也不翻译
     */
    long createMainAccount(long ownerId, String loginName, String displayName, String initialPassword);
}
