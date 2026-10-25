package com.yunwms.app;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * 模块边界兜底：依赖方向必须与 {@code openspec/config.yaml:18} 规定的 {@code app → auth/master → web} 一致。
 *
 * <p>约束来自两处：{@code openspec/config.yaml:18} 写明「模块划分：wms-web（入站协议类型）、wms-auth（认证与权限）、
 * wms-master（主数据）、wms-app（启动装配）。依赖单向：app → auth/master → web」；{@code AGENTS.md:45} 写明
 * 「禁止跨模块直接依赖（{@code wms-*} 模块间只允许通过 api 包或事件交互，ArchUnit 拦截）」。两处都写了约定，
 * 但后一处要求的拦截器此前并不存在，于是约定只能靠 review 时肉眼比对 import。
 * 跨模块直接依赖的失效模式与漏写鉴权注解同源——编译通过、单测通过、运行正常，代价要等到某个模块想单独
 * 演进或替换时才会以「改一处、连锁编译失败」的形式暴露，那时已经很难拆。把方向变成构建期断言后，
 * 破坏方向的提交在本地就红，而不是在若干个月后的重构里付账。
 *
 * <p>为什么用一条 {@code layeredArchitecture()} 而不是若干条 {@code noClasses()}：分层声明是「层 + 谁可以访问谁」
 * 的单一真源。用 {@code noClasses()} 表达时，每加一个模块就要人记得同时写正向和反向两条规则；漏写反向规则
 * 不会有任何提示，约束会静默地只剩一半（本轮改造前就处在这个状态：{@code auth → master} 被拦，
 * {@code master → auth} 没拦）。分层声明把「这一层允许谁访问」写成一条约束，双向方向由 ArchUnit 统一求值，
 * 新模块落地时约束自动生效，不依赖任何人的记忆。
 *
 * <p>为什么 {@code withOptionalLayers(true)}：{@code wms-master} 目前没有任何源码，它的层是空集。该开关把
 * 「层可以暂时为空」显式声明掉，规则不会因为它还没有类而失败（不开启时空层会生成一条
 * {@code Layer 'master' is empty} 的违反，那条记录与被检查的依赖方向无关，只会稀释真正的信号）。
 * 它放宽的仅仅是「层可以还没有类」，不是依赖方向：某个层一旦出现第一个类，该层的访问约束立即参与求值，
 * 不需要有人记得回来补规则。
 *
 * <p>为什么不套 {@code FreezingArchRule}（冻结基线）：2026-10-25 对现有代码实测，这条分层规则的存量违反为
 * <b>0 条</b>，没有债务可冻，空基线下的冻结机制不提供任何拦截力，反而带来两个真实的失效模式：
 * 其一，{@code allowStoreUpdate=true}（本地默认）时新增违反**不会**让测试失败，而是被静默写进基线并判为通过，
 * 控制台不列出违规条目——本地绿灯因此不构成「没引入新违反」的证据；其二，{@code allowStoreUpdate=false} 时
 * 能拦住，但报错只有 {@code StoreUpdateFailedException: Updating frozen violations is disabled}，
 * 看不到是哪条依赖越界，还得绕道去看基线 diff。纯 {@code layeredArchitecture()} 的失败信息是逐条
 * {@code 类.方法 → 类.方法 (文件:行)}，定位成本最低。若将来规则收紧后真的出现存量债务，再引入冻结，
 * 并**同时**把 CI 配成 {@code -Darchunit.freeze.store.default.allowStoreUpdate=false}，否则拦截力为零。
 * 基线是债务清单，不是豁免；没有债务时就不要这个清单。
 *
 * <p>由此产生一条硬约束：<b>层定义里的包名（{@code com.yunwms.web} / {@code auth} / {@code master} / {@code app}）
 * 属于架构常量，不得改动。</b>分层规则靠包名匹配层成员，包名一旦改名而这些常量没跟着改，层会静默变成空层，
 * 规则对所有类空转——测试仍然是绿的，但什么都没检查。同理，新增业务模块（inbound、inventory、outbound、task 等）
 * 落地时必须同时在这里加图层并声明它允许访问谁、允许谁访问它，否则该模块的类不属于任何层，不在检查范围内。
 *
 * <p>为什么必须 {@link ImportOption.DoNotIncludeTests}：扫描根包是 {@code com.yunwms}，会把测试类一起导入，
 * 而测试类天然要引用被它测试的模块（例如 wms-app 的集成测试引用 auth 的 service）。不排除测试类的话，
 * 规则会报出大量与生产依赖方向无关的违反，真正需要盯的会淹没在噪音里。
 *
 * <p>为什么放在 wms-app 而不是各模块内部：只有 wms-app 的运行期 classpath 同时包含 wms-web / wms-auth /
 * wms-master 的类。规则放在其他模块里，被依赖方根本不在 classpath 上，规则会「一条违反都查不到」而假通过。
 */
@AnalyzeClasses(packages = "com.yunwms", importOptions = ImportOption.DoNotIncludeTests.class)
@DisplayName("模块边界")
class ModuleBoundaryTest {

    /**
     * 分层依赖方向：{@code app → auth/master → web}，且 auth 与 master 平级互不依赖。
     *
     * <p>各条约束的失效后果：
     * <ul>
     *   <li>{@code web} 反向依赖上游会让所有引用 web 的模块被间接拖进上游的传递依赖，依赖图成环，
     *       公共模块不再能独立演进——这正是把统一响应体与异常处理抽到 web 的目的被抵消的方式。</li>
     *   <li>{@code auth} 与 {@code master} 互访会让两个业务域只能同生共死，单独替换或单独演进都做不到；
     *       需要跨域协作时应通过事件或上层（app）编排，而不是 import 对方的类。</li>
     *   <li>业务模块反向依赖 {@code app} 会让「装配」变成循环，业务模块一引用 app 就拿得到启动类与全局配置，
     *       分层名存实亡。</li>
     * </ul>
     *
     * <p>层顺序按任务约束自下而上书写（web → auth → master → app），约束方向由
     * {@code mayOnlyBeAccessedByLayers} / {@code mayNotBeAccessedByAnyLayer} 表达，与书写顺序无关；
     * 顺序保持自下而上是为了让「谁能访问谁」在读代码时一眼可辨。
     */
    @ArchTest
    static final ArchRule MODULE_DEPENDENCY_DIRECTION =
            layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("web").definedBy("com.yunwms.web..")
                    .layer("auth").definedBy("com.yunwms.auth..")
                    .layer("master").definedBy("com.yunwms.master..")
                    .layer("app").definedBy("com.yunwms.app..")
                    .whereLayer("web").mayOnlyBeAccessedByLayers("auth", "master", "app")
                    .whereLayer("auth").mayOnlyBeAccessedByLayers("app")
                    .whereLayer("master").mayOnlyBeAccessedByLayers("app")
                    .whereLayer("app").mayNotBeAccessedByAnyLayer()
                    .withOptionalLayers(true)
                    .because("依赖方向见 openspec/config.yaml:18");
}
