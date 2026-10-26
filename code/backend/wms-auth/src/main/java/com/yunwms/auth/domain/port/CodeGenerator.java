package com.yunwms.auth.domain.port;

/**
 * 按编码规则取号。
 *
 * <p>编码由系统生成、生成后不可修改且全局唯一，因此调用方拿到的就是最终编码，
 * 没有「先占位再改号」这条路径。
 *
 * <p><b>临时归属：本端口放在 {@code wms-auth}，等 {@code wms-master} 落地后迁入。</b>
 * 编码规则属于平台能力，语义上归 {@code wms-master}；但该模块目前没有任何源码，
 * 为一个取号引擎先建模块要同时改父 POM、ArchUnit 的层定义与 {@code wms-app} 的依赖。
 * 迁移时只改包名，方法签名不变。
 *
 * <p><b>事务口径：号段递进独立提交，不随业务事务回滚。</b>
 * 取号成功而业务落库失败时，号段被浪费是可接受的，重号不可接受：
 * 浪费一个号只让号段少一格，而号段本来就不要求连续；一旦两个对象拿到同一个编码，
 * 「编码全局唯一」这条约束就被永久破坏，且发现时往往已经写进了标签与单据。
 * 因此实现必须在自己的事务里提交这次递增，调用方即使在外层事务中调用并随后整体回滚，
 * 取到的号也不退回、不会被第二个请求拿到。
 *
 * <p>正因为号段会独立提交，取号失败（规则未配置、流水用尽）与取号成功但业务失败
 * 是两类不同的事件：两者的失败原因都应保留，不要把后者静默成「号没取到」。
 */
public interface CodeGenerator {

    /**
     * 取下一个编码。
     *
     * <p>同一 {@code ruleCode} 的并发调用必须拿到互不相同的编码：递增由数据库在单条语句内完成，
     * 调用方不需要额外加锁，也必须不要自己「先读当前值再算」。
     *
     * @param ruleCode 规则编码，对应 {@code sys_code_rule.rule_code}；不可为空或空白
     * @return 拼接好的编码，形如 {@code OWNER0006}
     * @throws com.yunwms.auth.domain.exception.CodeRuleException 规则未配置，或流水位数已用尽
     * @throws IllegalArgumentException {@code ruleCode} 为空或空白
     */
    String next(String ruleCode);
}
