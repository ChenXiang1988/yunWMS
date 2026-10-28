package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 货主负责客服的端到端验证：{@code /api/console/owners/{ownerId}/assignees} 的读、写两个端点。
 *
 * <p>这一批要证的是单测证不了的几件事：
 * <ul>
 *   <li><b>「主负责人唯一」在真实库上成立</b>——{@code ux_owner_assignee_role} 建在
 *       {@code (owner_id, relation_type)} 上，转交是「删旧主 + 插新主」；只有真 PostgreSQL 能证明
 *       这样写完之后该角色确实只剩一行。单测能证明调用顺序，证明不了落库结果；</li>
 *   <li><b>全量替换的落库结果</b>——一次提交同时给出主、副两个角色的目标值，副传 {@code null}
 *       与整段省略都要把旧的那一段关系删掉，而不是只改主负责人；</li>
 *   <li><b>主副都计入 {@code ASSIGNED} 数据范围</b>（规格的验收口径）——这条链路跨
 *       {@code wms-master}（关系）→ {@code wms-app}（适配器）→ {@code wms-auth}（范围推导 → SQL 追加条件），
 *       只有真实调用链能证明副负责人也进了 {@code owner_assignee} 的查询结果；</li>
 *   <li><b>审计真的落进了 {@code sys_audit_log}</b>——前后快照经 {@code jsonb} 往返后键仍是
 *       {@code primaryUserId} / {@code secondaryUserId}，值是数值而不是被序列化成对象；</li>
 *   <li><b>可见性随货主</b>——数据范围外的货主，读回 40400、写也回 40400，而不是「两个 null」
 *       或「改成功了」。负责人是货主的子资源，没有独立的可见范围；</li>
 *   <li><b>停用员工前必须已转交</b>（规格的验收口径）——停用仍担任负责人的员工要被 40900 拒掉且提示带数量，
 *       把负责人转交出去之后，同一次运行里同一名员工就能停用。这条链路与数据范围那条同构：关系在
 *       {@code wms-master}、判定在 {@code wms-auth}，只把关系写进表而不接上校验，上面几条照样全绿。</li>
 * </ul>
 *
 * <p><b>为什么继承 {@link AbstractOwnerDataScopeIntegrationTest} 而不是更上层的
 * {@link AbstractDataScopeIntegrationTest}：</b>本类既要用后者的造数与登录（货主、员工、角色、
 * 数据范围夹具），也要用前者已经封装的货主列表与详情调用。中间这一层正是为「过滤用例」建的两类读接口
 * helper，另写一份等于把同一个接口契约写成两处，将来路径一改就会有一份静默失准。
 *
 * <p><b>操作人不用种子账号 {@code superadmin}</b>（它带着「需先改密」标记，改它又会污染
 * {@code BootstrapIntegrationTest} 依赖的初始状态），而是用 SQL 造一个挂着内置超管角色的已改密账号：
 * 超管权限在鉴权时动态展开，因此它天然拥有 {@code system:owner:list}、{@code system:owner:update}
 * 与 {@code system:audit:list}。
 *
 * <p><b>货主一律用基类的 {@code seedOwner} 直接写库造：</b>建档本身不是本类的被测对象，用它准备数据会让
 * 「建档接口坏了」表现为一堆负责客服用例失败。
 *
 * <p>用例之间不隔离数据（同一个测试 JVM 共用一个库），因此每个用例用自己的货主编码、SKU 前缀、
 * 员工登录名与角色编码；断言只针对自己造的那几条。SKU 前缀避开 {@code 0}/{@code O}/{@code 1}/{@code I}
 * 且以 {@code RA} 开头——把业务键直接照抄进前缀会被 {@code ck_owner_sku_prefix} 当场拒掉，
 * 而那种失败看起来像「造数坏了」而不是「前缀不合规」。
 */
@DisplayName("货主负责客服")
class OwnerAssigneeIntegrationTest extends AbstractOwnerDataScopeIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "owner-assignee-operator";

    /** 数据范围限定到指定货主的员工，用于验证「范围外的货主读写都得 40400」。 */
    private static final String SCOPED_LOGIN_NAME = "owner-assignee-scoped";

    /** 范围角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String SCOPED_ROLE = "TEST_OWNER_ASSIGNEE_SCOPE";

    @BeforeEach
    void prepareAccounts() {
        link(seedStaff(OPERATOR_LOGIN_NAME, "负责客服操作员"), superAdminRole());
    }

    @Test
    @DisplayName("指定主副：初始主副都为空，指定主负责人与副负责人后回显这两名员工")
    void shouldAssignPrimaryAndSecondary() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OA-BASIC", "负责客服甲", "RAA2", "ACTIVE");
        long primaryId = seedStaff("oa-basic-primary", "负责客服甲主");
        long secondaryId = seedStaff("oa-basic-secondary", "负责客服甲副");

        JsonNode initial = assigneesOf(token, ownerId);
        // 键必须在、值是 null：配置弹窗据此显示「未配置」并允许选择员工；
        // 字段整段缺失会让前端读到 undefined，与「没有负责人」是两种渲染分支
        assertThat(initial.has("primaryUserId")).as("主负责人字段必须以 null 返回，而不是省略：%s", initial).isTrue();
        assertThat(fieldText(initial, "primaryUserId")).as("新货主还没有主负责人：%s", initial).isNull();
        assertThat(fieldText(initial, "secondaryUserId")).as("新货主还没有副负责人：%s", initial).isNull();
        // 回带 ownerId 是乱序响应防护：配置弹窗随货主切换复用同一个组件
        assertThat(initial.path("ownerId").asText()).as("响应要回带被查询的货主").isEqualTo(String.valueOf(ownerId));

        JsonNode saved = saveAssignees(token, ownerId, String.valueOf(primaryId), String.valueOf(secondaryId));
        assertThat(saved.path("code").asInt()).as("配置负责客服失败：%s", saved).isZero();

        JsonNode after = assigneesOf(token, ownerId);
        assertThat(fieldText(after, "primaryUserId")).as("主负责人未落库：%s", after)
                .isEqualTo(String.valueOf(primaryId));
        assertThat(fieldText(after, "secondaryUserId")).as("副负责人未落库：%s", after)
                .isEqualTo(String.valueOf(secondaryId));
    }

    @Test
    @DisplayName("转交主负责人：旧主负责人不再占主或副任何一边")
    void shouldTransferPrimaryToAnotherStaff() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OA-TRANSFER", "负责客服乙", "RAB3", "ACTIVE");
        long oldPrimaryId = seedStaff("oa-transfer-old", "负责客服乙旧主");
        long newPrimaryId = seedStaff("oa-transfer-new", "负责客服乙新主");
        long secondaryId = seedStaff("oa-transfer-secondary", "负责客服乙副");

        // 前提：先有主=A、副=B，否则「A 消失了」可能只是本来就没指定过
        assertThat(saveAssignees(token, ownerId, String.valueOf(oldPrimaryId), String.valueOf(secondaryId))
                .path("code").asInt()).as("前置配置失败").isZero();

        JsonNode transferred = saveAssignees(token, ownerId, String.valueOf(newPrimaryId), String.valueOf(secondaryId));
        assertThat(transferred.path("code").asInt()).as("转交主负责人失败：%s", transferred).isZero();

        JsonNode after = assigneesOf(token, ownerId);
        assertThat(fieldText(after, "primaryUserId")).as("转交后主负责人必须是新提交的那位：%s", after)
                .isEqualTo(String.valueOf(newPrimaryId));
        assertThat(fieldText(after, "secondaryUserId")).as("这次提交保留了副负责人：%s", after)
                .isEqualTo(String.valueOf(secondaryId));
        // 旧主负责人不得以任何身份残留：唯一索引只管「每个角色至多一行」，拦不住他被挪到副的那一行。
        // 用一次 GET 判定，不查表——读接口就是调用方将来判断同一件事的方式
        assertThat(List.of(fieldText(after, "primaryUserId"), fieldText(after, "secondaryUserId")))
                .as("旧主负责人不得再占主或副任何一边：%s", after)
                .doesNotContain(String.valueOf(oldPrimaryId));
    }

    @Test
    @DisplayName("撤销副负责人：副传 null 与整段省略都会清掉该角色，主负责人换人不受影响")
    void shouldClearSecondaryWhenNotProvided() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OA-CLEAR", "负责客服丙", "RAC4", "ACTIVE");
        long firstPrimaryId = seedStaff("oa-clear-primary-1", "负责客服丙主一");
        long secondPrimaryId = seedStaff("oa-clear-primary-2", "负责客服丙主二");
        long secondaryId = seedStaff("oa-clear-secondary", "负责客服丙副");

        assertThat(saveAssignees(token, ownerId, String.valueOf(firstPrimaryId), String.valueOf(secondaryId))
                .path("code").asInt()).as("前置配置失败").isZero();
        assertThat(fieldText(assigneesOf(token, ownerId), "secondaryUserId"))
                .as("前置条件：副负责人已经设上了").isEqualTo(String.valueOf(secondaryId));

        // 显式传 null 是契约里「不设副」的写法；同时把主负责人换成另一名员工
        JsonNode cleared = saveAssignees(token, ownerId, String.valueOf(secondPrimaryId), null);
        assertThat(cleared.path("code").asInt()).as("撤销副负责人失败：%s", cleared).isZero();

        JsonNode afterClear = assigneesOf(token, ownerId);
        assertThat(fieldText(afterClear, "primaryUserId")).as("撤销副负责人不该动主负责人：%s", afterClear)
                .isEqualTo(String.valueOf(secondPrimaryId));
        assertThat(fieldText(afterClear, "secondaryUserId")).as("副负责人传 null 必须清掉该角色：%s", afterClear)
                .isNull();

        // 整段省略与显式 null 是同一件事：请求体是全量替换，「省略」不是「别动这一边」
        assertThat(saveAssignees(token, ownerId, String.valueOf(secondPrimaryId), String.valueOf(secondaryId))
                .path("code").asInt()).as("重新设定副负责人失败").isZero();
        JsonNode omitted = putJson(token, assigneesPath(ownerId),
                "{\"primaryUserId\":\"" + secondPrimaryId + "\"}");
        assertThat(omitted.path("code").asInt()).as("省略副负责人字段应当成功：%s", omitted).isZero();
        assertThat(fieldText(assigneesOf(token, ownerId), "secondaryUserId"))
                .as("省略字段与传 null 同义，副负责人必须被清掉")
                .isNull();
    }

    @Test
    @DisplayName("参数校验：主负责人缺失、只填空白、与副负责人同人，三种都返回 40000 且不落库")
    void shouldRejectMissingBlankOrDuplicatePrimary() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OA-INVALID", "负责客服丁", "RAD5", "ACTIVE");
        long staffId = seedStaff("oa-invalid-staff", "负责客服丁员工");
        String staffUserId = String.valueOf(staffId);

        // 主负责人整段缺失：@NotBlank 在入口挡下，不必先把它当成一次「撤销主负责人」再走一遍用例
        JsonNode missing = putJson(token, assigneesPath(ownerId), "{}");
        assertThat(missing.path("code").asInt()).as("主负责人缺失必须被拒：%s", missing)
                .isEqualTo(BAD_REQUEST_CODE);
        assertThat(missing.path("message").asText()).as("提示要指明是主负责人的问题").contains("主负责人");

        JsonNode blank = saveAssignees(token, ownerId, "   ", staffUserId);
        assertThat(blank.path("code").asInt()).as("主负责人只填空白必须被拒：%s", blank)
                .isEqualTo(BAD_REQUEST_CODE);

        // 唯一索引拦不住「同一个人各占一行」，因此这条只能由用例判
        JsonNode samePerson = saveAssignees(token, ownerId, staffUserId, staffUserId);
        assertThat(samePerson.path("code").asInt()).as("主副是同一名员工必须被拒：%s", samePerson)
                .isEqualTo(BAD_REQUEST_CODE);
        assertThat(samePerson.path("message").asText()).as("提示要说清是同一个人").contains("同一名员工");

        JsonNode after = assigneesOf(token, ownerId);
        assertThat(fieldText(after, "primaryUserId")).as("被拒的提交不得落库：%s", after).isNull();
        assertThat(fieldText(after, "secondaryUserId")).as("被拒的提交不得落库：%s", after).isNull();
    }

    @Test
    @DisplayName("参数校验：负责人不存在或已停用都返回 40000，提示点名是哪个角色")
    void shouldRejectUnknownOrDisabledStaff() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OA-STAFF", "负责客服戊", "RAE6", "ACTIVE");
        long activeStaffId = seedStaff("oa-staff-active", "负责客服戊在册");
        long disabledStaffId = seedStaff("oa-staff-disabled", "负责客服戊已停用");
        // 停用是长期状态，重新启用后这个人就能再被指定；因此本用例自己造号自己停用，不碰任何种子账号
        jdbcTemplate.update("UPDATE sys_user SET status = 'DISABLED' WHERE id = ?", disabledStaffId);
        // 取一个必然不存在的标识，而不是写死一个「目前还没用到」的小整数
        long unknownStaffId = jdbcTemplate.queryForObject(
                "SELECT coalesce(max(id), 0) + 1000000 FROM sys_user", Long.class);

        JsonNode unknown = saveAssignees(token, ownerId, String.valueOf(unknownStaffId), null);
        assertThat(unknown.path("code").asInt()).as("不存在的员工不能被指定：%s", unknown)
                .isEqualTo(BAD_REQUEST_CODE);
        // 「不存在」与「已停用」同口径拒绝：区分两者会对枚举他人账号留出多余信息
        assertThat(unknown.path("message").asText()).as("提示要说明此人不在册").contains("不是当前在册的启用员工");

        JsonNode disabled = saveAssignees(token, ownerId, String.valueOf(disabledStaffId), null);
        assertThat(disabled.path("code").asInt()).as("已停用的员工不能被指定为主负责人：%s", disabled)
                .isEqualTo(BAD_REQUEST_CODE);

        // 副负责人同样要判：只判主负责人时，一个已停用的人能从副的位置进来
        JsonNode disabledSecondary =
                saveAssignees(token, ownerId, String.valueOf(activeStaffId), String.valueOf(disabledStaffId));
        assertThat(disabledSecondary.path("code").asInt()).as("已停用的员工不能被指定为副负责人：%s", disabledSecondary)
                .isEqualTo(BAD_REQUEST_CODE);
        assertThat(disabledSecondary.path("message").asText()).as("提示要点名是副负责人的问题").contains("副负责人");

        JsonNode after = assigneesOf(token, ownerId);
        assertThat(fieldText(after, "primaryUserId")).as("被拒的提交不得落库：%s", after).isNull();
        assertThat(fieldText(after, "secondaryUserId")).as("被拒的提交不得落库：%s", after).isNull();
    }

    @Test
    @DisplayName("越权：数据范围外的货主，读负责客服与改负责客服都返回 40400")
    void shouldDenyOwnerOutsideDataScope() throws Exception {
        long visibleOwnerId = seedOwner("OWNER-OA-SCOPE-IN", "负责客服范围己", "RAF7", "ACTIVE");
        long hiddenOwnerId = seedOwner("OWNER-OA-SCOPE-OUT", "负责客服范围庚", "RAG8", "ACTIVE");
        long staffId = seedStaff(SCOPED_LOGIN_NAME, "负责客服范围员工");
        long candidateId = seedStaff("oa-scope-candidate", "负责客服范围候选");
        link(staffId, seedOwnerEditorRole(SCOPED_ROLE, "CUSTOM", visibleOwnerId));
        String token = loginToken(SCOPED_LOGIN_NAME);

        // 对照组：范围里的货主能正常读（空的配置）。没有这一步，下面的 40400 可能只是缺权限点或路径写错
        JsonNode visible = assigneesOf(token, visibleOwnerId);
        assertThat(visible.path("primaryUserId").isNull()).as("范围里的货主应当能读到（空的）配置：%s", visible).isTrue();

        JsonNode hidden = getJson(token, assigneesPath(hiddenOwnerId));
        assertThat(hidden.path("code").asInt()).as("范围外的货主与「不存在」同口径：%s", hidden)
                .isEqualTo(NOT_FOUND_CODE);
        // 两个 null 与「无权」是两件事：前者说这个货主还没配负责人，后者说你根本看不到这个货主
        assertThat(hidden.path("data").isNull()).as("无权时必须报错，不能回两个 null：%s", hidden).isTrue();

        JsonNode rejected = saveAssignees(token, hiddenOwnerId, String.valueOf(candidateId), null);
        assertThat(rejected.path("code").asInt()).as("写侧同样要挡住范围外的货主：%s", rejected)
                .isEqualTo(NOT_FOUND_CODE);

        // 换拥有全部范围的超管读一次：那次被拒的写入确实没有落到库里
        JsonNode untouched = assigneesOf(loginToken(OPERATOR_LOGIN_NAME), hiddenOwnerId);
        assertThat(fieldText(untouched, "primaryUserId")).as("被拒的写入不得落库：%s", untouched).isNull();
    }

    @Test
    @DisplayName("数据范围：主、副负责人都计入 ASSIGNED，指定货主的员工同样可见")
    void shouldIncludeBothAssigneeRolesInAssignedScope() throws Exception {
        String marker = "OA-ASSIGNED";
        String ownerCode = "OWNER-OA-ASSIGNED";
        long ownerId = seedOwner(ownerCode, "负责客服范围辛" + marker, "RAH9", "ACTIVE");
        long primaryId = seedStaff("oa-assigned-primary", "负责客服辛主");
        long secondaryId = seedStaff("oa-assigned-secondary", "负责客服辛副");
        long unassignedId = seedStaff("oa-assigned-none", "负责客服辛无关");
        long customStaffId = seedStaff("oa-assigned-custom", "负责客服辛指定");

        // 配置负责人的是超管；A、B 只负责这一个货主，各自持 ASSIGNED 范围
        JsonNode configured = saveAssignees(loginToken(OPERATOR_LOGIN_NAME), ownerId,
                String.valueOf(primaryId), String.valueOf(secondaryId));
        assertThat(configured.path("code").asInt()).as("配置负责客服失败：%s", configured).isZero();
        link(primaryId, seedScopedRole("OA_ROLE_ASSIGNED_PRIMARY", "ASSIGNED"));
        link(secondaryId, seedScopedRole("OA_ROLE_ASSIGNED_SECONDARY", "ASSIGNED"));
        link(unassignedId, seedScopedRole("OA_ROLE_ASSIGNED_NONE", "ASSIGNED"));
        link(customStaffId, seedScopedRole("OA_ROLE_CUSTOM", "CUSTOM", ownerId));
        String primaryToken = loginToken("oa-assigned-primary");
        String secondaryToken = loginToken("oa-assigned-secondary");
        String unassignedToken = loginToken("oa-assigned-none");
        String customToken = loginToken("oa-assigned-custom");

        assertThat(codes(list(primaryToken, marker))).as("主负责人必须看到自己负责的货主")
                .containsExactly(ownerCode);
        assertThat(ownerDetailCode(primaryToken, ownerId)).as("详情与列表同范围").isZero();

        // 规格的验收口径：主、副都计入客服数据范围。只推导 PRIMARY 时这里会红
        assertThat(codes(list(secondaryToken, marker))).as("副负责人同样要看到这个货主")
                .containsExactly(ownerCode);
        assertThat(ownerDetailCode(secondaryToken, ownerId)).as("副负责人的详情同样可读").isZero();

        // 对照组：同样持 ASSIGNED、但没有任何负责关系的人看不到。缺这一条，「看得见」可能只是
        // 「ASSIGNED 等于全部货主」，那样上面两条断言就不再有证明力
        assertThat(codes(list(unassignedToken, marker))).as("没有负责关系的员工不该看到这个货主").isEmpty();

        // CUSTOM 是另一条推导路径，三种身份因此都要能看见
        assertThat(codes(list(customToken, marker))).as("CUSTOM 明细里的员工同样可见").containsExactly(ownerCode);
    }

    @Test
    @DisplayName("审计：每次成功的配置各留一条记录，前后快照给出主副两个员工标识")
    void shouldRecordAssigneeChangeAudit() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        String ownerCode = "OWNER-OA-AUDIT";
        long ownerId = seedOwner(ownerCode, "负责客服审计壬", "RAJ2", "ACTIVE");
        long firstPrimaryId = seedStaff("oa-audit-primary-1", "负责客服审计主一");
        long secondPrimaryId = seedStaff("oa-audit-primary-2", "负责客服审计主二");
        long secondaryId = seedStaff("oa-audit-secondary", "负责客服审计副");

        assertThat(saveAssignees(token, ownerId, String.valueOf(firstPrimaryId), String.valueOf(secondaryId))
                .path("code").asInt()).as("首次配置失败").isZero();
        assertThat(saveAssignees(token, ownerId, String.valueOf(secondPrimaryId), String.valueOf(secondaryId))
                .path("code").asInt()).as("转交失败").isZero();

        JsonNode records = auditRecordsOf(token, ownerCode);
        assertThat(records.size()).as("两次成功的配置各留一条：%s", records).isEqualTo(2);

        for (JsonNode record : records) {
            // 事件 OWNER_ASSIGNEE_CHANGED 的落库形态：对象是货主、动作是 UPDATE、摘要取事件模板 + 货主编码
            assertThat(record.path("bizType").asText()).isEqualTo("OWNER");
            assertThat(record.path("bizId").asText()).isEqualTo(ownerCode);
            assertThat(record.path("action").asText()).isEqualTo("UPDATE");
            assertThat(record.path("summary").asText()).isEqualTo("调整负责客服 " + ownerCode);
            // 键始终存在、值可空：首次指定时两个角色都还没有人，那两个键仍要在
            assertThat(fieldNamesOf(record.path("beforeValue")))
                    .as("变更前快照的键固定为两个角色：%s", record)
                    .containsExactlyInAnyOrder("primaryUserId", "secondaryUserId");
            assertThat(fieldNamesOf(record.path("afterValue")))
                    .as("变更后快照的键固定为两个角色：%s", record)
                    .containsExactlyInAnyOrder("primaryUserId", "secondaryUserId");
        }

        // 倒序：最新的那条在最前。首次指定：变更前两个角色都没有人，变更后是这一次提交的值
        JsonNode initial = records.get(1);
        assertThat(initial.path("beforeValue").path("primaryUserId").isNull())
                .as("首次指定的变更前没有主负责人：%s", initial).isTrue();
        assertThat(initial.path("beforeValue").path("secondaryUserId").isNull())
                .as("首次指定的变更前没有副负责人：%s", initial).isTrue();
        assertThat(initial.path("afterValue").path("primaryUserId").asLong())
                .as("变更后记的是员工标识：%s", initial).isEqualTo(firstPrimaryId);
        assertThat(initial.path("afterValue").path("secondaryUserId").asLong()).isEqualTo(secondaryId);

        // 转交：变更前是上一份事实，变更后是这一份；副负责人没换，两份快照里都是它
        JsonNode transferred = records.get(0);
        assertThat(transferred.path("beforeValue").path("primaryUserId").asLong())
                .as("变更前应当读到我替换掉的那位：%s", transferred).isEqualTo(firstPrimaryId);
        assertThat(transferred.path("beforeValue").path("secondaryUserId").asLong()).isEqualTo(secondaryId);
        assertThat(transferred.path("afterValue").path("primaryUserId").asLong()).isEqualTo(secondPrimaryId);
        assertThat(transferred.path("afterValue").path("secondaryUserId").asLong()).isEqualTo(secondaryId);
    }

    @Test
    @DisplayName("停用校验：仍担任货主负责人的员工被 40900 拒掉且提示带数量，转交后即可停用")
    void shouldRejectDisablingStaffWhoStillOwnsAssignments() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OA-DISABLE", "负责客服停用癸", "RAK3", "ACTIVE");
        long responsibleId = seedStaff("oa-disable-responsible", "负责客服停用责任人");
        long successorId = seedStaff("oa-disable-successor", "负责客服停用接手人");

        // 前提：这名员工确实是主负责人，否则「被拒绝」可能只是校验根本没跑到他名下
        assertThat(saveAssignees(token, ownerId, String.valueOf(responsibleId), null).path("code").asInt())
                .as("前置配置失败").isZero();

        JsonNode rejected = changeUserStatus(token, responsibleId, "DISABLED");
        // 409 段位而不是 40000：停用请求本身合法，被拒绝的是「货主因此失去负责人」这个后果
        assertThat(rejected.path("code").asInt()).as("仍担任负责人的员工必须被拒绝停用：%s", rejected)
                .isEqualTo(CONFLICT_CODE);
        // 数量与下一步一起进提示：只说「名下有货主」的话，管理员仍要自己翻一遍才知道要转交几个
        assertThat(rejected.path("message").asText()).as("提示要说清还有几个货主、下一步做什么：%s", rejected)
                .contains("仍担任 1 个货主的负责人", "请先转交");
        // 被拒的那次不得落库：停用同时会踢掉会话，落了库就退不回来
        assertThat(statusInDb(responsibleId)).as("被拒绝的停用不得落库").isEqualTo("ACTIVE");

        // 转交：主负责人换成另一名员工，原责任人不再占主或副任何一边
        assertThat(saveAssignees(token, ownerId, String.valueOf(successorId), null).path("code").asInt())
                .as("转交失败").isZero();

        JsonNode allowed = changeUserStatus(token, responsibleId, "DISABLED");
        assertThat(allowed.path("code").asInt()).as("转交后同一员工应当可以停用：%s", allowed).isZero();
        // 放行的那次必须真的写进库：只判响应码时，「没报错但也没停用」会漏过去
        assertThat(statusInDb(responsibleId)).as("放行后状态要真的落库").isEqualTo("DISABLED");
    }

    /**
     * 负责客服子资源的路径。
     *
     * <p>抽出来是因为九个用例都要拼同一个前缀：路径写错一次会让 40400 出现在不该出现的用例里，
     * 而那种失败看起来像业务 bug。
     *
     * @param ownerId 货主标识
     * @return 该货主负责客服的路径
     */
    private static String assigneesPath(long ownerId) {
        return "/api/console/owners/" + ownerId + "/assignees";
    }

    /**
     * 把主、副负责人一次写到目标状态，返回原始响应。
     *
     * <p>不在这里断言业务码：被拒的用例要的正是 4xxxx，由调用方按场景断言。
     *
     * @param token           操作人令牌
     * @param ownerId         货主标识，取自路径
     * @param primaryUserId   主负责人标识，字符串形式的十进制整数
     * @param secondaryUserId 副负责人标识；{@code null} 表示不设副负责人
     * @return 接口响应
     * @throws Exception 请求构造或执行失败
     */
    private JsonNode saveAssignees(String token, long ownerId, String primaryUserId, String secondaryUserId)
            throws Exception {
        return putJson(token, assigneesPath(ownerId), objectMapper.writeValueAsString(
                new AssigneePayload(primaryUserId, secondaryUserId)));
    }

    /**
     * 取某货主当前的负责客服配置，并断言请求本身成功。
     *
     * <p>只把「请求失败」判为失败，两个角色为空是合法结果，由调用方按场景断言。
     *
     * @param token   访问令牌
     * @param ownerId 货主标识
     * @return 响应里的 {@code data} 节点
     * @throws Exception 请求构造或响应解析失败
     */
    private JsonNode assigneesOf(String token, long ownerId) throws Exception {
        JsonNode response = getJson(token, assigneesPath(ownerId));
        assertThat(response.path("code").asInt()).as("查询负责客服失败：%s", response).isZero();
        return response.path("data");
    }

    /**
     * 取响应里的一个标识字段，JSON {@code null} 与字段缺失都转成 Java {@code null}。
     *
     * <p>不用 {@code asText()}：它对 JSON {@code null} 返回字符串 {@code "null"}，
     * 断言里就分不清「这个角色没有人」与「负责人标识是一段文本 null」。
     *
     * @param data  响应里的 {@code data} 节点或快照节点
     * @param field 字段名
     * @return 字段的文本值；JSON {@code null} 或字段缺失时为 {@code null}
     */
    private static String fieldText(JsonNode data, String field) {
        JsonNode node = data.path(field);
        return node.isNull() || node.isMissingNode() ? null : node.asText();
    }

    /**
     * 列出 JSON 对象的顶层字段名。
     *
     * @param node JSON 节点
     * @return 字段名，保持响应里的顺序；不是对象时为空
     */
    private static List<String> fieldNamesOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /**
     * 按货主编码追溯审计记录，并断言请求本身成功。
     *
     * <p>只查 {@code bizType=OWNER&bizId=<货主编码>}：负责人日志挂在货主编码下，
     * 从货主详情追溯时要与建档、改状态排在同一条链上。
     *
     * @param token     操作人令牌，需持有 {@code system:audit:list}
     * @param ownerCode 货主系统编码
     * @return 记录数组，按时间倒序
     * @throws Exception 请求构造或响应解析失败
     */
    private JsonNode auditRecordsOf(String token, String ownerCode) throws Exception {
        JsonNode page = getJson(token, "/api/console/audit-logs?bizType=OWNER&bizId=" + ownerCode);
        assertThat(page.path("code").asInt()).as("查询审计日志失败：%s", page).isZero();
        return page.path("data").path("records");
    }

    /**
     * 启用或停用某个账号，返回原始响应。
     *
     * <p>不在这里断言业务码：被拒的用例要的正是 40900，由调用方按场景断言。
     *
     * @param token  操作人令牌，需持有 {@code system:user:update}
     * @param userId 目标账号标识
     * @param status 目标状态，取 {@code ACTIVE} 或 {@code DISABLED}
     * @return 接口响应
     * @throws Exception 请求构造或执行失败
     */
    private JsonNode changeUserStatus(String token, long userId, String status) throws Exception {
        return putJson(token, "/api/console/users/" + userId + "/status",
                objectMapper.writeValueAsString(new StatusPayload(status)));
    }

    /**
     * 直接读库里的账号状态。
     *
     * <p>用来判定「被拒绝的那次没有落库」与「放行的那次真的落库」：停用成功时响应不带数据，
     * 而用户详情接口要求 {@code system:user:list}——本用例关心的是状态本身，不该再借一个与它
     * 无关的读接口来间接推断。
     *
     * @param userId 账号标识
     * @return 库里的状态编码
     */
    private String statusInDb(long userId) {
        return jdbcTemplate.queryForObject("SELECT status FROM sys_user WHERE id = ?", String.class, userId);
    }

    /**
     * 建一个既持 {@code system:owner:list} 又持 {@code system:owner:update} 的角色。
     *
     * <p>越权用例要同时证读侧与写侧，而基类的 {@link #seedScopedRole} 只授列表权限点：
     * 缺编辑点时写侧会先拿到 403，测到的就不是数据范围了。范围、状态与列表权限点仍交给它设置，
     * 这里只补一个授权，避免把同一套范围写入复制成两份。
     *
     * @param code      角色编码
     * @param scopeType 数据范围类型
     * @param ownerIds  {@code CUSTOM} 时的货主明细
     * @return 角色标识
     */
    private long seedOwnerEditorRole(String code, String scopeType, long... ownerIds) {
        long roleId = seedScopedRole(code, scopeType, ownerIds);
        jdbcTemplate.update("""
                INSERT INTO sys_role_menu (role_id, menu_id) SELECT ?, id FROM sys_menu
                WHERE perms = 'system:owner:update' ON CONFLICT (role_id, menu_id) DO NOTHING
                """, roleId);
        return roleId;
    }

    /**
     * 负责客服写入请求体的最小映射。
     *
     * <p>不复用生产侧的 {@code OwnerAssigneeSaveRequest}：同一个类型序列化再反序列化，
     * 会掩盖字段名被改而测试仍然通过的情况。
     *
     * <p>它<b>表达不了「字段整段省略」</b>——{@code null} 会序列化成显式的 {@code null}。
     * 需要验证「省略」的那一步因此手写 JSON 字面量。
     *
     * @param primaryUserId   主负责人标识
     * @param secondaryUserId 副负责人标识；{@code null} 序列化为显式 null
     */
    private record AssigneePayload(String primaryUserId, String secondaryUserId) {
    }

    /**
     * 启停请求体的最小映射，按 HTTP 上的 JSON 字段名验证契约。
     *
     * <p>与 {@link AssigneePayload} 同一理由不复用生产侧的请求类型：同一个类序列化再反序列化，
     * 会掩盖字段名被改而测试仍然通过的情况。
     *
     * @param status 目标状态
     */
    private record StatusPayload(String status) {
    }
}
