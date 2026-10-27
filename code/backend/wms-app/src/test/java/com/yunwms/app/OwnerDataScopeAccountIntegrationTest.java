package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 货主账号的货主数据范围：只可见所属货主，且这条隔离与角色无关。
 *
 * <p>为什么单独成类：员工侧的范围由「角色并集 + 用户覆盖」推导，货主账号则被强制收敛到
 * {@code sys_user.owner_id} 指向的那一个货主，两条规则由不同代码路径实现。混在一个类里时，
 * 「货主账号看得少」很容易被误读成角色配置生效，而不是强制隔离生效。
 *
 * <p>用例刻意给货主账号挂上内置超管角色（范围 {@code ALL}）：如果隔离依赖的是「它恰好没有角色」，
 * 这条用例会红。列表之外的详情与终止影响清单也要一起挡住——只挡列表时，知道标识的人仍能借
 * 计数探测「这个标识对应一条真实货主」。
 *
 * <p>用例之间不隔离数据（整个测试 JVM 共用一个库），因此本用例用自己的货主编码、SKU 前缀、
 * 登录名与关键字标记。
 */
@DisplayName("货主账号数据范围隔离")
class OwnerDataScopeAccountIntegrationTest extends AbstractOwnerDataScopeIntegrationTest {

    @Test
    @DisplayName("货主账号：只可见所属货主，即使角色是 ALL 也读不到他方详情")
    void shouldIsolateOwnerAccountEvenWithAllScopeRole() throws Exception {
        String marker = "FLT-OWNER";
        long ownerA = seedOwner("OWNER-FLT-OWNER-A", "货主账号甲" + marker, "FQA3", "ACTIVE");
        long ownerB = seedOwner("OWNER-FLT-OWNER-B", "货主账号乙" + marker, "FQB4", "ACTIVE");
        long ownerAccountId = seedOwnerAccount("flt-owner-account", "货主账号甲", ownerA);
        // 角色给 ALL：这是刻意的——货主账号的隔离不能依赖「它恰好没有角色」
        link(ownerAccountId, superAdminRole());

        String token = loginToken("flt-owner-account");
        JsonNode page = list(token, marker);

        assertThat(codes(page)).as("货主账号只能看自己那一个货主").containsExactly("OWNER-FLT-OWNER-A");
        assertThat(total(page)).isEqualTo(1);
        assertThat(ownerDetailCode(token, ownerA)).isZero();
        assertThat(ownerDetailCode(token, ownerB)).isEqualTo(NOT_FOUND_CODE);
        assertThat(terminationImpactCode(token, ownerB)).isEqualTo(NOT_FOUND_CODE);
    }
}
