package com.yunwms.master.application.dto;

/**
 * 开通主账号的结果：新账号的标识与它的登录名。
 *
 * <p><b>为什么把登录名带回来：</b>它是这次提交产生的唯一一个人能记住的标识。界面要把它显示在
 * 成功提示里（「登录名 OWNER0001」），否则管理员讲不清「刚才开的那个账号叫什么」——
 * 账号标识是一串数据库主键，而登录名正好是货主编码，两者都不会出现在货主的档案字段里。
 *
 * <p>两个值都以既成事实出现：标识由 {@code wms-auth} 的建号用例返回，登录名取自货主编码。
 * 是否以字符串序列化是接口层的约定，本对象不掺协议决定。
 *
 * @param accountId 数据库生成的账号标识
 * @param loginName 登录名，等于该货主的系统编码
 */
public record ProvisionedOwnerAccount(long accountId, String loginName) {
}
