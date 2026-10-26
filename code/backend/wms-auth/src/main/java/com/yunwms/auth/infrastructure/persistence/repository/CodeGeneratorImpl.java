package com.yunwms.auth.infrastructure.persistence.repository;

import com.yunwms.auth.domain.exception.CodeRuleException;
import com.yunwms.auth.domain.port.CodeGenerator;
import com.yunwms.auth.infrastructure.persistence.entity.CodeRuleSequenceRow;
import com.yunwms.auth.infrastructure.persistence.mapper.SysCodeRuleMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CodeGenerator} 的实现：一条 {@code UPDATE ... RETURNING} 原子取号，再按规则拼装。
 *
 * <p><b>为什么是 {@code REQUIRES_NEW} 而不是随调用方的事务走。</b>
 * 取号要保证的是「同一个编码不会被发两次」，而不是「号段与业务数据同生共死」。
 * 若取号加入调用方事务，业务落库失败回滚会把 {@code current_value} 一并退回，
 * 看似省下了一个号，实际上换来了一个更难查的问题：号段退回去之后，
 * 同一段时间内已经发出去并贴在实物或单据上的号仍在流通，
 * 下一次取号会把它再发一遍——这是重号，不是浪费。
 *
 * <p>所以这里主动开一个新事务并提交：外层无论提交还是回滚，号都已经作废，不再发出。
 * 代价是外层事务持有一个连接、本方法再借一个（{@code REQUIRES_NEW} 不会复用外层连接），
 * 峰值连接数是「业务事务数 × 2」。这是有意的取舍——浪费一个号只影响号段的连续性，
 * 而号段本来就不要求连续（{@code step} 允许大于 1、取号失败也不回退）；
 * 重号则无法事后修复：编码已经写在实物标签与单据上。
 *
 * <p>为什么不在本类里自己控制 {@code autoCommit} 或另起线程：前者绕开 Spring 的事务抽象后
 * 与外层事务的连接状态互相干扰，后者会丢掉事务上下文且异常无法回传。
 *
 * <p>拼装放在这里而不是 SQL 里：{@code lpad} 只能补齐、不能判断溢出，
 * 而「位数用满后拒绝生成而不是进位」这条语义必须有人明确执行（见 {@link CodeRuleException}）。
 *
 * <p><b>临时归属：本类随 {@link CodeGenerator} 一并暂放 {@code wms-auth}，{@code wms-master}
 * 落地后迁入。</b>理由见端口上的说明。
 */
@Repository
public class CodeGeneratorImpl implements CodeGenerator {

    /**
     * 补零用的字符。
     *
     * <p>不复用 {@code String.format("%0Nd")}：位数来自数据库的 {@code seq_length}，
     * 拼进格式串前要先确认它是合法值；而 {@code "0".repeat(n)} 只接受一个长度，
     * 位数非法时表现为明显的负长度异常，不会悄悄生成一个格式串。
     */
    private static final String PADDING = "0";

    private final SysCodeRuleMapper sysCodeRuleMapper;

    public CodeGeneratorImpl(SysCodeRuleMapper sysCodeRuleMapper) {
        this.sysCodeRuleMapper = sysCodeRuleMapper;
    }

    /**
     * 取下一个编码。
     *
     * <p>判定顺序固定为「先拒绝空规则编码 → 取号 → 判规则是否存在 → 判位数是否溢出 → 拼装」。
     * 空规则编码排在最前，是为了不让一次调用方 bug 变成一次数据库往返，
     * 更不让它被当成「规则未配置」——那会指向错误的责任方。
     *
     * <p>位数判定用「分配到的值转成十进制后的长度」，不用 {@code 10^seqLength} 求幂：
     * 位数是业务口径（4 位就是 4 个字符），幂等值在 {@code seqLength} 取到上界附近时
     * 还要担心溢出，直接用长度判断没有这类问题。
     *
     * @param ruleCode 规则编码，对应 {@code sys_code_rule.rule_code}
     * @return 拼接好的编码，形如 {@code OWNER0006}
     * @throws IllegalArgumentException 规则编码为 {@code null} 或空白
     * @throws CodeRuleException        规则未配置，或本次分配到的流水号已超出位数
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("编码规则编码不能为空");
        }
        CodeRuleSequenceRow row = sysCodeRuleMapper.advance(ruleCode);
        if (row == null) {
            // 命中零行是正常结果：规则没落库。这里不能返回空串或退回一个默认编码，
            // 那会让调用方拿到一个看起来正常的编码，直到唯一索引或人工核对时才发现
            throw CodeRuleException.notConfigured(ruleCode);
        }
        String prefix = row.getPrefix();
        Integer seqLength = row.getSeqLength();
        Long currentValue = row.getCurrentValue();
        if (prefix == null || seqLength == null || currentValue == null) {
            // RETURNING 只返回被写的行，这三列在库内都是 NOT NULL；
            // 映射不到只可能是 SQL 与投影的列名对不上，属于编程错误，不当业务失败处理。
            // prefix 为 null 必须在这里拦下：字符串拼接会把它变成字面量 "null"，
            // 于是生成 "null0006" 这种看起来正常、实际永远无法匹配的编码
            throw new IllegalStateException("取号返回值缺少 prefix、seq_length 或 current_value，规则编码为：" + ruleCode);
        }
        String sequence = currentValue.toString();
        if (sequence.length() > seqLength) {
            // 判定放在取号之后：SQL 只返回分配到的值，不为了预判而多读一列旧值。
            // 这里抛出的是运行时异常，本次 REQUIRES_NEW 事务随之回滚，因此递增不落库
            // （实测：1 位规则在 9 之后取号被拒，current_value 仍为 9）。
            // 于是规则卡在溢出前的值上，每次调用都以同一原因失败，既不会持续消耗号段，
            // 也不会在人工加宽位数之前悄悄生成第 5 位——那会让同一规则的编码长度开始漂移
            throw CodeRuleException.sequenceExhausted(ruleCode, currentValue, seqLength);
        }
        return prefix + PADDING.repeat(seqLength - sequence.length()) + sequence;
    }
}
