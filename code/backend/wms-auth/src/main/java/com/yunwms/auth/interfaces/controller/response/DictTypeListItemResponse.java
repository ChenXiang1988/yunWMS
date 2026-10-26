package com.yunwms.auth.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 字典类型列表的一行。
 *
 * <p>没有 {@code id}：业务代码与配置按 {@code dictType} 引用字典，把自增主键暴露出去只会诱使
 * 调用方按 id 传参，而按 id 引用的字典在重新种数据后含义会变。
 *
 * @param dictType  类型编码，如 {@code OWNER_TYPE}；取某类字典项的接口用它做路径参数
 * @param name      中文名
 * @param nameEn    英文名，可为 {@code null}
 * @param itemCount 该类下未删除的项数（含已停用的项）
 * @param status    类型状态编码：{@code ACTIVE} 启用，{@code DISABLED} 停用
 */
public record DictTypeListItemResponse(
        @Schema(description = "字典类型编码，如 OWNER_TYPE；取某类字典项时作为路径参数")
        String dictType,

        @Schema(description = "字典类型中文名，如「客户类型」")
        String name,

        @Schema(description = "字典类型英文名，可为 null")
        String nameEn,

        @Schema(description = "该类型下未删除的字典项数量，含已停用的项；取值为整数")
        long itemCount,

        @Schema(description = "类型状态编码：ACTIVE 启用，DISABLED 停用。停用的类型不应再被新的引用处使用")
        String status) {
}
