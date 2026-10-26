package com.yunwms.master.interfaces.controller.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 编辑请求体的契约钉：四个不可改的字段不能出现在请求体里。
 *
 * <p>为什么值得单独钉一条：这四个字段各自的失效后果都不一样，但都不报错——
 * {@code code} 出现在这里等于允许改标识（改了就不是同一个货主，历史单据全对不上）；
 * {@code skuPrefix} 出现在这里等于允许改已生成 SKUID 的前四位（已打印的标签指向另一个货主）；
 * {@code status} 与 {@code initialStatus} 出现在这里等于给状态机开了一个绕过流转规则的后门。
 * 而 {@code FAIL_ON_UNKNOWN_PROPERTIES} 只拦「请求里多传字段」，拦不住「有人往请求体里加字段」。
 */
@DisplayName("编辑请求体")
class OwnerUpdateRequestTest {

    @Test
    @DisplayName("只接收可编辑的档案字段，不含 code / skuPrefix / status / initialStatus")
    void shouldOnlyExposeEditableInputs() {
        List<String> components = Arrays.stream(OwnerUpdateRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components)
                .as("编辑接口的字段清单就是「可编辑范围」本身，多一个字段就是多一个绕过锁定与状态机的入口")
                .containsExactly(
                        "name", "nameEn", "shortName", "externalCode",
                        "ownerType", "settlementEntity",
                        "country", "province", "city", "district", "postalCode", "addressLine",
                        "remark")
                .doesNotContain("code", "skuPrefix", "status", "initialStatus", "id", "ownerId");
    }
}
