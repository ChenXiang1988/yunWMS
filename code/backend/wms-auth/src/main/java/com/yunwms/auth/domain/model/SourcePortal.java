package com.yunwms.auth.domain.model;

/**
 * 审计日志的请求来源端：这次操作是从哪个端发出的。
 *
 * <p><b>为什么不复用 {@link AccessiblePortal}。</b>两者取值相同，回答的问题不同：
 * {@code AccessiblePortal} 是**授权**意义上的「这个账号能进哪个端」，它的 {@code code()} 是
 * {@code console} / {@code portal} 的小写编码，是给前端的路由契约；本枚举是**事实**意义上的
 * 「这次请求实际从哪个端到达」，入库取枚举名 {@code CONSOLE} / {@code PORTAL}。合并成一个类型后，
 * 同一列会出现「有时存 name、有时存 code」的写法，而这个错误在写入时不会报错、只在回查时表现为
 * 「有的日志来源端是 console，有的是 CONSOLE」。何况按规格，来源端 MUST NOT 由主体类型推导
 * （见 {@code V038} 的注释），两者本来就不该共用一条推导链。
 *
 * <p>数据库存 {@link #name()}；列为 {@code NULL} 表示没有端的概念（系统内部触发），
 * 读取到未知编码时失败，不静默当成 {@link #CONSOLE}——那会把一次货主端的操作记成仓网端。
 */
public enum SourcePortal {

    /** 仓网管理端，内部员工使用。 */
    CONSOLE,

    /** 货主端，货主账号使用。 */
    PORTAL;

    /**
     * 按数据库中的编码还原。
     *
     * @param code 来源端编码，取值为枚举名
     * @return 对应的来源端
     * @throws IllegalArgumentException 编码未知，说明数据已损坏，不静默回落
     */
    public static SourcePortal fromCode(String code) {
        for (SourcePortal portal : values()) {
            if (portal.name().equals(code)) {
                return portal;
            }
        }
        throw new IllegalArgumentException("未知的来源端编码：" + code);
    }
}
