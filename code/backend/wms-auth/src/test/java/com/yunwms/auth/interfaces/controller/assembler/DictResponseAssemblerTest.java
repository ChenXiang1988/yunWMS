package com.yunwms.auth.interfaces.controller.assembler;

import com.yunwms.auth.domain.model.DictItem;
import com.yunwms.auth.domain.model.DictStatus;
import com.yunwms.auth.domain.model.DictTypeSummary;
import com.yunwms.auth.interfaces.controller.response.DictItemResponse;
import com.yunwms.auth.interfaces.controller.response.DictTypeListItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 字典响应组装的单测。
 *
 * <p>钉住三件接口层约定：标识以字符串传输、状态以枚举名传输、{@code ext} 原样透传。
 *
 * <p>其中最重要的一条是「{@code labelEn} 为空时原样返回 {@code null}」：规格说「缺失英文名时
 * 回退中文」，但回退属于展示层。后端一旦在这里补上 {@code label}，前端就再也分不清
 * 「数据缺英文名」与「英文名恰好等于中文名」，而前者正是要修的数据问题。
 */
@DisplayName("字典响应组装")
class DictResponseAssemblerTest {

    private final DictResponseAssembler assembler = new DictResponseAssembler();

    @Test
    @DisplayName("项：标识转字符串，状态转枚举名，ext 原样透传")
    void shouldAssembleItemKeepingContract() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("parent", "US");

        DictItemResponse response = DictResponseAssembler.toItemResponse(
                new DictItem(42L, "PROVINCE", "加利福尼亚州", "California", "US-CA", 50,
                        DictStatus.ACTIVE, ext));

        assertThat(response.id()).isEqualTo("42");
        assertThat(response.value()).isEqualTo("US-CA");
        assertThat(response.sortNo()).isEqualTo(50);
        assertThat(response.status()).isEqualTo("ACTIVE");
        // 后端不解释 ext 的业务含义：原样交出，键与值都不动
        assertThat(response.ext()).containsEntry("parent", "US");
    }

    @Test
    @DisplayName("项：labelEn 为 null 时原样返回 null，不回退中文")
    void shouldKeepNullLabelEnWithoutFallback() {
        DictItemResponse response = DictResponseAssembler.toItemResponse(
                new DictItem(7L, "TEMP_ZONE", "冷藏", null, "CHILLED", 20,
                        DictStatus.DISABLED, Map.of()));

        assertThat(response.label()).isEqualTo("冷藏");
        assertThat(response.labelEn()).isNull();
        assertThat(response.status()).isEqualTo("DISABLED");
        // ext 为空的项返回空对象而不是 null：前端取值不必先判空
        assertThat(response.ext()).isEmpty();
    }

    @Test
    @DisplayName("类型清单：字段一一对应，项数原样带出")
    void shouldAssembleTypeList() {
        List<DictTypeListItemResponse> response = assembler.toTypeListResponse(List.of(
                new DictTypeSummary("COUNTRY", "国家", "Country", 28L, DictStatus.ACTIVE)));

        assertThat(response).hasSize(1);
        assertThat(response.get(0).dictType()).isEqualTo("COUNTRY");
        assertThat(response.get(0).nameEn()).isEqualTo("Country");
        assertThat(response.get(0).itemCount()).isEqualTo(28L);
        assertThat(response.get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("批量：键顺序与项顺序都保持不变")
    void shouldKeepOrderInBatchResponse() {
        Map<String, List<DictItem>> itemsByType = new LinkedHashMap<>();
        itemsByType.put("OWNER_TYPE", List.of(
                new DictItem(1L, "OWNER_TYPE", "品牌方", "Brand Owner", "BRAND", 10,
                        DictStatus.ACTIVE, Map.of())));
        itemsByType.put("TEMP_ZONE", List.of(
                new DictItem(2L, "TEMP_ZONE", "常温", "Ambient", "NORMAL", 10,
                        DictStatus.ACTIVE, Map.of()),
                new DictItem(3L, "TEMP_ZONE", "冷冻", "Frozen", "FROZEN", 30,
                        DictStatus.ACTIVE, Map.of())));

        Map<String, List<DictItemResponse>> response = assembler.toBatchResponse(itemsByType);

        assertThat(response).containsOnlyKeys("OWNER_TYPE", "TEMP_ZONE");
        assertThat(response.get("TEMP_ZONE")).extracting(DictItemResponse::value)
                .containsExactly("NORMAL", "FROZEN");
    }
}
