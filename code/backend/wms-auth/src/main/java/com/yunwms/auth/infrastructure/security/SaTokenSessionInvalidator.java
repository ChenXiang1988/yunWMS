package com.yunwms.auth.infrastructure.security;

import cn.dev33.satoken.stp.StpUtil;
import com.yunwms.auth.domain.port.SessionInvalidator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用 Sa-Token 使会话失效。
 *
 * <p>失效落在「令牌再也换不到登录身份」这一层，而不是在会话里写一个「已踢出」标记再让业务代码去判：
 * 标记要靠每个入口自觉检查，漏掉一个入口等于没踢；令牌失效由鉴权过滤器统一拦住，不依赖业务代码配合。
 * 因此这里用 {@code logout} 系动作而不是 {@code kickout}——两者的失效效果相同，区别只是被踢的人
 * 下一次能读到不同的原因文案，本系统所有未登录都用同一个错误码，那点区别到不了调用方。
 *
 * <p>「保留当前会话」只能逐个令牌登出：{@code logout(loginId)} 在最后一个终端被移除时会把账号会话
 * 一并删掉，而主体类型、货主归属与「需先改密」标记都挂在账号会话上，用它会把发起请求的这个会话
 * 连同身份信息一起打掉。
 */
@Component
public class SaTokenSessionInvalidator implements SessionInvalidator {

    @Override
    public void invalidateAllOf(long userId) {
        StpUtil.logout(userId);
    }

    @Override
    public void invalidateOthersOfCurrent(long userId) {
        String currentToken = StpUtil.getTokenValue();
        // 取到的是账号会话里终端列表的副本，逐个登出不会影响本次遍历
        List<String> tokenValues = StpUtil.getTokenValueListByLoginId(userId);
        for (String tokenValue : tokenValues) {
            // 拿候选值去比当前值：当前请求取不到令牌时 currentToken 为 null，比较结果为「不相同」，
            // 也就是照常登出——没有当前会话就没有要保留的会话；反过来拿 null 调 equals 会空指针
            if (!tokenValue.equals(currentToken)) {
                StpUtil.logoutByTokenValue(tokenValue);
            }
        }
    }
}
