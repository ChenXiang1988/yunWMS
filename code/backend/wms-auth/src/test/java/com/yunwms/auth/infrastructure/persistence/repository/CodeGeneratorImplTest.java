package com.yunwms.auth.infrastructure.persistence.repository;

import com.yunwms.auth.domain.exception.CodeRuleException;
import com.yunwms.auth.infrastructure.persistence.entity.CodeRuleSequenceRow;
import com.yunwms.auth.infrastructure.persistence.mapper.SysCodeRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * 取号引擎的单测：只钉拼装与溢出判定，不起 Spring、不连库。
 *
 * <p>Mapper 用 mock 替换，因此这里断言的是「拿到一条 RETURNING 结果之后怎么处理」，
 * 而不是 SQL 本身对不对。并发下是否重号由 {@code UPDATE ... RETURNING} 的单语句原子性保证，
 * 不能靠这个测试证明——它已被实测覆盖，见交付说明。
 *
 * <p>本类直接 {@code new} 出被测对象，不走 Spring 代理，因此
 * {@code REQUIRES_NEW} 在这里不生效也测不到。这里不假装验证事务语义：
 * 「号段即使业务回滚也不退回」只在真实事务管理器下才有意义。
 *
 * <p>为什么不用 {@code @ExtendWith(MockitoExtension.class)}：与既有单测保持一致，
 * 且本类需要的替换点只有一个，手工 mock 比注解加参数注入更直白。
 */
@DisplayName("取号引擎")
class CodeGeneratorImplTest {

    private static final String RULE = "OWNER";
    private static final String PREFIX = "OWNER";

    private SysCodeRuleMapper sysCodeRuleMapper;
    private CodeGeneratorImpl codeGenerator;

    @BeforeEach
    void setUp() {
        sysCodeRuleMapper = mock(SysCodeRuleMapper.class);
        codeGenerator = new CodeGeneratorImpl(sysCodeRuleMapper);
    }

    @Test
    @DisplayName("正常取号：current_value 递增到 5 之后返回 OWNER0006")
    void shouldReturnNextCode() {
        // 递增后的值就是本次分配到的号：库里 current_value 原为 5，
        // 语句把它改成 6 并返回 6，因此编码是 0006 而不是 0005
        given(sysCodeRuleMapper.advance(RULE)).willReturn(row(PREFIX, 4, 6L));

        assertThat(codeGenerator.next(RULE)).isEqualTo("OWNER0006");
    }

    @Test
    @DisplayName("补零：seq_length=4、分配到的流水号 8 生成 OWNER0008，不是 OWNER8")
    void shouldPadSequenceToConfiguredLength() {
        given(sysCodeRuleMapper.advance(RULE)).willReturn(row(PREFIX, 4, 8L));

        assertThat(codeGenerator.next(RULE)).isEqualTo("OWNER0008");
    }

    @Test
    @DisplayName("刚好用满：4 位到 9999 仍可生成，不是一到上界就拒绝")
    void shouldAllowLastValueOfTheRange() {
        // 溢出判定必须是「超过位数」而不是「达到上界」：
        // 差一位就拒绝会让每个规则的最后一格永远用不上，而它是合法编码
        given(sysCodeRuleMapper.advance(RULE)).willReturn(row(PREFIX, 4, 9999L));

        assertThat(codeGenerator.next(RULE)).isEqualTo("OWNER9999");
    }

    @Test
    @DisplayName("位数用尽：分配到的流水号 10000 超出 4 位时抛异常，不返回 OWNER10000")
    void shouldRejectWhenSequenceExhausted() {
        // 库里 current_value = 9999 时再取一次号，语句返回 10000；
        // 此时必须报错而不是进到第 5 位——继续生成会让「4 位流水」的既有编码
        // 与新编码长度不一致，而长度稳定是可被标签与扫码依赖的前提
        given(sysCodeRuleMapper.advance(RULE)).willReturn(row(PREFIX, 4, 10000L));

        Throwable thrown = catchThrowable(() -> codeGenerator.next(RULE));

        assertThat(thrown).isInstanceOf(CodeRuleException.class);
        assertThat(thrown).hasMessageContaining("规则 OWNER 的流水号已用尽");
        assertThat(((CodeRuleException) thrown).reason())
                .isEqualTo(CodeRuleException.Reason.SEQUENCE_EXHAUSTED);
        assertThat(((CodeRuleException) thrown).ruleCode()).isEqualTo(RULE);
    }

    @Test
    @DisplayName("规则不存在：抛业务异常并提示未配置，不返回任何编码")
    void shouldRejectMissingRule() {
        // 语句命中零行时 MyBatis 返回 null（返回单行的方法不会给出空集合）
        given(sysCodeRuleMapper.advance(RULE)).willReturn(null);

        Throwable thrown = catchThrowable(() -> codeGenerator.next(RULE));

        assertThat(thrown).isInstanceOf(CodeRuleException.class);
        assertThat(thrown).hasMessage("编码规则 OWNER 未配置");
        assertThat(((CodeRuleException) thrown).reason())
                .isEqualTo(CodeRuleException.Reason.NOT_CONFIGURED);
    }

    @Test
    @DisplayName("规则编码为空：直接拒绝且不打数据库，避免被当成「规则未配置」")
    void shouldRejectBlankRuleCode() {
        // 空编码不是「库里没这条规则」，而是调用方没传参；
        // 若放行到 SQL，WHERE rule_code = null 恒不命中，会以「规则未配置」的面目出现，
        // 把一次调用方 bug 伪装成配置缺失
        assertThatThrownBy(() -> codeGenerator.next("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("编码规则编码不能为空");
        verifyNoInteractions(sysCodeRuleMapper);
    }

    @Test
    @DisplayName("取号只发一条语句：不同步读当前值再写回")
    void shouldIssueExactlyOneStatement() {
        given(sysCodeRuleMapper.advance(RULE)).willReturn(row(PREFIX, 4, 6L));

        codeGenerator.next(RULE);

        // 「读当前值 → 计算 → 写回」会在这里表现为多次调用；
        // 断言只调了 advance 一次，是为了把「不许拆成两步」这条约束钉在测试里，
        // 而不是只写在注释里——注释不会因为将来某次改动而变红
        verify(sysCodeRuleMapper).advance(RULE);
        verifyNoMoreInteractions(sysCodeRuleMapper);
    }

    /**
     * 构造一条 RETURNING 结果。
     *
     * @param prefix       规则前缀
     * @param seqLength    流水位数
     * @param currentValue 本次分配到的流水号
     * @return 投影对象
     */
    private static CodeRuleSequenceRow row(String prefix, int seqLength, long currentValue) {
        CodeRuleSequenceRow row = new CodeRuleSequenceRow();
        row.setPrefix(prefix);
        row.setSeqLength(seqLength);
        row.setCurrentValue(currentValue);
        return row;
    }
}
