package com.yunwms.master.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 开通货主主账号的请求体。
 *
 * <p><b>没有 {@code loginName}：</b>登录名由系统按货主的系统编码生成，界面只读。多传会被
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} 拒绝（{@code docs/api-convention.md} 第六节），
 * 不是静默忽略——静默忽略会让调用方以为「我指定的登录名生效了」。
 *
 * <p><b>没有 {@code phone}：</b>手机号属联系人档案（3.5），账号本身不保存它（规格明令）。
 * 在这里收一个手机号，等于给「账号表里也存一份」开口子，而两份数据迟早不一致。
 *
 * <p><b>没有 {@code roleIds}：</b>货主侧的角色与权限点属 3.4 / 4.4。当前给货主账号分配角色会
 * 造成越权——鉴权侧见到 {@code builtin = true} 的角色会展开全部权限点。
 *
 * <p>两个字段都带标准约束注解：单字段约束由 Bean Validation 表达；用例层另有一次姓名非空判定，
 * 因为批量导入、初始化脚本这类非 HTTP 入口同样要受这条业务规则约束。
 *
 * @param displayName     主账号姓名（展示名），必填，最长 64 个字符
 * @param initialPassword 初始密码明文，必填；账号落地时只存哈希，并被标记为首次登录必须修改
 */
public record OwnerAccountProvisionRequest(

        @NotBlank(message = "主账号姓名不能为空")
        @Size(max = 64, message = "主账号姓名不能超过 64 个字符")
        @Schema(description = "主账号姓名（展示名），必填，最长 64 个字符；登录货主端后显示在页头。"
                + "与登录名无关——登录名固定取货主系统编码", example = "张三")
        String displayName,

        @NotBlank(message = "初始密码不能为空")
        @Schema(description = "初始密码明文，必填，需满足密码策略：8–20 位且同时包含大写字母、小写字母与数字。"
                + "账号落地时只存哈希，且被标记为首次登录必须修改；该账号首次登录后除改密外"
                + "调用任何业务接口都会返回 40104", example = "Owner@1234")
        String initialPassword) {
}
