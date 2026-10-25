package com.yunwms.auth.config;

import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;

import java.util.Set;

/**
 * 「需先改密」状态下的访问判定。
 *
 * <p>规格要求：用初始密码登录成功后，系统仅允许访问改密界面，其他业务接口返回未授权。
 * 判定做成不依赖容器的纯函数，是因为它只有两个输入（请求路径、会话标记）和一个结论，
 * 却能决定「用户能不能继续用系统」——这类规则必须能被单测逐条钉住，而不是只能靠起服务点一遍。
 *
 * <p>拒绝时抛 {@link BizException}：鉴权过滤器会把异常交给 {@code RestAuthResponses}，
 * 由它把错误码翻译成响应体，本类不碰 HTTP 状态码与序列化。
 */
public final class MustChangePasswordGate {

    /**
     * 待改密状态下仍可访问的路径。
     *
     * <p>只有改密接口本身。它必须与 {@code AuthController} 上 {@code /api/auth} 与 {@code /password}
     * 的映射一致：写错一个字，用户改不了密也出不去，等于把账号锁死。
     */
    private static final Set<String> ALLOWED_PATHS = Set.of("/api/auth/password");

    private MustChangePasswordGate() {
    }

    /**
     * 校验本次请求是否放行。
     *
     * @param requestPath        请求路径，可以带查询串与尾斜杠，本方法自行归一化
     * @param mustChangePassword 当前会话是否处于「需先改密」状态
     * @throws BizException 需先改密且路径不在放行清单内，错误码为 {@link ErrorCode#MUST_CHANGE_PASSWORD}
     */
    public static void assertAllowed(String requestPath, boolean mustChangePassword) {
        if (!mustChangePassword) {
            return;
        }
        if (ALLOWED_PATHS.contains(normalize(requestPath))) {
            return;
        }
        throw new BizException(ErrorCode.MUST_CHANGE_PASSWORD);
    }

    /**
     * 把请求路径归一化成放行清单里的写法。
     *
     * <p>尾斜杠必须容忍：Spring MVC 的路由匹配把 {@code /api/auth/password/} 与不带尾斜杠视为同一个接口，
     * 拦截若只认一种写法，用户就会遇到「接口存在却被拒」的 40104，而他没有别的路可走。
     *
     * <p>查询串一并去掉：{@code SaRequest#getRequestPath} 取的是 {@code getRequestURI}，本来就不含查询串，
     * 但这条实现细节不该成为正确性的前提——本方法只依赖「路径判断不该看参数」这一条。
     *
     * @param requestPath 原始请求路径，可为 {@code null}
     * @return 去查询串、去尾斜杠后的路径；空输入返回空串
     */
    private static String normalize(String requestPath) {
        if (requestPath == null || requestPath.isEmpty()) {
            return "";
        }
        int queryIndex = requestPath.indexOf('?');
        String path = queryIndex >= 0 ? requestPath.substring(0, queryIndex) : requestPath;
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
