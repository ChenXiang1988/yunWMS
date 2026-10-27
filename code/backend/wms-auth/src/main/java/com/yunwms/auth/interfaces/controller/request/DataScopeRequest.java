package com.yunwms.auth.interfaces.controller.request;

import com.yunwms.auth.domain.model.DataScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 配置数据范围的请求体，角色与用户两个端点共用。
 *
 * <p>共用一个类型是因为字段完全相同：都是「类型 + 指定货主明细」，都是整批替换语义。区别只在
 * {@code dataScopeType} 是否可为空——角色总有范围，用户可为「清除覆盖」。这条差异属于用例，
 * 不属于协议形状：拆成两个字段一样的 record 之后，两个端点对同一件事的字段名迟早会不一致。
 *
 * <p>{@code ownerIds} 按 {@code docs/api-convention.md} 第五节以字符串传输：超过 2^53 的
 * {@code Long} 在 JS 里会丢精度。解析在 Controller 里借 {@link RequestIds} 完成，
 * 非法值一律 40000 并带出字段名。
 *
 * @param dataScopeType 数据范围类型：ALL / ASSIGNED / CUSTOM；用户端点上传 {@code null} 表示清除覆盖
 * @param ownerIds      指定货主标识，以字符串传输；仅 {@code CUSTOM} 时可为非空
 */
public record DataScopeRequest(
        @Schema(description = "数据范围类型：ALL 全部货主，ASSIGNED 我负责的货主，CUSTOM 指定货主。"
                + "角色端点必填；用户端点传 null 表示清除用户级覆盖、回落到角色默认。取值非法返回 40000")
        DataScopeType dataScopeType,

        @Schema(description = "指定货主标识，以字符串传输：数组元素是字符串形式的十进制整数。"
                + "仅 dataScopeType 为 CUSTOM 时可非空，其他类型传了返回 40000；"
                + "货主必须存在且启用，否则返回 40000")
        List<@NotNull(message = "货主标识不能为空") String> ownerIds) {
}
