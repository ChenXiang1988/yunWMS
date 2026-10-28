package com.yunwms.master.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 开通货主主账号的响应体。
 *
 * <p>账号标识以字符串传输：{@code Long} 超过 2^53 时 JS 会丢精度，等到出问题再改要动全部前端
 * （{@code docs/api-convention.md} 第五节）。
 *
 * <p>登录名原样回带，不接受调用方指定：它是这次开通唯一一个人能记住的值（等于货主系统编码），
 * 界面把它显示在成功提示里，管理员才能把「刚开的那个账号」转告货主。
 *
 * @param accountId 新建账号的标识，字符串形式的十进制整数
 * @param loginName 登录名，等于该货主的系统编码，形如 {@code OWNER0001}；创建后不可修改
 */
public record ProvisionedOwnerAccountResponse(

        @Schema(description = "新建账号的标识，字符串形式的十进制整数；后续停用、重置密码用它定位",
                example = "42")
        String accountId,

        @Schema(description = "登录名，等于该货主的系统编码，形如 OWNER0001；由系统生成、不可修改，"
                + "账号首次登录后会被要求先改密码", example = "OWNER0001")
        String loginName) {
}
