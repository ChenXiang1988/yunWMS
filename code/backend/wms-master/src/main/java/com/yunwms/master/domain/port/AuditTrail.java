package com.yunwms.master.domain.port;

import com.yunwms.master.domain.model.OwnerAuditEvent;

import java.util.Map;

/**
 * 审计留痕：本模块的写操作把自己的变更交给审计能力记录下来。
 *
 * <p><b>为什么本模块只声明端口：</b>审计日志落在 {@code sys_audit_log}，归属 {@code wms-auth}
 * （见测试资源里的 {@code table-ownership.json}，越界的 Mapper 会被 {@code MapperTableAccessTest} 拦住），
 * 而 {@code auth} 与 {@code master} 互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截）。只有 {@code wms-app} 同时认识两边，因此与本模块的取号、字典、
 * 账号计数、开号四个端口同一路子：端口在这里，适配器在那里装配。
 *
 * <p><b>签名里不出现对方的类型，也不出现会话与 Servlet 类型：</b>端口一旦引用 {@code AuditAction} /
 * {@code AuditBizType} / {@code AuditEntry}，两个模块就通过类型耦合成了「改一处、连锁编译」的关系，
 * 边界测试也会随之变红；引用 Sa-Token 或 {@code HttpServletRequest} 同样会把「这次请求是从哪个端来的」
 * 变成调用方的责任——而调用方连自己是怎么被调起来的都不知道。操作人、来源地址与来源端由实现自己取。
 *
 * <p><b>{@code bizId} 用系统编码，不用数据库主键：</b>管理端按货主编码追溯（「这个客户被谁改过」），
 * 主键是一串在界面与单据上都不出现的数字，按它检索等于让审计查不到——而审计的价值全在于被查到。
 *
 * <p><b>实现必须在调用方的事务内写入（调用方有事务时）：</b>审计与业务变更要么一起成功、要么一起回滚。
 * 分开提交会留下「业务改了但没留痕」或「留了痕但业务没改」两种都需要人工核对的残局。
 */
public interface AuditTrail {

    /**
     * 记录一次对象变更。
     *
     * @param event  本模块能产生的事件，决定审计的对象类型、动作与摘要措辞
     * @param bizId  对象标识；货主用系统编码，不用数据库主键——审计要被「按编码追溯」查得到
     * @param before 变更前快照，新增时为 {@code null}
     * @param after  变更后快照，删除时为 {@code null}
     */
    void record(OwnerAuditEvent event, String bizId, Map<String, Object> before, Map<String, Object> after);
}
