package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 货主数据范围过滤用例的共用基类：在 {@link AbstractDataScopeIntegrationTest} 的造数与登录之上，
 * 补上被过滤的三类读接口（列表、详情、终止影响清单）的调用与断言取值。
 *
 * <p>为什么单独一层：这些 helper 只有过滤用例用得上（配置用例走的是角色与用户端点），
 * 塞进公共基类会让配置侧读到一个与它无关的接口契约，也把基类推过 300 行。
 *
 * <p>{@code list} 与详情都断言业务码为 0：请求本身失败时要与「范围过滤掉」区分开，
 * 否则 404 到底来自过滤还是来自参数错误，看断言分不出来。
 */
abstract class AbstractOwnerDataScopeIntegrationTest extends AbstractDataScopeIntegrationTest {

    /**
     * 取一页货主列表。
     *
     * @param token   访问令牌
     * @param keyword 关键字，用于把结果限定在本用例造的数据上
     * @return 响应里的 {@code data} 节点
     * @throws Exception 请求构造或响应解析失败
     */
    protected JsonNode list(String token, String keyword) throws Exception {
        return list(token, keyword, 1, 200);
    }

    /**
     * 取一页货主列表。
     *
     * @param token    访问令牌
     * @param keyword  关键字
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @return 响应里的 {@code data} 节点
     * @throws Exception 请求构造或响应解析失败
     */
    protected JsonNode list(String token, String keyword, int pageNo, int pageSize) throws Exception {
        JsonNode response = getJson(token,
                "/api/console/owners?keyword=" + keyword + "&pageNo=" + pageNo + "&pageSize=" + pageSize);
        assertThat(response.path("code").asInt()).as("列表查询失败：%s", response).isZero();
        return response.path("data");
    }

    /**
     * 取货主详情的业务码。
     *
     * @param token   访问令牌
     * @param ownerId 货主标识
     * @return 业务码；0 表示成功
     * @throws Exception 请求构造或响应解析失败
     */
    protected int ownerDetailCode(String token, long ownerId) throws Exception {
        return getJson(token, "/api/console/owners/" + ownerId).path("code").asInt();
    }

    /**
     * 取终止影响清单的业务码。
     *
     * @param token   访问令牌
     * @param ownerId 货主标识
     * @return 业务码；0 表示成功
     * @throws Exception 请求构造或响应解析失败
     */
    protected int terminationImpactCode(String token, long ownerId) throws Exception {
        return getJson(token, "/api/console/owners/" + ownerId + "/termination-impact").path("code").asInt();
    }

    /**
     * 取分页响应里的总数。
     *
     * @param page 列表响应里的 {@code data} 节点
     * @return 总数
     */
    protected static int total(JsonNode page) {
        return page.path("total").asInt();
    }

    /**
     * 取本页的货主编码。
     *
     * @param page 列表响应里的 {@code data} 节点
     * @return 编码列表
     */
    protected static List<String> codes(JsonNode page) {
        List<String> codes = new ArrayList<>();
        page.path("records").forEach(row -> codes.add(row.path("code").asText()));
        return codes;
    }
}
