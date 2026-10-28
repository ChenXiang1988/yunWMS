package com.yunwms.app.bridge;

import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.master.domain.model.OwnerAuditEvent;
import com.yunwms.master.domain.port.AuditTrail;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 把 {@code wms-master} 的审计窄端口接到 {@code wms-auth} 的审计写入端口上。
 *
 * <p><b>为什么这个类在 {@code wms-app}：</b>审计日志落在 {@code sys_audit_log}，归属 {@code wms-auth}
 * （见测试资源里的 {@code table-ownership.json}，越界的 Mapper 会被 {@code MapperTableAccessTest} 拦住），
 * 而 {@code auth} 与 {@code master} 互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截）。只有 {@code wms-app} 的运行期 classpath 同时包含两边，
 * 与 {@link OwnerCodeGeneratorAdapter} / {@link DictLookupAdapter} / {@link OwnerAccountCounterAdapter} /
 * {@link OwnerAccountProvisionerAdapter} 同一路子。
 *
 * <p><b>为什么在这里把字符串验成枚举：</b>{@code OwnerAuditEvent} 用字符串表达对象类型与动作
 * （它不可能引用 {@code wms-auth} 的枚举），拼错在 {@code master} 侧不会有任何提示。校验放在这个
 * 唯一的跨模块接缝上，拼错立刻抛 {@link IllegalStateException}——这是编程错误，不是业务错误，
 * 不会变成一次用户可见的拒绝，也不会写进一条对象类型或动作对不上的垃圾审计。
 *
 * <p><b>操作人、来源地址与来源端不在这里取：</b>它们由 {@link AuditRecorder} 的实现从当前请求上下文读。
 * 本类连「这次请求从哪个端来」都不问——那只有实现层能可靠判定，在这里再判一次就是第二份口径。
 *
 * <p><b>异常一律透出：</b>审计写失败必须让业务一起失败。写入加入调用方的事务（{@code REQUIRED}），
 * 在这里把异常吞掉换成「记日志后继续」，等于让业务在没有任何留痕的情况下提交成功。
 */
@Component
public class AuditTrailAdapter implements AuditTrail {

    private final AuditRecorder auditRecorder;

    public AuditTrailAdapter(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    /**
     * {@inheritDoc}
     *
     * <p>摘要取「事件模板 + 对象标识」（{@code "新建货主 OWNER0005"}）：模板由事件给，标识由调用点给，
     * 两者在这里拼一次。让每个调用点各写一句完整摘要，同一类事件的措辞会在几处慢慢分叉。
     */
    @Override
    public void record(OwnerAuditEvent event, String bizId, Map<String, Object> before, Map<String, Object> after) {
        auditRecorder.record(new AuditEntry(
                resolveBizType(event),
                bizId,
                resolveAction(event),
                event.summary() + " " + bizId,
                before,
                after));
    }

    /**
     * 把事件上的对象类型编码验成 {@code wms-auth} 的枚举。
     *
     * @param event 本模块产生的审计事件
     * @return 对应的审计对象类型
     * @throws IllegalStateException 编码与对端枚举名对不上，说明事件定义写错了
     */
    private static AuditBizType resolveBizType(OwnerAuditEvent event) {
        String code = event.bizType();
        try {
            return AuditBizType.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("审计对象类型编码无法映射到 wms-auth：事件=" + event.name()
                    + "，编码=" + code, e);
        }
    }

    /**
     * 把事件上的动作编码验成 {@code wms-auth} 的枚举。
     *
     * @param event 本模块产生的审计事件
     * @return 对应的审计动作
     * @throws IllegalStateException 编码与对端枚举名对不上，说明事件定义写错了
     */
    private static AuditAction resolveAction(OwnerAuditEvent event) {
        String code = event.action();
        try {
            return AuditAction.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("审计动作编码无法映射到 wms-auth：事件=" + event.name()
                    + "，编码=" + code, e);
        }
    }
}
