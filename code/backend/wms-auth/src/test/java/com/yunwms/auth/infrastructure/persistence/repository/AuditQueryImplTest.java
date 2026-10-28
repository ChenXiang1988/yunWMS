package com.yunwms.auth.infrastructure.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditLog;
import com.yunwms.auth.domain.model.AuditPage;
import com.yunwms.auth.domain.model.SourcePortal;
import com.yunwms.auth.infrastructure.persistence.entity.SysAuditLogEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysAuditLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 审计查询实现的单测。
 *
 * <p>要钉住的是转换处的两条口径：<b>库里的编码怎么还原成领域枚举</b>（未知编码必须失败，不能回落成默认值），
 * 以及 <b>{@code jsonb} 文本怎么变成快照</b>（{@code NULL} 保持为 {@code null}，与空对象区分开）。
 * 排序与 {@code LIMIT/OFFSET} 的最终效果只能在真实库上验证，见 {@code wms-app} 的
 * {@code AuditLogIntegrationTest}。
 */
@DisplayName("审计查询")
class AuditQueryImplTest {

    private static final long LOG_ID = 99L;
    private static final Instant CREATED_AT = Instant.parse("2026-10-28T02:03:04Z");

    private SysAuditLogMapper sysAuditLogMapper;
    private AuditQueryImpl query;

    @BeforeEach
    void setUp() {
        sysAuditLogMapper = mock(SysAuditLogMapper.class);
        query = new AuditQueryImpl(sysAuditLogMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("按对象检索：偏移量按 (页码 - 1) × 每页条数换算，总数取自计数语句")
    void shouldConvertPageNoToOffsetAndCarryTotal() {
        given(sysAuditLogMapper.countByObject("OWNER", "OWNER0001")).willReturn(42L);
        given(sysAuditLogMapper.selectPageByObject("OWNER", "OWNER0001", 20, 40L))
                .willReturn(List.of(row()));

        AuditPage page = query.findByObject(AuditBizType.OWNER, "OWNER0001", 3, 20);

        verify(sysAuditLogMapper).selectPageByObject("OWNER", "OWNER0001", 20, 40L);
        assertThat(page.pageNo()).isEqualTo(3);
        assertThat(page.pageSize()).isEqualTo(20);
        assertThat(page.total()).isEqualTo(42L);
        assertThat(page.records()).hasSize(1);
    }

    @Test
    @DisplayName("按对象检索：编码还原成枚举，来源端为空时保持为空")
    void shouldRestoreEnumsAndKeepMissingPortalNull() {
        given(sysAuditLogMapper.countByObject("OWNER", "OWNER0001")).willReturn(1L);
        given(sysAuditLogMapper.selectPageByObject("OWNER", "OWNER0001", 20, 0L))
                .willReturn(List.of(row()));

        AuditLog log = query.findByObject(AuditBizType.OWNER, "OWNER0001", 1, 20).records().getFirst();

        assertThat(log.id()).isEqualTo(LOG_ID);
        assertThat(log.bizType()).isEqualTo(AuditBizType.OWNER);
        assertThat(log.action()).isEqualTo(AuditAction.UPDATE);
        assertThat(log.operatorId()).isEqualTo(7L);
        assertThat(log.operatorName()).isEqualTo("张三");
        assertThat(log.sourcePortal()).isNull();
        assertThat(log.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("按对象检索：来源端有值时按编码还原")
    void shouldRestoreSourcePortal() {
        SysAuditLogEntity row = row();
        row.setSourcePortal("PORTAL");
        given(sysAuditLogMapper.selectPageByObject("OWNER", "OWNER0001", 20, 0L)).willReturn(List.of(row));

        AuditLog log = query.findByObject(AuditBizType.OWNER, "OWNER0001", 1, 20).records().getFirst();

        assertThat(log.sourcePortal()).isEqualTo(SourcePortal.PORTAL);
    }

    @Test
    @DisplayName("按对象检索：快照往返，嵌套结构不丢字段；没有的值保持 null")
    void shouldParseSnapshots() {
        SysAuditLogEntity row = row();
        row.setBeforeValue("{\"weight\":{\"value\":12.5,\"unit\":\"KG\"},\"tags\":[\"易碎\",\"冷藏\"]}");
        row.setAfterValue("{\"weight\":{\"value\":13.0,\"unit\":\"KG\"},\"tags\":[\"易碎\"]}");
        given(sysAuditLogMapper.selectPageByObject("OWNER", "OWNER0001", 20, 0L)).willReturn(List.of(row));

        AuditLog log = query.findByObject(AuditBizType.OWNER, "OWNER0001", 1, 20).records().getFirst();

        assertThat(log.beforeValue())
                .containsEntry("tags", List.of("易碎", "冷藏"))
                .containsEntry("weight", Map.of("value", 12.5, "unit", "KG"));
        assertThat(log.afterValue()).containsEntry("tags", List.of("易碎"));
    }

    @Test
    @DisplayName("按对象检索：库里没有的编码一律失败，不按默认值兜底")
    void shouldRejectUnknownCodes() {
        SysAuditLogEntity unknownBizType = row();
        unknownBizType.setBizType("WAREHOUSE");
        given(sysAuditLogMapper.selectPageByObject("OWNER", "OWNER0001", 20, 0L))
                .willReturn(List.of(unknownBizType));
        assertThatThrownBy(() -> query.findByObject(AuditBizType.OWNER, "OWNER0001", 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WAREHOUSE");

        SysAuditLogEntity unknownAction = row();
        unknownAction.setAction("IMPORT");
        given(sysAuditLogMapper.selectPageByObject("OWNER", "OWNER0001", 20, 0L))
                .willReturn(List.of(unknownAction));
        assertThatThrownBy(() -> query.findByObject(AuditBizType.OWNER, "OWNER0001", 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IMPORT");

        SysAuditLogEntity unknownPortal = row();
        unknownPortal.setSourcePortal("PDA");
        given(sysAuditLogMapper.selectPageByObject("OWNER", "OWNER0001", 20, 0L))
                .willReturn(List.of(unknownPortal));
        assertThatThrownBy(() -> query.findByObject(AuditBizType.OWNER, "OWNER0001", 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDA");
    }

    @Test
    @DisplayName("按对象检索：快照不是合法 JSON 时带日志标识失败")
    void shouldRejectMalformedSnapshot() {
        SysAuditLogEntity row = row();
        row.setAfterValue("not-json");
        given(sysAuditLogMapper.selectPageByObject("OWNER", "OWNER0001", 20, 0L)).willReturn(List.of(row));

        assertThatThrownBy(() -> query.findByObject(AuditBizType.OWNER, "OWNER0001", 1, 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(LOG_ID));
    }

    /** 一行合法的日志；快照默认都为 {@code null}，各用例按需覆盖。 */
    private static SysAuditLogEntity row() {
        SysAuditLogEntity row = new SysAuditLogEntity();
        row.setId(LOG_ID);
        row.setBizType("OWNER");
        row.setBizId("OWNER0001");
        row.setAction("UPDATE");
        row.setOperatorId(7L);
        row.setOperatorName("张三");
        row.setOperatorIp("10.0.0.8");
        row.setSummary("修改结算主体");
        row.setCreatedAt(CREATED_AT);
        return row;
    }
}
