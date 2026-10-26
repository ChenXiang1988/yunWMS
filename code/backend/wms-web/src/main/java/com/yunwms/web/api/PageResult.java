package com.yunwms.web.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 分页响应的 {@code data}：{@code {pageNo, pageSize, total, records}}。
 *
 * <p>结构按 {@code docs/api-convention.md} 第四节定义。它属于入站协议，不属于任何业务模块：
 * 分页是 HTTP 契约的形态，与「分的是角色还是用户」无关。留在第一个用到它的模块里，
 * 第二个分页接口出现时只有两条路——依赖那个模块，或者复制一份；两条路都会让
 * 「全仓只有一种分页形态」这件事失去保障。
 *
 * <p>类型参数是<b>每行的响应体</b>，不是领域模型：把用例结果转成行对象是装配器的职责，
 * 本类型只管分页外壳。
 *
 * @param pageNo   页码，从 1 开始
 * @param pageSize 每页条数
 * @param total    满足筛选条件的总数，不是本页条数
 * @param records  本页数据；没有数据时为空数组，调用方 MUST NOT 传 {@code null}
 * @param <T>      单行响应体的类型
 */
public record PageResult<T>(
        @Schema(description = "页码，从 1 开始", example = "1")
        int pageNo,

        @Schema(description = "每页条数，取值 1 至 200，超出范围时接口返回 40000", example = "20")
        int pageSize,

        @Schema(description = "满足筛选条件的总条数，不是本页条数", example = "137")
        long total,

        @Schema(description = "本页数据；无数据时为空数组，不会是 null")
        List<T> records) {
}
