package com.yunwms.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * OpenAPI 文档的全局约定：系统定位与三类通用响应。
 *
 * <p>放在 {@code wms-app} 而不是各业务模块：这两件事都作用于「文档整体」，与某个业务域无关。
 * 放进 {@code wms-auth} 会让该模块承担一份它并不拥有的契约；放进 {@code wms-web} 又违反
 * 「{@code wms-web} 只放入站协议类型」的边界（见 {@code docs/coding-project.md}）。
 * {@code wms-app} 是唯一同时看得见全部模块的位置，也符合它「只做装配」的定位。
 *
 * <p>为什么用 {@link OpenApiCustomizer} 而不是在每个 {@code @Operation} 上挂 401/403/500：
 * {@code docs/api-convention.md} 第八节要求这三类响应由全局配置统一描述。逐接口重复声明时，
 * 任何一处措辞改动都要改 15 个地方，改漏的那一处不会有任何提示，直到有人照着过期的文档写代码；
 * 而且新增接口时忘写就少一段说明。改成「组件里定义一次、接口里引用一次」之后，
 * 接口侧只剩一个引用，说明文字只有一份。
 *
 * <p>错误响应体单独定义 {@code ApiErrorBody} 而不复用成功响应的 {@code ApiResult}：
 * springdoc 为每个泛型实例生成各自的 schema（如 {@code ApiResultLoginResponse}），并不存在一个
 * 通用的 {@code ApiResult}，按名字引用会引用到一个不存在的 schema。这里显式描述错误场景下的三个字段，
 * 也顺带把「data 固定为 null」这个调用方容易误判的点写进契约。
 */
@Configuration
public class OpenApiConfig {

    /** 未认证响应在 {@code components.responses} 里的名字，由各接口以 {@code $ref} 引用。 */
    private static final String UNAUTHORIZED_RESPONSE = "Unauthorized";

    /** 无权限响应在 {@code components.responses} 里的名字。 */
    private static final String FORBIDDEN_RESPONSE = "Forbidden";

    /** 服务内部错误响应在 {@code components.responses} 里的名字。 */
    private static final String INTERNAL_ERROR_RESPONSE = "InternalServerError";

    /** 通用错误响应体的 schema 名，与 springdoc 生成的 {@code ApiResult*} 分开，避免撞名。 */
    private static final String ERROR_BODY_SCHEMA = "ApiErrorBody";

    private static final String COMPONENT_REF_PREFIX = "#/components/";

    /**
     * 把系统定位与通用响应写进最终生成的文档。
     *
     * <p>Bean 名不能叫 {@code globalOpenApiCustomizer}：springdoc 自己的配置类里已经有一个同名 Bean，
     * 重名会让 Spring Boot 在启动期以 {@code BeanDefinitionOverrideException} 直接失败。
     *
     * @return 作用于文档整体的定制器，由 springdoc 在解析完全部接口注解之后调用
     */
    @Bean
    public OpenApiCustomizer yunwmsOpenApiCustomizer() {
        return openApi -> {
            describeSystem(openApi);
            Components components = componentsOf(openApi);
            components.addSchemas(ERROR_BODY_SCHEMA, errorBodySchema());
            components.addResponses(UNAUTHORIZED_RESPONSE,
                    errorResponse(401, "未登录或登录已失效，前端据此跳转登录页"));
            components.addResponses(FORBIDDEN_RESPONSE,
                    errorResponse(403, "已登录但没有该接口要求的权限点，前端据此提示无权访问"));
            components.addResponses(INTERNAL_ERROR_RESPONSE,
                    errorResponse(500, "服务内部错误，已由全局异常处理记录日志并转换为统一响应体"));
            attachSharedResponses(openApi);
        };
    }

    /**
     * 说明本系统分两端，以及统一响应外壳与三类通用响应的关系。
     *
     * <p>写在 {@code info} 里而不写在每个接口上：两端的分工与「HTTP 200 + 业务码」是全局口径，
     * 每个接口重复一遍只会互相漂移。接口侧只描述自己特有的语义。
     */
    private void describeSystem(OpenAPI openApi) {
        Info info = openApi.getInfo();
        if (info == null) {
            info = new Info();
            openApi.setInfo(info);
        }
        info.setTitle("yunWMS 后端接口");
        info.setDescription("""
                yunWMS 分两端，靠路径前缀区分鉴权主体：`/api/console` 供仓网管理端使用，
                `/api/portal` 供货主端使用；`/api/auth` 由两端共用，登录的主体类型由后端按账号判定，
                调用方不传也不允许传。

                除未认证（401）、无权限（403）与服务内部错误（500）外，所有接口一律返回 HTTP 200，
                业务结果由响应体 `ApiResult` 的 `code` 表达（0 表示成功，非 0 见错误码表）。
                这三类通用响应在 `components.responses` 中统一声明，各接口直接引用，不再重复描述。
                """);
    }

    /**
     * 给每个接口挂上三类通用响应的引用。
     *
     * <p>引用而不是内联：说明文字与响应体结构只在 {@code components} 里定义一次，
     * 接口侧只多三行 {@code $ref}，后续调整措辞不需要动任何 Controller。
     */
    private void attachSharedResponses(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        for (PathItem pathItem : openApi.getPaths().values()) {
            for (var operation : pathItem.readOperations()) {
                if (operation.getResponses() == null) {
                    operation.setResponses(new ApiResponses());
                }
                operation.getResponses().addApiResponse("401", ref(UNAUTHORIZED_RESPONSE));
                operation.getResponses().addApiResponse("403", ref(FORBIDDEN_RESPONSE));
                operation.getResponses().addApiResponse("500", ref(INTERNAL_ERROR_RESPONSE));
            }
        }
    }

    /**
     * 构造一个通用错误响应：中文说明 + 统一响应体。
     *
     * @param status      HTTP 状态码，写进说明与响应定义，保证文档与实际行为一致
     * @param description 面向调用方的中文说明，讲清「谁在什么情况下会拿到它、前端该怎么处理」
     * @return 待注册到 {@code components.responses} 的响应定义
     */
    private ApiResponse errorResponse(int status, String description) {
        Schema<?> body = new Schema<>().$ref(COMPONENT_REF_PREFIX + "schemas/" + ERROR_BODY_SCHEMA);
        return new ApiResponse()
                .description(status + "：" + description)
                .content(new Content().addMediaType(APPLICATION_JSON_VALUE, new MediaType().schema(body)));
    }

    /**
     * 错误响应体的结构：与成功响应同一外壳，`data` 恒为 null。
     *
     * <p>不复用成功响应的 schema 名，理由见类注释；字段说明只写错误场景特有的语义，
     * 不重复 {@code ApiResult} 已经在代码里表达的通用含义。
     */
    private Schema<?> errorBodySchema() {
        return new ObjectSchema()
                .description("业务错误响应体，与成功响应同一外壳 ApiResult")
                .addProperty("code", new IntegerSchema()
                        .description("业务错误码，五位整数，前三位对齐 HTTP 语义（401xx 未认证、403xx 无权限、500xx 内部错误）"))
                .addProperty("message", new StringSchema()
                        .description("面向调用方的中文提示文本，可直接展示"))
                .addProperty("data", new ObjectSchema()
                        .description("错误响应中恒为 null，调用方不应读取"));
    }

    private ApiResponse ref(String name) {
        return new ApiResponse().$ref(COMPONENT_REF_PREFIX + "responses/" + name);
    }

    private Components componentsOf(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        return openApi.getComponents();
    }
}
