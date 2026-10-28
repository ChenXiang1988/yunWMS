package com.yunwms.auth.interfaces.controller.request;

import com.yunwms.auth.domain.model.DictStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 启用或停用字典项的请求体：{@code {"status": "DISABLED"}}。
 *
 * <p>{@code status} 声明为必传：省略字段与传错值都必须被打回，而不是「不传就不改」——
 * 后者会让一次「以为停用了」的操作变成空操作，而管理员已经把这个项当成已停用、还等着它从下拉里消失。
 *
 * <p>状态值取自 {@link DictStatus} 的枚举名，未知编码在反序列化阶段就失败，不会落进业务层。
 * 用枚举名而不是序号：序号会随枚举成员的增删改序而漂移（{@code docs/api-convention.md} 第五节）。
 *
 * @param status 目标状态：{@code ACTIVE} 启用，{@code DISABLED} 停用
 */
public record DictItemStatusRequest(
        @NotNull(message = "目标状态不能为空")
        @Schema(description = "目标状态，必传：ACTIVE 启用，DISABLED 停用。传 null 与传未知编码都会被拒绝，"
                + "不按「不传就不改」处理；与当前状态相同时返回成功，重复提交不报错")
        DictStatus status) {
}
