package com.yunwms.app;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块注册门禁：{@code com.yunwms} 下的顶层包必须是显式登记过的后端模块。
 *
 * <p>为什么需要这条测试：{@link com.yunwms.app.ModuleBoundaryTest} 的分层规则只对「已经写进层定义」的包生效。
 * 新增一个 {@code com.yunwms.inventory} 顶层包时，它的类不属于任何层，ArchUnit 对它零检查——
 * 测试仍然全绿，但新模块可以随意依赖任何地方。分层规则的空层问题（见 {@code ModuleBoundaryTest} 对
 * {@code withOptionalLayers} 的说明）在这里以另一种形式重现：<b>没被声明的模块等于没有约束</b>。
 * 把「模块清单」本身变成断言，新增模块就必须先回答「它属于哪一层、谁能访问它」。
 *
 * <p>为什么要求双向一致（既不许有未登记的包，也不许登记了却扫不到类）：只查「未登记」时，
 * 包名写错（{@code com.yunwms.msater}）或模块被删都不会有任何提示，注册表会慢慢变成一份过时的文档。
 * 反向断言让注册表与代码结构互为对方的校验。
 *
 * <p>为什么排除测试类：扫描根包是 {@code com.yunwms}，测试类的包名同样以 {@code com.yunwms.*} 开头。
 * 它们目前都落在已登记的模块下，不会产生误报，但「测试代码能否定义新顶层包」不是本条规则要回答的问题，
 * 排除之后规则只对生产代码的模块结构负责。
 *
 * <p>新增模块的正确做法（{@code docs/coding-project.md:146-154} 的门槛先过一遍）：
 * <ol>
 *   <li>在 {@link #REGISTERED_MODULES} 登记模块名；</li>
 *   <li>在 {@code ModuleBoundaryTest} 增加图层，并声明它允许访问谁、允许谁访问它；</li>
 *   <li>在 {@code wms-app} 的 {@code pom.xml} 加模块依赖。</li>
 * </ol>
 * 三步缺一不可：只做第一步会让模块进入 ArchUnit 的扫描范围却不受分层约束，问题只是从「静默无约束」
 * 变成「有一半约束」，比原来更难发现。
 */
@DisplayName("模块注册")
class ModuleRegistrationTest {

    /** 业务代码根包。与 {@link ArchitectureGuardTest}、{@link com.yunwms.app.ModuleBoundaryTest} 保持一致。 */
    private static final String BASE_PACKAGE = "com.yunwms";

    /**
     * 已登记的后端模块，与 {@code code/backend/pom.xml} 的 {@code <modules>} 及
     * {@code docs/coding-project.md:18-24} 的模块清单一致。
     */
    private static final List<String> REGISTERED_MODULES = List.of("web", "auth", "master", "app");

    @Test
    @DisplayName("com.yunwms 下的顶层包与已登记模块一一对应")
    void topLevelPackagesShouldMatchRegisteredModules() {
        Set<String> discovered = discoverTopLevelPackages();
        // 扫描为空说明根包名改了或 classpath 不对，此时下面的断言会空转通过——必须先把这种情况判死
        assertThat(discovered).as("没有扫描到任何 com.yunwms 顶层包，本测试会空跑").isNotEmpty();

        Set<String> unregistered = new TreeSet<>(discovered);
        unregistered.removeAll(REGISTERED_MODULES);
        assertThat(unregistered)
                .as("以下顶层包未登记为后端模块：新增模块必须先在本类登记，并在 ModuleBoundaryTest 增加图层与访问约束，"
                        + "否则它的类不属于任何层，模块边界规则对它零检查")
                .isEmpty();

        Set<String> missing = new TreeSet<>(REGISTERED_MODULES);
        missing.removeAll(discovered);
        assertThat(missing)
                .as("以下已登记模块没有扫描到任何生产类：包名改过、模块被删，或根包不在 classpath 上。"
                        + "注册表与 ModuleBoundaryTest 的层定义必须同步修改，否则对应层的约束会静默空转")
                .isEmpty();
    }

    /**
     * 扫描 {@code com.yunwms} 下出现过的全部顶层包名。
     *
     * <p>从类反推包名而不是读目录：目录可能与 classpath 不一致（{@code target/classes} 里残留已删除的旧类），
     * 而本测试要回答的是「编译产物里实际存在哪些模块」。
     *
     * @return 顶层包名集合，例如 {@code web}、{@code auth}
     */
    private static Set<String> discoverTopLevelPackages() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE)
                .stream()
                .map(JavaClass::getPackageName)
                .filter(packageName -> packageName.startsWith(BASE_PACKAGE + "."))
                .map(packageName -> packageName.substring(BASE_PACKAGE.length() + 1))
                .map(ModuleRegistrationTest::topLevelName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 取相对包名中的第一段。
     *
     * @param relativePackageName 去掉根包前缀后的包名，例如 {@code auth.domain.model}
     * @return 顶层包名，例如 {@code auth}
     */
    private static String topLevelName(String relativePackageName) {
        int separator = relativePackageName.indexOf('.');
        return (separator < 0 ? relativePackageName : relativePackageName.substring(0, separator)).toLowerCase(Locale.ROOT);
    }
}
