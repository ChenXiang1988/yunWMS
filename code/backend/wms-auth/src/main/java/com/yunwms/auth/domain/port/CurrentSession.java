package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.SubjectType;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * 当前请求所属会话的读写入口。
 *
 * <p>登录、强制改密拦截、改密三处都要读或写同一个「需先改密」标记：登录时写入、拦截时读取、
 * 改密成功后清除。三者若各自去 {@code StpUtil} 取会话，键名与语义会各写一遍，
 * 迟早出现「登录写了、拦截读的是另一个键」这种查不出原因的错。这里把它收成一个端口，
 * 顺便让上述三处都能被 Mockito 驱动，不必起容器。
 *
 * <p>用户标识只暴露 {@code OptionalLong}，不抛会话实现自己的异常：端口不认识 Sa-Token，
 * 「当前没有登录」由调用方翻译成业务错误码。
 */
public interface CurrentSession {

    /**
     * 取当前会话的登录用户标识。
     *
     * @return 用户标识；当前请求没有有效会话时为空
     */
    OptionalLong currentUserId();

    /**
     * 取当前会话的主体类型。
     *
     * <p>数据范围解析必须区分货主账号与内部员工：货主账号的范围由所属货主强制决定，
     * 不经任何角色配置。主体类型在登录时写进会话（见 {@code SaTokenSessionTokenIssuer}），
     * 这里只读会话，不回查数据库——解析在每次请求上都会跑一次。
     *
     * @return 主体类型；当前请求没有有效会话、或会话里没有该属性时为空
     * @throws IllegalArgumentException 会话里的编码未知，说明会话被写坏
     */
    Optional<SubjectType> currentSubjectType();

    /**
     * 取当前会话的登录用户姓名。
     *
     * <p>审计写入要用它填 {@code operator_name}：那是写入当时的姓名快照，不随人员之后改名变化
     * （见 {@code sys_audit_log.operator_name} 的列注释）。放在会话端口上而不是让审计实现回查
     * {@code sys_user}：回查会让每条审计多一次 SELECT，且账号已被逻辑删除时查不到人，
     * 只能退回一个更不准确的占位值——而那个人当时确实登录着、确实做了这件事。
     *
     * @return 姓名；当前请求没有有效会话、或会话里没有该属性时为空
     */
    Optional<String> currentUserDisplayName();

    /**
     * 取当前会话所属的货主标识。
     *
     * <p>只有货主账号会有该属性。会话里的货主标识来自登录时读取的账号行，不从请求参数取：
     * 前端传来的货主标识可以被改，而这条是强制隔离的唯一依据。
     *
     * @return 所属货主标识；内部员工或没有有效会话时为空
     */
    OptionalLong currentOwnerId();

    /**
     * 当前会话是否处于「需先改密」状态。
     *
     * <p>拦截链路在每个请求上都会问一次，因此这里只读会话，不回查数据库：
     * 改密成功后标记会被清掉，数据库与标记在同一次请求里对齐。
     *
     * @return 需先改密返回 {@code true}；已登录但无此标记、或根本没有会话时返回 {@code false}
     */
    boolean mustChangePassword();

    /**
     * 把当前会话标记为「需先改密」。
     *
     * <p>由登录用例在令牌签发之后调用：会话在签发时才存在，先标记会拿不到会话。
     */
    void markMustChangePassword();

    /**
     * 清除当前会话的「需先改密」标记。
     *
     * <p>由改密用例在密码落库之后调用。标记落在账号会话上，因此同一账号的其他令牌也会一起解除拦截，
     * 这正是期望行为：改的是账号的密码，不是某一个令牌的密码。
     */
    void clearMustChangePassword();
}
