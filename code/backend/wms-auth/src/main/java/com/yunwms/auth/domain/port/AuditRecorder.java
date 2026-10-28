package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.AuditEntry;

/**
 * 审计日志的写入入口。
 *
 * <p><b>只追加：</b>本接口不提供修改与删除，实现也不得暴露。规格要求「审计日志 MUST 只追加不可修改」，
 * 能力上不提供这条路径，比事后审计「有没有人删过」可靠得多——后者只能在删除动作本身也被记进
 * 同一个可被删除的地方时才成立。
 *
 * <p><b>操作人信息由实现自己取</b>，不由调用方传：标识、姓名、来源地址与来源端都在实现层从当前请求
 * 上下文读取。调用方各写一遍会遗漏，而且来源端只有实现层能可靠判定——调用方不知道自己是被哪个端的
 * 请求调起来的。
 *
 * <p>取不到操作人（系统内部触发，如定时任务）时仍然写入，操作人标识落 {@code NULL}：
 * 审计的价值在于「记下了」，而不是「因为取不到人就整条不记」。
 */
public interface AuditRecorder {

    /**
     * 追加一条审计日志。
     *
     * <p>实现必须在调用方的事务内写入（调用方有事务时加入它）：审计与业务变更要么一起成功，
     * 要么一起回滚。分开提交会得到「业务改了但没留痕」或「留了痕但业务没改」两种都需要人工核对的残局。
     *
     * @param entry 日志内容；{@code beforeValue} 与 {@code afterValue} 由调用方给出完整快照
     * @throws IllegalArgumentException 日志内容缺少对象类型、动作或对象标识，或标识与摘要超长
     */
    void record(AuditEntry entry);
}
