package com.yunwms.app;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构门禁：把 {@code docs/coding-project.md} 里写在纸上的层内约束变成构建期断言。
 *
 * <p>为什么需要这个类：{@link ModuleBoundaryTest} 只校验四个 Maven 模块之间的类引用方向，而
 * {@code docs/coding-project.md:62-68} 与 {@code :106}、{@code :110}、{@code :114} 另外写了五条约束
 * （domain 不使用框架、domain/infrastructure 不认识 Web、application 不碰 request/response、
 * {@code wms-web} 只有五个白名单类型、{@code *Entity} 只住在 {@code infrastructure/persistence/entity}），
 * 并声称「这三条 MUST 由 ArchUnit 测试拦截」。实测该断言此前并不存在：约定只能靠 review 时肉眼比对 import。
 *
 * <p>这五条的失效模式同源：违反之后代码照样编译、单测照样绿、功能照样跑，代价推迟到很久以后才以
 * 「领域模型认得出 HTTP」「持久化实现依赖响应体」这类无法回溯的形态暴露。例如 domain 里出现一个
 * {@code @JsonProperty}，领域模型的字段名就与前端契约绑死，此后改字段名等于改 API。
 * 把约束变成断言之后，这类改动在本地就红。
 *
 * <p>为什么全部写成「谁不能依赖谁」的否定式：肯定式（{@code onlyDependOnClassesThat()}）
 * 需要穷举允许的包，每加一个合法依赖（比如 {@code java.time}）都要回来改规则，而漏改的后果是合法代码变红；
 * 否定式只需列出禁止项，新增合法依赖不会被拦，漏列的禁止项最多让某条约束暂时变弱——两种失误的代价不对称。
 *
 * <p>为什么必须 {@link ImportOption.DoNotIncludeTests}：扫描根包是 {@code com.yunwms}，测试类也要引用被它测试的模块，
 * 不排除的话规则会报出大量与生产依赖无关的违反。这里排除的是「测试代码」而不是「本条规则不适用」——
 * 生产代码一条都不放过。
 *
 * <p>为什么不套 {@code FreezingArchRule}：2026-10-27 对现有代码实测，本类五条规则的存量违反为 <b>0 条</b>
 * （审计见 {@code openspec/changes/archive/2026-10-27-audit-architecture-boundaries/audit.md} 第 2 节 C12/C13/C16/C17/C20），
 * 没有债务可冻，空基线下的冻结机制不提供拦截力，反而会静默把新增违反写进基线（理由详见
 * {@link ModuleBoundaryTest} 的类注释）。将来若真有存量债务，再引入冻结，并同时把 CI 配成
 * {@code -Darchunit.freeze.store.default.allowStoreUpdate=false}。
 *
 * <p>为什么放在 {@code wms-app}：只有它的运行期 classpath 同时包含 {@code wms-web} / {@code wms-auth} /
 * {@code wms-master} 的类。规则放在其他模块里，被检查的类根本不在 classpath 上，规则会「零违反」假通过。
 *
 * <p>包名模式一律用 {@code ..domain..} / {@code ..application..} 这类「任意模块下的这一层」写法，
 * 而不是写死 {@code com.yunwms.auth.domain..}：将来落地 inbound / inventory / outbound 等模块时，
 * 新模块自动进入检查范围，不需要有人记得回来补规则。只有 {@code com.yunwms.web} 是单模块协议层，才写死全名。
 */
@AnalyzeClasses(packages = "com.yunwms", importOptions = ImportOption.DoNotIncludeTests.class)
@DisplayName("架构门禁")
class ArchitectureGuardTest {

    /**
     * {@code wms-web} 的全部合法类型，与 {@code docs/coding-project.md:46-56} 的白名单表逐条对应。
     *
     * <p>新增任何一项都是架构决策，必须走 design 评审：这份清单的意义是让「wms-web 又多了一个类」
     * 这件事必须被显式讨论，而不是某天有人顺手把工具类放进去。
     */
    private static final List<String> WEB_ALLOWED_SIMPLE_NAMES = List.of(
            "ApiResult",
            "ErrorCode",
            "PageResult",
            "BizException",
            "GlobalExceptionHandler");

    /**
     * domain 不依赖任何框架与协议栈。
     *
     * <p>依据 {@code docs/coding-project.md:106}「domain MUST NOT 依赖 Spring、MyBatis、Jackson 或任何框架注解」，
     * 以及 {@code :62} 对「技术共享模块不得被 domain 反向依赖」的要求。
     *
     * <p>逐条：
     * <ul>
     *   <li>{@code org.springframework..}：注解（{@code @Service}、{@code @Transactional}）会让领域类只能在
     *       Spring 容器里实例化，纯内存单测与将来的批处理入口都得先起上下文。</li>
     *   <li>{@code com.baomidou..} / {@code org.apache.ibatis..}：领域模型一旦带上 {@code @TableName} 或
     *       {@code BaseMapper}，表结构与领域模型就成了一对一，{@code docs/coding-project.md:110} 要求的
     *       「实体与领域模型分离」随之失效。</li>
     *   <li>{@code com.fasterxml..}：Jackson 注解把领域字段名直接变成 HTTP 契约，改字段名即改 API。</li>
     *   <li>{@code jakarta..} / {@code javax..}：校验注解与 Servlet 类型同属框架能力，理由与上面三条一致。</li>
     * </ul>
     */
    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "com.baomidou..",
                            "com.fasterxml..",
                            "jakarta..",
                            "javax..",
                            "org.apache.ibatis..")
                    .because("docs/coding-project.md:106 —— domain 不得依赖 Spring、MyBatis、Jackson 或任何框架注解");

    /**
     * domain 与 infrastructure 不认识 {@code wms-web}。
     *
     * <p>依据 {@code docs/coding-project.md:63-64}：{@code wms-web} 属于入站协议层，领域模型不认识 HTTP，
     * 持久化实现不认识响应结构。
     *
     * <p>为什么不能用「application 已经这么干了所以 domain 也可以」来解释：{@code :65} 明确记着
     * application 引用 {@code wms-web} 是<b>已知技术债</b>，并要求「新代码 MUST NOT 扩大这处耦合」。
     * 债务的定义是「要还的」，不是「可以往外扩散的」。domain 与 infrastructure 目前是 0 import
     * （审计 C13），保持零成本，没有任何理由让它变成第二个、第三个债务点。
     *
     * <p>为什么 infrastructure 也不行：{@code infrastructure/persistence/repository} 实现的是 domain 定义的 port，
     * 它一旦 import {@code ApiResult}，就说明它开始参与协议决定，Repository 的返回值将被 HTTP 结构污染，
     * 领域侧再想拿走这个 port 就得连协议一起搬。
     */
    @ArchTest
    static final ArchRule DOMAIN_AND_INFRASTRUCTURE_KNOW_NOTHING_ABOUT_WEB =
            noClasses()
                    .that().resideInAnyPackage("..domain..", "..infrastructure..")
                    .should().dependOnClassesThat().resideInAPackage("com.yunwms.web..")
                    .because("docs/coding-project.md:63-64 —— domain/infrastructure 不得 import com.yunwms.web.*");

    /**
     * application 不依赖入站协议的请求/响应对象。
     *
     * <p>依据 {@code docs/coding-project.md:114}「请求/响应 MUST NOT 被 application 或 domain import」。
     *
     * <p>失效模式：application 一旦接收 {@code *Request}，用例的入参口径就由 HTTP 决定——字段上的
     * {@code @NotNull} 变成业务前置条件、新增一个查询参数就要改用例签名、PDA 与开放接口想复用同一用例
     * 得先造一个假的 Request。正确形态是 application 自己的 {@code command}/{@code query} 对象，
     * 由 interfaces 层的 assembler 转换（{@code docs/coding-project.md:81}、{@code :115}）。
     *
     * <p>包名模式只覆盖 {@code interfaces.controller.request|response}，与当前目录结构一致
     * （{@code wms-auth}/{@code wms-master} 都是 {@code interfaces/controller/request|response}）。
     * 若将来 request/response 迁到 {@code controller} 之外，本模式必须同步放宽，否则规则会静默空转。
     */
    @ArchTest
    static final ArchRule APPLICATION_IS_INDEPENDENT_OF_REQUEST_AND_RESPONSE =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..interfaces.controller.request..",
                            "..interfaces.controller.response..")
                    .because("docs/coding-project.md:114 —— request/response 不得被 application 或 domain import");

    /**
     * {@code wms-web} 里只允许五个白名单类型。
     *
     * <p>依据 {@code docs/coding-project.md:42-56}：这个模块替代原 {@code wms-common}，改名是为了收窄职责。
     * {@code common} 变成垃圾场的机制不是谁一次性塞进去一堆类，而是「这个工具方法放哪都不合适，
     * 先放 common 吧」被重复了几十次。白名单把每一次「先放这儿」都变成一次失败构建，逼着选真正的归属。
     *
     * <p>用简单类名比对而不是全限定名：白名单管的是「这个类型是什么」，不是「它放在哪个子包」。
     * 现在 {@code api} 与 {@code exception} 两个子包分别装着响应结构与异常，若哪天重新分包，
     * 规则不应因此变红。
     *
     * <p>为什么自己写 {@link ArchCondition} 而不是 {@code haveNameMatching}：匹配类的规则走正则，
     * 要么锚不住结尾（{@code ApiResultFoo} 会被当成 {@code ApiResult} 放过），要么报错信息只说
     * 「名字不匹配某个正则」，看不出允许清单是什么。自定义条件的失败信息直接列出白名单全文。
     */
    @ArchTest
    static final ArchRule WEB_CONTAINS_ONLY_WHITELISTED_TYPES =
            classes()
                    .that().resideInAPackage("com.yunwms.web..")
                    .should(haveSimpleNameIn(WEB_ALLOWED_SIMPLE_NAMES))
                    .because("docs/coding-project.md:46-56 —— wms-web 只放统一响应、错误码、业务异常、全局异常处理与分页结构");

    /**
     * 所有 {@code *Entity} 只住在 {@code infrastructure/persistence/entity}。
     *
     * <p>依据 {@code docs/coding-project.md:110}「实体（*Entity）MUST 与表一一对应，只出现在
     * infrastructure/persistence/entity」，以及 {@code docs/coding-java.md:44}「表映射类 MUST 放在模块的
     * infrastructure/persistence/entity，查询投影 MUST NOT 声明 @TableName」。
     *
     * <p>失效模式：{@code *Entity} 出现在 {@code domain} 或 {@code application} 时，表结构立刻成为
     * 领域契约——加一列就要改领域模型，删除一列会打断用例；而 {@code docs/coding-project.md:110}
     * 要求的「创建与重建走不同入口、重建不绕过校验」也就失效，因为 Entity 自带无参构造与 setter，
     * 谁都可以绕过紧凑构造器造出一个非法状态的对象。
     *
     * <p>只约束后缀名，不约束包内其他类型：{@code infrastructure/persistence/entity} 下还有
     * {@code *Row} 这类查询投影（不声明 {@code @TableName}、不参与写入），它们本来就该待在这里。
     */
    @ArchTest
    static final ArchRule ENTITIES_LIVE_IN_PERSISTENCE_ENTITY =
            classes()
                    .that().haveSimpleNameEndingWith("Entity")
                    .should().resideInAPackage("..infrastructure.persistence.entity")
                    .because("docs/coding-project.md:110 —— *Entity 与表一一对应，只出现在 infrastructure/persistence/entity");

    /**
     * 构造「简单类名必须属于给定白名单」的断言。
     *
     * <p>只比对简单类名，不比对包名：见 {@link #WEB_CONTAINS_ONLY_WHITELISTED_TYPES} 的说明。
     *
     * @param allowedSimpleNames 允许出现的简单类名
     * @return 条件对象，违反时逐类报出全限定名与允许清单
     */
    private static ArchCondition<JavaClass> haveSimpleNameIn(List<String> allowedSimpleNames) {
        return new ArchCondition<>("简单类名属于白名单 " + allowedSimpleNames) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean allowed = allowedSimpleNames.contains(javaClass.getSimpleName());
                events.add(new SimpleConditionEvent(javaClass, allowed,
                        javaClass.getName() + " 出现在 wms-web，但不在白名单 " + allowedSimpleNames
                                + " 内；新增类型属于架构决策，必须先走 design 评审"));
            }
        };
    }
}
