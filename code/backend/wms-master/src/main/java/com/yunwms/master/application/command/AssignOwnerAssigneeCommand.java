package com.yunwms.master.application.command;

/**
 * 配置货主负责客服的入参：提交的是「主、副两个角色最终应该是谁」，不是「这一次改哪一个角色」。
 *
 * <p><b>为什么是全量替换而不是部分更新：</b>部分更新必须区分「这个字段没传」与「传了空值表示清除」，
 * 而 HTTP 请求体、JSON 反序列化与 {@code record} 三处都不携带这个区别——字段缺失时构造出来的就是
 * {@code null}，与显式清空是同一个值。前端于是只能自己记住「哪些字段没有传」，后端却无法验证它记对了没有；
 * 记错一次，「只换副负责人」的提交就会静默地把主负责人一并清掉。全量替换下目标状态由这一次请求完整表达，
 * 两条关系的写法没有第二种读法。
 *
 * <p><b>{@link #primaryUserId} 为空不是「不改」，而是「撤销主负责人」</b>——那是规格禁止的状态
 * （每个货主 MUST 有一名主负责人），因此用例判 40000，而不是把它当成缺省值跳过。
 * 字段类型取 {@link Long} 而非 {@code long} 就是为了让「没填」能被表达出来，
 * 否则那条校验分支永远走不到，输入会在反序列化阶段变成一个 0，最后以「员工不存在」的身份被拒绝。
 *
 * <p><b>{@link #secondaryUserId} 为空是合法输入</b>：它的含义是「该货主不设副负责人」，
 * 用例会清掉已有的那一段关系。副负责人本来就是可选的（规格 MAY 有一名）。
 *
 * <p><b>本类型不做字段校验，也不判断两个标识是否指向同一人：</b>主必填、主副不同、员工是否在册
 * 这三条都属于业务规则，落在 {@code OwnerAssigneeService#assign}。这里只承载调用方提交的值，
 * 让「校验」只有一个落点。
 *
 * @param ownerId         货主标识
 * @param primaryUserId   主负责人的员工标识；为空时用例拒绝，因为清除主负责人不在这里的语义之内
 * @param secondaryUserId 副负责人的员工标识；为 {@code null} 表示该货主不设副负责人
 */
public record AssignOwnerAssigneeCommand(
        long ownerId,
        Long primaryUserId,
        Long secondaryUserId) {
}
