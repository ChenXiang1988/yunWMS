package com.yunwms.master.infrastructure.security;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.yunwms.master.domain.port.CurrentOperator;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;

/**
 * 用 Sa-Token 读取当前请求的登录用户标识。
 *
 * <p><b>为什么本模块可以直接读 Sa-Token，而不是等 wms-app 装一个适配器：</b>Sa-Token 是技术组件，
 * 不是另一个业务模块的能力。本模块的接口层为了 {@code @SaCheckPermission} 已经依赖它，
 * 同一个库里取一次登录标识不构成跨模块耦合，理由见 {@link CurrentOperator}。
 *
 * <p><b>为什么不把「需先改密」标记也读过来：</b>那是登录链路的状态，由 {@code MustChangePasswordGate}
 * 在每个请求上判，本模块既不该判也无从处理。端口只留一个方法，正是为了让这类复制无处可放。
 *
 * <p>未登录在这里不是异常：鉴权过滤器已经拦过匿名请求，走到用例还取不到会话，只可能是账号在请求途中
 * 被删除或令牌已失效。那是一个要由用例翻译成 40100 的普通情况，不该让会话库的异常类型漏出端口。
 */
@Component
public class SaTokenCurrentOperator implements CurrentOperator {

    @Override
    public OptionalLong currentUserId() {
        try {
            return OptionalLong.of(StpUtil.getLoginIdAsLong());
        } catch (NotLoginException e) {
            return OptionalLong.empty();
        }
    }
}
