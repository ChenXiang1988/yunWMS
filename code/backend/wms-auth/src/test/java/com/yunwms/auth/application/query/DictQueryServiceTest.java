package com.yunwms.auth.application.query;

import com.yunwms.auth.domain.model.DictItem;
import com.yunwms.auth.domain.model.DictStatus;
import com.yunwms.auth.domain.model.DictTypeSummary;
import com.yunwms.auth.domain.port.DictQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 字典查询用例的单测。
 *
 * <p>端口替换为 mock，不起 Spring、不连库。这里钉住的是用例自己的职责：{@code enabledOnly} 到状态
 * 筛选的翻译、{@code parentValue} 的归一、类型不存在转 404、批量读取的类型归一与「一次查询」。
 *
 * <p>「真的只有 ACTIVE 被返回」「parent 过滤真的作用在 ext 上」属于 SQL 行为，只能在集成测试里验证：
 * 端口被 mock 之后，无论用例怎么调，返回的都是 mock 给的那批数据。
 */
@DisplayName("字典查询")
class DictQueryServiceTest {

    private static final String DICT_TYPE = "PROVINCE";

    private DictQuery dictQuery;
    private DictQueryService dictQueryService;

    @BeforeEach
    void setUp() {
        dictQuery = mock(DictQuery.class);
        dictQueryService = new DictQueryService(dictQuery);
    }

    @Test
    @DisplayName("下拉语义：enabledOnly=true 时只按启用项过滤")
    void shouldFilterActiveOnlyWhenEnabledOnly() {
        given(dictQuery.existsType(DICT_TYPE)).willReturn(true);
        given(dictQuery.findItems(DICT_TYPE, DictStatus.ACTIVE, null)).willReturn(List.of(item(1L, "US-CA")));

        List<DictItem> items = dictQueryService.items(DICT_TYPE, true, null);

        verify(dictQuery).findItems(DICT_TYPE, DictStatus.ACTIVE, null);
        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("历史回显语义：enabledOnly=false 时不限状态，停用项一并返回")
    void shouldNotFilterStatusWhenEnabledOnlyIsFalse() {
        given(dictQuery.existsType(DICT_TYPE)).willReturn(true);
        given(dictQuery.findItems(DICT_TYPE, null, null))
                .willReturn(List.of(item(1L, "US-CA"), disabledItem(2L, "US-XX")));

        List<DictItem> items = dictQueryService.items(DICT_TYPE, false, null);

        // 状态筛选交给端口：传 null 表示不限状态，而不是传一个「全部」的枚举值
        verify(dictQuery).findItems(DICT_TYPE, null, null);
        assertThat(items).extracting(DictItem::status).contains(DictStatus.DISABLED);
    }

    @Test
    @DisplayName("层级：parentValue 原样下传给端口，全空白按不筛选处理")
    void shouldPassParentValueDownAndTreatBlankAsNoFilter() {
        given(dictQuery.existsType(DICT_TYPE)).willReturn(true);
        given(dictQuery.findItems(DICT_TYPE, DictStatus.ACTIVE, "US")).willReturn(List.of(item(1L, "US-CA")));
        given(dictQuery.findItems(DICT_TYPE, DictStatus.ACTIVE, null)).willReturn(List.of());

        dictQueryService.items(DICT_TYPE, true, " US ");
        dictQueryService.items(DICT_TYPE, true, "   ");

        // 不裁剪会变成 ext ->> 'parent' = '   '，匹配不到任何项，而调用方的本意是不筛选
        verify(dictQuery).findItems(DICT_TYPE, DictStatus.ACTIVE, "US");
        verify(dictQuery).findItems(DICT_TYPE, DictStatus.ACTIVE, null);
    }

    @Test
    @DisplayName("类型不存在：按 404 处理，且不再去查项")
    void shouldRejectMissingDictType() {
        given(dictQuery.existsType("NOPE")).willReturn(false);

        assertThatThrownBy(() -> dictQueryService.items("NOPE", true, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        // 类型不存在时不再去查项：查了也只能得到空列表，而空列表会被前端当成「这一类没配项」
        verify(dictQuery, never()).findItems(anyString(), any(), any());
    }

    @Test
    @DisplayName("双语：labelEn 为 null 时用例原样返回，不回退到 label")
    void shouldNotFallbackLabelEn() {
        given(dictQuery.existsType(DICT_TYPE)).willReturn(true);
        given(dictQuery.findItems(DICT_TYPE, DictStatus.ACTIVE, null)).willReturn(List.of(
                new DictItem(3L, DICT_TYPE, "某州", null, "US-XX", 60, DictStatus.ACTIVE, Map.of())));

        List<DictItem> items = dictQueryService.items(DICT_TYPE, true, null);

        // 回退属于展示层：用例补上 label 之后，前端再也分不清「缺英文名」与「英文名等于中文名」
        assertThat(items.get(0).labelEn()).isNull();
        assertThat(items.get(0).label()).isEqualTo("某州");
    }

    @Test
    @DisplayName("类型清单：原样返回，项数由端口一次取回")
    void shouldReturnAllTypes() {
        given(dictQuery.findAllTypes()).willReturn(List.of(
                new DictTypeSummary("COUNTRY", "国家", "Country", 28L, DictStatus.ACTIVE)));

        List<DictTypeSummary> types = dictQueryService.listTypes();

        assertThat(types).extracting(DictTypeSummary::dictType).containsExactly("COUNTRY");
        assertThat(types.get(0).itemCount()).isEqualTo(28L);
    }

    @Test
    @DisplayName("批量：不存在的类型静默跳过，且只发一次查询")
    void shouldSkipMissingTypesAndQueryOnce() {
        // 端口只返回 TEMP_ZONE 的项：OWNER_TYPE 相当于「已被管理员删掉」
        given(dictQuery.findItemsByTypes(anyList(), eq(DictStatus.ACTIVE)))
                .willReturn(List.of(tempZoneItem()));

        Map<String, List<DictItem>> result = dictQueryService.batch(List.of("OWNER_TYPE", "TEMP_ZONE"), true);

        assertThat(result).containsOnlyKeys("TEMP_ZONE");
        assertThat(result.get("TEMP_ZONE")).extracting(DictItem::value).containsExactly("FROZEN");
        // 一次查询取回多类型：按类型循环会让请求里的类型数直接变成 SQL 条数
        verify(dictQuery).findItemsByTypes(List.of("OWNER_TYPE", "TEMP_ZONE"), DictStatus.ACTIVE);
    }

    @Test
    @DisplayName("批量：类型编码去掉空白、去重，空项被丢弃")
    void shouldNormalizeRequestedTypes() {
        given(dictQuery.findItemsByTypes(anyList(), eq(DictStatus.ACTIVE))).willReturn(List.of());

        dictQueryService.batch(List.of(" OWNER_TYPE ", "OWNER_TYPE", "", "  ", "TEMP_ZONE"), true);

        verify(dictQuery).findItemsByTypes(List.of("OWNER_TYPE", "TEMP_ZONE"), DictStatus.ACTIVE);
    }

    @Test
    @DisplayName("批量：没有有效类型时返回空 Map 且不查库")
    void shouldReturnEmptyMapWithoutQueryWhenNoTypes() {
        assertThat(dictQueryService.batch(null, true)).isEmpty();
        assertThat(dictQueryService.batch(List.of("  ", ""), true)).isEmpty();

        // 空的 IN 条件在拼装时会被省略，查询会退化成「查全部字典项」
        verifyNoInteractions(dictQuery);
    }

    private static DictItem item(long id, String value) {
        return new DictItem(id, DICT_TYPE, "加利福尼亚州", "California", value, 50,
                DictStatus.ACTIVE, Map.of("parent", "US"));
    }

    private static DictItem disabledItem(long id, String value) {
        return new DictItem(id, DICT_TYPE, "已停用州", null, value, 60,
                DictStatus.DISABLED, Map.of("parent", "US"));
    }

    private static DictItem tempZoneItem() {
        return new DictItem(9L, "TEMP_ZONE", "冷冻", "Frozen", "FROZEN", 30,
                DictStatus.ACTIVE, Map.of());
    }
}
