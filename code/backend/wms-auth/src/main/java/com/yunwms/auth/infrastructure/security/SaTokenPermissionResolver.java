package com.yunwms.auth.infrastructure.security;

import cn.dev33.satoken.stp.StpInterface;
import com.yunwms.auth.domain.port.UserPermissionQuery;
import com.yunwms.auth.domain.port.UserRoleQuery;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把 Sa-Token 的权限查询接到本模块的端口上。
 *
 * <p>它是唯一一处「Sa-Token 认识本模块」的桥：端口签名只出现 {@code long} 与 {@code List<String>}，
 * 领域层与用例层因此不知道权限是怎么算出来的，也不知道鉴定框架是哪个。
 *
 * <p>声明成 Bean 即可生效：Sa-Token 的 Spring 装配（{@code SaBeanInject}）会收集容器里的
 * {@link StpInterface} 并注册进去，不需要额外的 {@code @Configuration}。
 *
 * <p>每次鉴权都查库，不做缓存：权限可能在管理员改角色后立刻变化，缓存要么带来「改了不生效」的窗口，
 * 要么需要一套失效通知。当前每次权限校验是 3 次单表查询（用户角色 / 角色 / 权限点），
 * 在角色数量为个位数时开销可接受；量级变化后再考虑加缓存与失效策略。
 */
@Component
public class SaTokenPermissionResolver implements StpInterface {

    private final UserPermissionQuery userPermissionQuery;
    private final UserRoleQuery userRoleQuery;

    public SaTokenPermissionResolver(UserPermissionQuery userPermissionQuery, UserRoleQuery userRoleQuery) {
        this.userPermissionQuery = userPermissionQuery;
        this.userRoleQuery = userRoleQuery;
    }

    /**
     * 取本次会话主体拥有的权限点。
     *
     * @param loginId   登录标识，由 {@code StpUtil.login(account.id())} 写入，是用户主键
     * @param loginType 账号体系标识；本系统只有一套账号体系，因此不参与判断
     * @return 权限点标识列表，没有权限时为空列表
     * @throws IllegalStateException 登录标识不是数字，说明会话内容被篡改或登录链路写错了
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return userPermissionQuery.findActivePermissionCodesByUserId(toUserId(loginId));
    }

    /**
     * 取本次会话主体拥有的角色标识。
     *
     * <p>与登录响应里的角色列表共用同一条查询：两处口径不同的话，前端会看到一个「登录时有角色、
     * 鉴权时没有」的用户。
     *
     * @param loginId   登录标识，由 {@code StpUtil.login(account.id())} 写入，是用户主键
     * @param loginType 账号体系标识，同上不参与判断
     * @return 角色标识列表，没有角色时为空列表
     * @throws IllegalStateException 登录标识不是数字
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return userRoleQuery.findActiveRoleCodesByUserId(toUserId(loginId));
    }

    /**
     * 登录标识就是 {@code sys_user.id}，在签发令牌时写入。
     *
     * <p>必须同时接受 {@link Number} 与 {@link CharSequence}：签发时传入的是 {@code long}，
     * 但 Sa-Token 从令牌还原出来的 loginId 是字符串（登录标识本身按字符串存取），
     * 只认其中一种会在「登录成功后的第一次鉴权」就抛异常。
     *
     * <p>解析失败一律抛出而不是回落：能静默变成 0 或空权限的话，坏掉的会话看起来就像「没有权限」，
     * 排查时会被引到授权数据上。
     */
    private static long toUserId(Object loginId) {
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        if (loginId instanceof CharSequence text) {
            try {
                return Long.parseLong(text.toString());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("登录标识不是数字，无法用于授权查询：" + loginId, e);
            }
        }
        throw new IllegalStateException("登录标识不是数字，无法用于授权查询：" + loginId);
    }
}
