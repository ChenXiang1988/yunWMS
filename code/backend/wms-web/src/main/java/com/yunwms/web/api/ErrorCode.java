package com.yunwms.web.api;

/**
 * 错误码。
 *
 * <p>用五位整数而非 HTTP 状态码：HTTP 状态只表达「哪一类问题」，业务需要区分到「哪一个问题」。
 * 前三位对齐 HTTP 语义，后两位是同类内的序号，便于人读日志时一眼判断大致方向。
 */
public enum ErrorCode {

    OK(0, "成功"),

    BAD_REQUEST(40000, "请求参数不合法"),
    UNAUTHORIZED(40100, "未登录或登录已失效"),

    /**
     * 凭据有效但处于强制改密状态。
     *
     * <p>必须与 40100 区分：此时用户是已登录的，回「未登录」会让前端跳登录页，
     * 形成「登录 → 被踢回登录」的循环。前端须先判本码再判 HTTP 401。
     */
    MUST_CHANGE_PASSWORD(40104, "需先修改初始密码"),

    /** 登录名不存在与密码错误共用此码：分开会把登录框变成账号枚举接口。 */
    LOGIN_FAILED(40101, "登录名或密码不正确"),
    LOGIN_ACCOUNT_DISABLED(40102, "账号已停用"),
    LOGIN_ACCOUNT_LOCKED(40103, "账号已被锁定，请稍后再试"),

    FORBIDDEN(40300, "无权访问"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "数据冲突"),

    /**
     * 登录名已被占用（不区分大小写）。
     *
     * <p>单独成码而非复用 {@link #CONFLICT}：前端需要在「登录名」字段上就地提示，
     * 而不是弹一个泛化的冲突提示。判重只能由数据库唯一索引给出——服务层「先查再插」
     * 在并发下无效。
     */
    LOGIN_NAME_TAKEN(40901, "登录名已被占用"),

    /**
     * 角色编码已被占用（含已删除的角色）。
     *
     * <p>与 {@link #LOGIN_NAME_TAKEN} 同一理由单独成码：编码是建角色表单上的一个字段，
     * 前端要就地挂提示，而不是弹一个泛化的「数据冲突」。判重只能由数据库唯一索引
     * {@code ux_sys_role_code} 给出——服务层「先查再插」在并发下无效。
     *
     * <p>提示里必须点明「含已删除的角色」：{@code ux_sys_role_code} 是全表唯一索引、不带
     * {@code deleted_at} 条件，软删除之后编码仍被占着，而管理界面（含列表）看不到已删除的角色。
     * 不说这一句，管理员会陷入「明明没有这个角色，却说我重复」的困惑。
     */
    ROLE_CODE_TAKEN(40902, "角色编码已被占用（含已删除的角色）"),

    /**
     * 邮箱已被占用（不区分大小写）。
     *
     * <p>与 {@link #LOGIN_NAME_TAKEN} 同一理由单独成码：编辑用户时邮箱是一个表单字段，
     * 前端要就地挂提示，而不是弹一个泛化的「数据冲突」。判重同样只能由数据库唯一索引给出。
     *
     * <p>编号跳过 40902：该段位已用于角色编码占用（见 {@link #ROLE_CODE_TAKEN}）。
     */
    EMAIL_TAKEN(40903, "邮箱已被其他账号使用"),

    /**
     * 货主编码已被占用。
     *
     * <p>编码由编码规则生成，建档人根本没有填过它，撞上唯一索引说明取号与落库之间发生了不该发生的重复
     * （取号缺陷，或历史数据里已有一行同号）。用户对此没有任何可改的输入，能做的只有重新提交，
     * 因此文案里必须写清这一点——否则一条「编码已被占用」的提示会把人引到表单里去找一个不存在的字段。
     *
     * <p>单独成码而非复用 {@link #CONFLICT}：这是建档唯一一处「无需修改任何入参」的冲突，
     * 前端据此可以只提示重试、不挂字段级错误。
     */
    OWNER_CODE_TAKEN(40904, "货主编码已被占用，请重新提交"),

    /**
     * SKU 前缀已被其他货主占用。
     *
     * <p>与 {@link #LOGIN_NAME_TAKEN} 同一理由单独成码：前缀是建档表单上的一个字段，前端要就地挂提示，
     * 而不是弹一个泛化的「数据冲突」。判重只能由 {@code ux_owner_sku_prefix} 给出——服务层
     * 「先查再插」在并发下无效。
     *
     * <p>抛出时提示里必须带上被占用的前缀原文：用户要据此换一个可用前缀，只说「已被占用」
     * 等于让他在 4 位字符的空间里盲猜。占用方即使是草稿也算占用——该索引是全表唯一，草稿同样占着。
     */
    OWNER_SKU_PREFIX_TAKEN(40905, "SKU 前缀已被占用"),

    /**
     * 货主的外部系统编码已被其他货主占用。
     *
     * <p>口径是「未删除的货主」：{@code ux_owner_external_code} 是带 {@code deleted_at IS NULL} 条件的
     * 部分唯一索引，已删除货主的外部码可以被新货主复用。这与 SKU 前缀（全表唯一）不是同一条规则，
     * 两条都不做预检，一律由数据库判定。
     */
    OWNER_EXTERNAL_CODE_TAKEN(40906, "外部码已被占用"),

    INTERNAL(50000, "服务器内部错误");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
