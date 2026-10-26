package com.yunwms.master.interfaces.controller.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 建档请求体的契约钉：标识与落库状态不能由调用方指定。
 *
 * <p>为什么值得单独钉一条：{@code FAIL_ON_UNKNOWN_PROPERTIES} 只拦「请求里多传字段」，
 * 它拦不住「有人往请求体里加了一个字段」——加了之后多传就不再是多余字段，而是正常入参。
 * 一旦 {@code code} 出现在这里，调用方就能选一个编码，与「编码由编码规则生成」直接冲突；
 * 一旦 {@code status} 出现，建档就绕开了「只允许草稿与直接提交」这两条入口。
 * 用「字段清单全等」而不是「不含 code」，是为了让新增字段也必须过这一关。
 */
@DisplayName("建档请求体")
class OwnerCreateRequestTest {

    @Test
    @DisplayName("只接收档案字段与 initialStatus，不接受 code 与 status")
    void shouldOnlyExposeCreationInputs() {
        List<String> components = Arrays.stream(OwnerCreateRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components)
                .as("建档请求体多出的字段都会被当成入参；code 必须由系统生成，status 必须由 initialStatus 决定")
                .containsExactly(
                        "name", "nameEn", "shortName", "externalCode", "skuPrefix",
                        "ownerType", "settlementEntity",
                        "country", "province", "city", "district", "postalCode", "addressLine",
                        "remark", "initialStatus");
    }
}
