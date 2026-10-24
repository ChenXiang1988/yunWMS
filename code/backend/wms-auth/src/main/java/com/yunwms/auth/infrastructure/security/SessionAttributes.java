package com.yunwms.auth.infrastructure.security;

/**
 * 会话属性名。
 *
 * <p>键只在这里定义：登录写入、后续的货主强制过滤与会话审计读取，必须是同一份键，
 * 各写各的字面量迟早出现「写进 loginIp、读的是 sourceAddress」这类查不出原因的错。
 *
 * <p>值只存 String、基本类型包装类与时间：会话将来可能换到 Redis，存领域对象会让序列化方式绑定到实现。
 */
public final class SessionAttributes {

    /** 登录时刻，值为 {@link java.time.Instant}。 */
    public static final String LOGIN_AT = "loginAt";

    /** 来源地址，值为 {@link String}。 */
    public static final String SOURCE_ADDRESS = "sourceAddress";

    /** 客户端标识，值为 {@link String}。 */
    public static final String CLIENT_IDENTIFIER = "clientIdentifier";

    /** 主体类型编码，值为 {@link String}，取值见 {@code SubjectType}。 */
    public static final String SUBJECT_TYPE = "subjectType";

    /** 所属货主标识，值为 {@link Long}；内部员工不写该属性。 */
    public static final String OWNER_ID = "ownerId";

    private SessionAttributes() {
    }
}
