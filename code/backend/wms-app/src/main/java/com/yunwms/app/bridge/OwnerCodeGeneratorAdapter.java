package com.yunwms.app.bridge;

import com.yunwms.auth.domain.port.CodeGenerator;
import com.yunwms.master.domain.port.OwnerCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * 把 {@code wms-master} 的货主取号端口接到 {@code wms-auth} 的取号引擎上。
 *
 * <p><b>为什么这个类在 {@code wms-app}：</b>取号能力当前落在 {@code wms-auth}，而
 * {@code auth} 与 {@code master} 互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截）。两个模块之间只剩「由上层装配」这条路，而只有 {@code wms-app}
 * 的运行期 classpath 同时包含两边——把适配器放进任一业务模块，另一个模块的类就不在编译期可见。
 * 本类因此只有装配，不含业务判断：规则编码的取值是唯一一处「知识」，且它来自迁移里的种子数据。
 *
 * <p>委托而非复制：取号的事务语义（号段递进独立提交、回滚不退号）由被委托方实现，
 * 本类不做任何缓存或重试——在这里补一层「取号失败重试」会让号段消耗与失败原因脱钩。
 */
@Component
public class OwnerCodeGeneratorAdapter implements OwnerCodeGenerator {

    /**
     * 货主编码的规则编码，对应 {@code sys_code_rule.rule_code}，种子见
     * {@code V030__seed_code_rules.sql}（prefix {@code OWNER}、流水 4 位，形如 {@code OWNER0001}）。
     */
    private static final String OWNER_RULE_CODE = "OWNER";

    private final CodeGenerator codeGenerator;

    public OwnerCodeGeneratorAdapter(CodeGenerator codeGenerator) {
        this.codeGenerator = codeGenerator;
    }

    /**
     * {@inheritDoc}
     *
     * <p>取号失败（规则未配置、流水位数用尽）由被委托方抛出它自己的异常类型，本类原样上抛、
     * 不做翻译：失败原因属于提供方，把它包装成 {@code wms-master} 需要认识的新类型，
     * 等于让两个模块再共享一个契约。
     */
    @Override
    public String next() {
        return codeGenerator.next(OWNER_RULE_CODE);
    }
}
