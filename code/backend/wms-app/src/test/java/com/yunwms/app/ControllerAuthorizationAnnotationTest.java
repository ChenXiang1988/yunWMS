package com.yunwms.app;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 兜底测试：每个 REST 接口方法都必须显式声明鉴权方式。
 *
 * <p>为什么需要这条测试：注解式鉴权的失效模式是「漏写 = 放行」，而且完全静默——
 * 新写一个接口忘了加注解，它立刻对任何已登录用户可用，编译器、单测、日志都不会有任何反应。
 * 把「必须带注解」变成一条构建期断言，漏写就从「线上越权」变成「本地红」。
 *
 * <p>只检查方法级注解，不接受类级注解：类级注解会让「这个方法到底要不要鉴权」变成一个
 * 要往上翻两屏才能回答的问题，而本测试要的是打开方法就能看见答案。
 *
 * <p>不启动 Spring 上下文：这里查的是源码里的注解，不是运行期的 Bean。用类路径扫描而不是
 * 手写接口清单，是为了让将来新增的 Controller 自动进入检查范围——靠人维护的清单会漏。
 */
@DisplayName("接口鉴权注解兜底")
class ControllerAuthorizationAnnotationTest {

    /** 业务代码根包。扫描范围必须覆盖全部模块，否则新模块里的 Controller 不会被检查。 */
    private static final String BASE_PACKAGE = "com.yunwms";

    /** 三种被认可的鉴权声明。 */
    private static final List<Class<? extends Annotation>> AUTHORIZATION_ANNOTATIONS = List.of(
            SaCheckPermission.class,
            SaCheckLogin.class,
            SaIgnore.class);

    @Test
    @DisplayName("所有 REST 接口方法都带 @SaCheckPermission / @SaCheckLogin / @SaIgnore")
    void everyEndpointShouldDeclareAuthorization() throws ClassNotFoundException {
        List<Class<?>> controllers = restControllers();
        // 扫描为空说明包名改了或 classpath 不对，此时下面的断言会空转通过——必须先把这种情况判死
        assertThat(controllers).as("没有扫描到任何 Controller，本测试会空跑").isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!needsAuthorization(method)) {
                    continue;
                }
                if (!declaresAuthorization(method)) {
                    violations.add(controller.getName() + "#" + method.getName());
                }
            }
        }

        assertThat(violations)
                .as("以下接口方法缺少鉴权注解（%s），注解式鉴权下漏写等于对任何登录用户放行",
                        AUTHORIZATION_ANNOTATIONS.stream().map(Class::getSimpleName).toList())
                .isEmpty();
    }

    /**
     * 找出全部 Controller 类型。
     *
     * <p>按 {@code @Controller} 过滤而不是 {@code @RestController}：后者带 {@code @Controller} 元注解，
     * 用父注解才能同时覆盖两种写法。
     *
     * @return Controller 类型列表
     * @throws ClassNotFoundException 扫描到的类无法加载，说明编译产物与扫描路径不一致
     */
    private static List<Class<?>> restControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            controllers.add(Class.forName(candidate.getBeanClassName()));
        }
        return controllers;
    }

    /**
     * 判断方法是否属于需要鉴权的接口方法。
     *
     * <p>排除编译器生成的桥接方法与静态方法：它们不是请求入口。
     */
    private static boolean needsAuthorization(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && !Modifier.isStatic(method.getModifiers())
                && !method.isSynthetic()
                && !method.isBridge();
    }

    private static boolean declaresAuthorization(Method method) {
        for (Class<? extends Annotation> annotation : AUTHORIZATION_ANNOTATIONS) {
            if (method.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        return false;
    }
}
