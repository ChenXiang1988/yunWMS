package com.yunwms.auth.infrastructure.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditLog;
import com.yunwms.auth.domain.model.AuditPage;
import com.yunwms.auth.domain.model.SourcePortal;
import com.yunwms.auth.domain.port.AuditQuery;
import com.yunwms.auth.infrastructure.persistence.entity.SysAuditLogEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysAuditLogMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * {@link AuditQuery} 的 MyBatis 实现。
 *
 * <p>两条 SQL：计数与分页。计数与分页分开成两次查询而不是用窗口函数一次取回：结果为空页时窗口函数
 * 不会返回任何行，总数就没地方取（与 {@code RoleQuery} 同一口径）。
 *
 * <p>{@code jsonb} 的解析放在这里而不是 SQL 或响应组装处：{@code jsonb} 经 JDBC 以文本返回，
 * 解析它属于持久化实现细节；放在持久化层之外，领域模型与接口层就都要认识「库里存的是 JSON 文本」。
 *
 * <p>枚举与来源端的中文说明不做在这里：{@link AuditBizType#fromCode}、{@link AuditAction#fromCode}、
 * {@link SourcePortal#fromCode} 遇到未知编码直接抛异常。这是刻意的——库里出现未知编码说明数据被
 * 绕过 CHECK 写坏，把脏数据当成 {@code CREATE} 处理会让审计记录说谎。
 */
@Repository
public class AuditQueryImpl implements AuditQuery {

    /** 快照的形状：任意字符串键到任意 JSON 值。审计本身不解释它的业务含义。 */
    private static final TypeReference<Map<String, Object>> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    private final SysAuditLogMapper sysAuditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditQueryImpl(SysAuditLogMapper sysAuditLogMapper, ObjectMapper objectMapper) {
        this.sysAuditLogMapper = sysAuditLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuditPage findByObject(AuditBizType bizType, String bizId, int pageNo, int pageSize) {
        // 枚举入库存的是 name()，查询条件按同一编码给出；入参已由用例层保证非空
        String bizTypeCode = bizType.name();
        long total = sysAuditLogMapper.countByObject(bizTypeCode, bizId);
        long offset = (long) (pageNo - 1) * pageSize;
        List<AuditLog> records = sysAuditLogMapper.selectPageByObject(bizTypeCode, bizId, pageSize, offset)
                .stream()
                .map(this::toAuditLog)
                .toList();
        return new AuditPage(pageNo, pageSize, total, records);
    }

    /**
     * 把一行日志转成读模型，并解析两份快照。
     *
     * @param row 数据库返回的行
     * @return 审计日志读模型
     * @throws IllegalStateException 快照不是合法的 JSON 对象，说明数据已损坏
     * @throws IllegalArgumentException 库里的对象类型、动作或来源端编码未知，说明数据已损坏
     */
    private AuditLog toAuditLog(SysAuditLogEntity row) {
        return new AuditLog(
                row.getId(),
                AuditBizType.fromCode(row.getBizType()),
                row.getBizId(),
                AuditAction.fromCode(row.getAction()),
                row.getOperatorId(),
                row.getOperatorName(),
                row.getOperatorIp(),
                row.getSourcePortal() == null ? null : SourcePortal.fromCode(row.getSourcePortal()),
                row.getSummary(),
                parseSnapshot(row, "before_value", row.getBeforeValue()),
                parseSnapshot(row, "after_value", row.getAfterValue()),
                row.getCreatedAt());
    }

    /**
     * 解析一份快照。
     *
     * <p>失败时带上日志标识与列名再抛：SQL 只回一个 JSON 文本，没有标识的话「哪一条日志写坏了」
     * 只能靠人翻库。原始异常保留为 cause，堆栈里仍有 Jackson 的定位信息。
     *
     * @param row   数据库返回的行
     * @param column 列名，用于失败信息
     * @param json  列值；为 {@code null} 表示这条日志没有该项（新增没有变更前、删除没有变更后）
     * @return 快照；列为 {@code NULL} 时返回 {@code null}，与「空对象」区分开
     * @throws IllegalStateException 快照不是合法的 JSON 对象
     */
    private Map<String, Object> parseSnapshot(SysAuditLogEntity row, String column, String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SNAPSHOT_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("审计日志的 " + column + " 不是合法的 JSON 对象：id="
                    + row.getId(), ex);
        }
    }
}
