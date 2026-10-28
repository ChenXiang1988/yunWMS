package com.yunwms.master.application.command;

/**
 * 开通货主主账号的入参。
 *
 * <p><b>没有登录名：</b>它由货主的系统编码决定，调用方无权指定。把登录名放进命令，
 * 等于给「按员工手机号建个货主账号」留了一条路，而规格明确要求登录名由系统生成、不得用手机号。
 *
 * <p><b>没有手机号：</b>手机号属联系人档案（3.5），账号本身不保存它。开通时多收一个字段，
 * 下一步就会有人拿它去建联系人——那是另一个用例、另一张表。
 *
 * <p><b>没有角色：</b>货主侧角色属 3.4 / 4.4；本用例只负责建一条能登录的账号。
 *
 * <p>携带原始值不做清洗：去空白、空串按未填处理属于业务规则，与校验同处用例层。
 *
 * @param ownerId         要开通账号的货主标识
 * @param displayName     主账号姓名（展示名），可为空串或全空白，由用例判定
 * @param initialPassword 初始密码明文，仅在本进程内存中短暂存在
 */
public record ProvisionOwnerAccountCommand(long ownerId, String displayName, String initialPassword) {
}
