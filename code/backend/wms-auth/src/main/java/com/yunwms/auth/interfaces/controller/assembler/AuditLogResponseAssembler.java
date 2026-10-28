package com.yunwms.auth.interfaces.controller.assembler;

import com.yunwms.auth.domain.model.AuditLog;
import com.yunwms.auth.domain.model.AuditPage;
import com.yunwms.auth.interfaces.controller.response.AuditLogItemResponse;
import com.yunwms.web.api.PageResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 审计日志用例结果到响应体的转换。
 *
 * <p>转换集中在这里，是因为「标识以字符串传输」「枚举以编码字符串返回、来源端可为空」都属于接口层约定：
 * 用例只认领域模型，是否序列化成字符串、空值怎么表示由契约决定。
 */
@Component
public class AuditLogResponseAssembler {

    /**
     * 组装分页响应。
     *
     * @param page 一页审计日志
     * @return 分页响应体，{@code records} 恒不为 {@code null}
     */
    public PageResult<AuditLogItemResponse> toPageResponse(AuditPage page) {
        List<AuditLogItemResponse> records = page.records().stream()
                .map(AuditLogResponseAssembler::toItem)
                .toList();
        return new PageResult<>(page.pageNo(), page.pageSize(), page.total(), records);
    }

    /**
     * 组装单行响应。
     *
     * @param log 一条审计日志
     * @return 可直接序列化的行对象
     */
    private static AuditLogItemResponse toItem(AuditLog log) {
        return new AuditLogItemResponse(
                Long.toString(log.id()),
                log.bizType().name(),
                log.bizId(),
                log.action().name(),
                // 操作人标识可为空：系统内部触发的日志没有操作人，转成字符串前必须先判空
                log.operatorId() == null ? null : Long.toString(log.operatorId()),
                log.operatorName(),
                log.operatorIp(),
                log.sourcePortal() == null ? null : log.sourcePortal().name(),
                log.summary(),
                log.beforeValue(),
                log.afterValue(),
                log.createdAt());
    }
}
