package com.yunwms.master.application.audit;

import com.yunwms.master.domain.model.NewOwner;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.domain.model.OwnerProfile;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.model.SkuPrefix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计快照的单测：不起 Spring、不连库，只验「写进 {@code jsonb} 的那份字段集」。
 *
 * <p>要钉住三件事：
 * <ul>
 *   <li><b>键集是白名单</b>——实体上多一个字段，快照不会自动带上它。这条防的是「将来加了敏感字段、
 *       审计表开始悄悄保存它」，而审计只追加、发现时删不掉；</li>
 *   <li><b>null 保留键</b>——{@code "remark": null} 与「没有 remark 这个键」在事后追溯里含义不同；</li>
 *   <li><b>值都是标量</b>——枚举取名字、值对象取原文。把领域对象直接塞进 {@code Map} 时，
 *       Jackson 会按记录结构展开，快照的键集就不再是这里断言的那一份。</li>
 * </ul>
 */
@DisplayName("货主审计快照")
class OwnerAuditSnapshotsTest {

    /** 白名单的全部键，顺序不参与比较：落库是 jsonb，读回来键序会变。 */
    private static final List<String> EXPECTED_KEYS = List.of(
            "code", "name", "nameEn", "shortName", "externalCode", "skuPrefix", "ownerType",
            "settlementEntity", "status", "country", "province", "city", "district", "postalCode",
            "addressLine", "remark");

    @Test
    @DisplayName("键集固定为白名单：标识与时间戳等实体字段不进快照")
    void shouldContainOnlyWhitelistedKeys() {
        Map<String, Object> snapshot = OwnerAuditSnapshots.of(owner());

        assertThat(snapshot.keySet())
                .as("实体上的 id、createdAt、createdBy、updatedAt、updatedBy 一律不进快照")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_KEYS);
    }

    @Test
    @DisplayName("值为 null 的字段保留键：remark 为 null 表示「当时没有备注」")
    void shouldKeepKeysOfNullValues() {
        Map<String, Object> snapshot = OwnerAuditSnapshots.of(owner());

        assertThat(snapshot).containsKey("remark");
        assertThat(snapshot.get("remark")).isNull();
        // 三个可空字段都走同一条规则：键在、值为 null
        assertThat(snapshot.keySet()).contains("nameEn", "shortName", "remark");
        assertThat(snapshot.get("nameEn")).isNull();
    }

    @Test
    @DisplayName("值都是标量：枚举取名字、值对象取原文，快照里没有领域对象")
    void shouldFlattenValueObjectsAndEnums() {
        Map<String, Object> snapshot = OwnerAuditSnapshots.of(owner());

        assertThat(snapshot.get("status")).isInstanceOf(String.class).isEqualTo("ACTIVE");
        // 直接放 SkuPrefix 会变成 {"value":"AMZ2"}，读日志的人与查询条件都要多穿一层
        assertThat(snapshot.get("skuPrefix")).isInstanceOf(String.class).isEqualTo("AMZ2");
        assertThat(snapshot.get("country")).isEqualTo("CN");
    }

    @Test
    @DisplayName("建档快照：编码与初始状态取自本次提交，档案部分取写入模型")
    void shouldSnapshotNewOwnerWithGeneratedCode() {
        Map<String, Object> snapshot = OwnerAuditSnapshots.of(newOwner(), "OWNER0005", OwnerStatus.DRAFT);

        assertThat(snapshot)
                .containsEntry("code", "OWNER0005")
                .containsEntry("status", "DRAFT")
                .containsEntry("name", "深圳某某贸易")
                .containsEntry("skuPrefix", "AMZ2");
        assertThat(snapshot.keySet()).containsExactlyInAnyOrderElementsOf(EXPECTED_KEYS);
    }

    @Test
    @DisplayName("编辑后快照：档案取本次落库的值，编码 / 前缀 / 状态取既成事实")
    void shouldMergeSubmittedProfileWithImmutableFields() {
        OwnerProfile replaced = new OwnerProfile(
                "深圳某某贸易（改名）", null, null, "EXT-002", "BRAND", "深圳某某贸易有限公司",
                new OwnerAddress("CN", "CN-SH", "深圳", "南山区", "518000", "科技园路 2 号"), "新备注");

        Map<String, Object> snapshot = OwnerAuditSnapshots.ofUpdated(owner(), replaced);

        assertThat(snapshot)
                .containsEntry("name", "深圳某某贸易（改名）")
                .containsEntry("externalCode", "EXT-002")
                .containsEntry("addressLine", "科技园路 2 号")
                .containsEntry("remark", "新备注")
                // 编辑改不到的三项必须原样，否则日志会暗示这次调用改了它们
                .containsEntry("code", "OWNER0005")
                .containsEntry("skuPrefix", "AMZ2")
                .containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("流转后快照：只换状态，其余字段取流转前的既成事实")
    void shouldChangeOnlyStatus() {
        Map<String, Object> snapshot = OwnerAuditSnapshots.ofStatusChanged(owner(), OwnerStatus.TERMINATED);

        assertThat(snapshot)
                .containsEntry("status", "TERMINATED")
                .containsEntry("code", "OWNER0005")
                .containsEntry("name", "深圳某某贸易")
                .containsEntry("postalCode", "518000");
    }

    @Test
    @DisplayName("开号快照：只有登录名与账号标识，标识是字符串")
    void shouldRecordAccountOpenedWithStringId() {
        Map<String, Object> snapshot = OwnerAuditSnapshots.ofAccountOpened("OWNER0005", 24L);

        assertThat(snapshot)
                .hasSize(2)
                .containsEntry("loginName", "OWNER0005")
                .containsEntry("accountId", "24");
    }

    /**
     * 一条已落库的货主：可空字段（nameEn、shortName、remark）刻意留 {@code null}，
     * 用于验证「键在、值为 null」。
     *
     * @return 货主
     */
    private static Owner owner() {
        return new Owner(
                5L,
                "OWNER0005",
                "深圳某某贸易",
                null,
                null,
                "EXT-001",
                new SkuPrefix("AMZ2"),
                "BRAND",
                "深圳某某贸易有限公司",
                OwnerStatus.ACTIVE,
                new OwnerAddress("CN", "CN-SH", "深圳", "南山区", "518000", "科技园路 1 号"),
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                42L,
                Instant.parse("2026-01-01T00:00:00Z"),
                42L);
    }

    /**
     * 一条待落库的新货主，属性取与 {@link #owner()} 相同的值。
     *
     * @return 新货主
     */
    private static NewOwner newOwner() {
        return new NewOwner(
                "深圳某某贸易",
                null,
                null,
                "EXT-001",
                new SkuPrefix("AMZ2"),
                "BRAND",
                "深圳某某贸易有限公司",
                new OwnerAddress("CN", "CN-SH", "深圳", "南山区", "518000", "科技园路 1 号"),
                null);
    }
}
