package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 货主税号的端到端验证：{@code /api/console/owners/{ownerId}/taxes} 的四个端点。
 *
 * <p>要证的是单测证不了的几件事：
 * <ul>
 *   <li><b>两条部分唯一索引在真实库上成立</b>——{@code ux_owner_tax_unique} 的键里带
 *       {@code COALESCE(province, '')}、{@code ux_owner_tax_default} 带 {@code is_default} 条件，
 *       都是 PostgreSQL 专有语义：只有真库会拒绝「同货主同国家同州同类型」的第二行，
 *       也只有真库能证明「先清旧默认、再写新默认」之后该国只剩一条默认；</li>
 *   <li><b>默认切换的落库结果</b>——接口先 {@code clearDefault} 再插入，单测只能证明调用顺序，
 *       证明不了旧的那行真的变成非默认；因此除了列表，还直接数一次库里的默认行数；</li>
 *   <li><b>四条业务规则的拒绝口径</b>——类型与国家匹配（{@code TAX_ID_TYPE} 字典的适用清单）、
 *       州销售税号必须有州省、州省必须属于所选国家、统一社会信用代码 18 位大写字母数字，
 *       四条都跨「字典查询 → 用例判定 → 全局异常处理」，只有真实调用链能证明最终返回的是 40000
 *       且不落库；</li>
 *   <li><b>软删除与「不存在」同口径</b>——删掉的行列表里消失，再编辑或删除它回 40400，
 *       而不是「改成功了」；</li>
 *   <li><b>可见性随货主</b>——数据范围外的货主，列表与新增都回 40400，而不是空列表或写成功。
 *       税号没有独立的可见范围，它随货主；</li>
 *   <li><b>审核留痕挂在货主编码下</b>——三个写入口共用一个事件，对象类型沿用 {@code OWNER}，
 *       从货主详情追溯时这些记录必须出现（见 {@code OwnerAuditEvent#OWNER_TAX_CHANGED}）。</li>
 * </ul>
 *
 * <p><b>操作人不用种子账号 {@code superadmin}</b>（它带着「需先改密」标记，改它又会污染
 * {@code BootstrapIntegrationTest} 依赖的初始状态），而是用 SQL 造一个挂着内置超管角色的已改密账号：
 * 超管权限在鉴权时动态展开，因此它天然拥有 {@code V033} 的 {@code system:owner:list}、
 * {@code V042} 的 {@code system:owner:tax:edit} 与 {@code system:audit:list}。
 *
 * <p><b>货主一律用基类的 {@code seedOwner} 直接写库造：</b>建档本身不是本类的被测对象，
 * 用它准备数据会让「建档接口坏了」表现为一堆税号用例失败。
 *
 * <p><b>用例之间不隔离数据</b>（同一个测试 JVM 共用一个库），因此每个用例用自己的货主编码与 SKU 前缀，
 * 断言只针对自己造的那几条。SKU 前缀避开 {@code 0}/{@code O}/{@code 1}/{@code I} 且以 {@code TX} 开头：
 * 把业务键直接照抄进前缀会被 {@code ck_owner_sku_prefix} 当场拒掉（{@code [A-Z0-9]{4}} 虽然放行，
 * 但种子数据要能被 {@code SkuPrefix} 读回），而那种失败看起来像「造数坏了」而不是「前缀不合规」。
 *
 * <p><b>「货主端接口不返回税号」不在本类的覆盖范围内：</b>全仓没有任何 {@code /api/portal} 端点
 * （货主端档案接口尚未实现），这条验收没有可被调用的入口，硬写一个「访问某路径得到 404」的用例
 * 只会把「接口不存在」固化成断言。它应随货主端档案接口一起验证，见交付说明。
 */
@DisplayName("货主税号")
class OwnerTaxIntegrationTest extends AbstractDataScopeIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "owner-tax-operator";

    /** 数据范围限定到指定货主的员工，用于验证「范围外的货主读写都得 40400」。 */
    private static final String SCOPED_LOGIN_NAME = "owner-tax-scoped";

    /** 范围角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String SCOPED_ROLE = "TEST_OWNER_TAX_SCOPE";

    /** 合法的统一社会信用代码样例：18 位大写字母与数字。 */
    private static final String USCC = "91310115MA1K35X2X8";

    /** 同一个货主编辑后要换成的另一个合法号码，只改最后一位。 */
    private static final String USCC_EDITED = "91310115MA1K35X2X9";

    @BeforeEach
    void prepareAccounts() {
        link(seedStaff(OPERATOR_LOGIN_NAME, "税号操作员"), superAdminRole());
    }

    @Test
    @DisplayName("全链路：为中国货主新增 18 位统一社会信用代码并设为默认，列表只有一条且字段回带正确")
    void shouldCreateUsccAndReturnItInList() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OT-CHAIN", "税号全链路甲", "TXA2", "ACTIVE");

        JsonNode created = createTax(token, ownerId, new TaxPayload("CN", null, "CN_USCC", USCC, true));
        assertThat(created.path("code").asInt()).as("新增税号失败：%s", created).isZero();
        String taxId = created.path("data").path("id").asText();
        // 新增回带整行：前端据此直接渲染这一行，不必再取一次列表
        assertThat(created.path("data").path("country").asText()).isEqualTo("CN");
        assertThat(created.path("data").path("taxType").asText()).isEqualTo("CN_USCC");
        assertThat(created.path("data").path("taxNumber").asText()).isEqualTo(USCC);
        assertThat(created.path("data").path("isDefault").asBoolean()).isTrue();
        // 键在、值为 null：国家级税号本就没有州省，与「字段缺失」是两件事
        assertThat(created.path("data").path("province").isNull()).as("没有州省时必须回 null：%s", created).isTrue();

        JsonNode taxes = taxesOf(token, ownerId);
        assertThat(taxes.size()).as("只提交了一条：%s", taxes).isEqualTo(1);
        JsonNode row = taxes.get(0);
        assertThat(row.path("id").asText()).as("列表里的标识就是新增回带的那个").isEqualTo(taxId);
        assertThat(row.path("country").asText()).isEqualTo("CN");
        assertThat(row.path("taxType").asText()).isEqualTo("CN_USCC");
        assertThat(row.path("taxNumber").asText()).isEqualTo(USCC);
        assertThat(row.path("isDefault").asBoolean()).as("提交 true 就必须落成 true：%s", row).isTrue();
        assertThat(row.path("province").isNull()).as("州省保持未填：%s", row).isTrue();
    }

    @Test
    @DisplayName("类型与国家不匹配：中国货主在国家 CN 下登记仅适用 US 的 EIN，返回 40000 且不落库")
    void shouldRejectTaxTypeNotApplicableToCountry() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        // 业务键不超过 owner.code 的 varchar(20)：超长会在造数时以 22001 失败，看起来像用例坏了
        long ownerId = seedOwner("OWNER-OT-TYPE", "税号类型乙", "TXB3", "ACTIVE");

        // 判的是提交的国家与类型能否配上，不是货主注册地：CN 名下不可能有美国的雇主识别号
        JsonNode rejected = createTax(token, ownerId, new TaxPayload("CN", null, "US_EIN", "12-3456789", false));

        assertThat(rejected.path("code").asInt())
                .as("类型不适用于所填国家必须被拒：%s", rejected)
                .isEqualTo(BAD_REQUEST_CODE);
        // 带出双方原文：只说「不匹配」时，操作人不知道该换类型还是换国家
        assertThat(rejected.path("message").asText()).contains("US_EIN", "CN", "不匹配");
        assertThat(taxesOf(token, ownerId)).as("被拒绝的新增不得落库").isEmpty();
    }

    @Test
    @DisplayName("税号格式：统一社会信用代码 17 位或含小写都返回 40000，18 位大写字母数字通过")
    void shouldEnforceEighteenUpperAlnumForUscc() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OT-FORMAT", "税号格式丙", "TXC4", "ACTIVE");

        JsonNode tooShort = createTax(token, ownerId, new TaxPayload("CN", null, "CN_USCC", "91310115MA1K35X2X", false));
        assertThat(tooShort.path("code").asInt()).as("17 位必须被拒：%s", tooShort).isEqualTo(BAD_REQUEST_CODE);
        assertThat(tooShort.path("message").asText()).contains("18 位");

        // 只判形态、不做大小写归一：把提交的值悄悄改写会让审计里「提交的值」与「库里的值」对不上
        JsonNode lowerCase = createTax(token, ownerId,
                new TaxPayload("CN", null, "CN_USCC", "91310115ma1k35x2x8", false));
        assertThat(lowerCase.path("code").asInt()).as("含小写字母必须被拒：%s", lowerCase).isEqualTo(BAD_REQUEST_CODE);
        assertThat(taxesOf(token, ownerId)).as("两次被拒都不落库").isEmpty();

        JsonNode accepted = createTax(token, ownerId, new TaxPayload("CN", null, "CN_USCC", USCC, false));
        assertThat(accepted.path("code").asInt()).as("18 位大写字母数字必须通过：%s", accepted).isZero();
        assertThat(taxesOf(token, ownerId).size()).as("通过的那条落库了").isEqualTo(1);
    }

    @Test
    @DisplayName("州销售税号缺州省：不传 province 与只填空白都返回 40000，提示点明州省")
    void shouldRejectStateSalesTaxWithoutProvince() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedUsOwner("OWNER-OT-STATE-SALES", "税号州销售丁", "TXD5");

        // 「整段省略」是最常见的漏填形态，手写 JSON 才表达得出来（Payload 的 null 会序列化成显式 null）
        JsonNode omitted = postJson(token, taxesPath(ownerId),
                "{\"country\":\"US\",\"taxType\":\"US_STATE_SALES\",\"taxNumber\":\"CA-000-001\"}");
        assertThat(omitted.path("code").asInt()).as("州销售税号没有州省必须被拒：%s", omitted)
                .isEqualTo(BAD_REQUEST_CODE);
        assertThat(omitted.path("message").asText()).as("提示要说清缺的是州省").contains("州省");

        // 只填空白与没填同口径：否则它会以「没有州省」的形态落库，而唯一索引把空州省归一后判重，
        // 「加州一条、德州一条」就会被判成重复登记
        JsonNode blank = createTax(token, ownerId, new TaxPayload("US", "   ", "US_STATE_SALES", "CA-000-001", false));
        assertThat(blank.path("code").asInt()).as("只填空白的州省同样必须被拒：%s", blank)
                .isEqualTo(BAD_REQUEST_CODE);
        assertThat(taxesOf(token, ownerId)).as("两次被拒都不落库").isEmpty();
    }

    @Test
    @DisplayName("州省不属于国家：美国货主填中国的省码返回 40000，提示带出州省原文")
    void shouldRejectProvinceNotBelongingToCountry() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedUsOwner("OWNER-OT-PROVINCE", "税号州省戊", "TXE6");

        JsonNode rejected = createTax(token, ownerId, new TaxPayload("US", "CN-GD", "US_STATE_SALES", "CA-000-001", false));

        assertThat(rejected.path("code").asInt()).as("州省不属于所选国家必须被拒：%s", rejected)
                .isEqualTo(BAD_REQUEST_CODE);
        // 省码前缀只是种子数据的写法，不是判据；提示带原值，操作人才看得出是自己选错了国家还是选错了州
        assertThat(rejected.path("message").asText()).contains("CN-GD", "不属于");
        assertThat(taxesOf(token, ownerId)).as("被拒绝的新增不得落库").isEmpty();
    }

    @Test
    @DisplayName("同国家同州同类型不可重复：重复登记返回 40900，换州或换类型都能成功")
    void shouldRejectDuplicateRegistrationButAllowOtherProvinceOrType() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OT-DUP", "税号重复己", "TXF7", "ACTIVE");

        JsonNode first = createTax(token, ownerId, new TaxPayload("US", "US-CA", "US_STATE_SALES", "CA-000-001", false));
        assertThat(first.path("code").asInt()).as("首条州销售税号应成功：%s", first).isZero();

        // 号码不同也算重复：唯一索引的键里没有 tax_number，撞的是「同货主同国家同州同类型」
        JsonNode duplicate = createTax(token, ownerId, new TaxPayload("US", "US-CA", "US_STATE_SALES", "CA-000-002", false));
        assertThat(duplicate.path("code").asInt()).as("同组合重复登记必须被拒：%s", duplicate)
                .isEqualTo(CONFLICT_CODE);
        // 说成「税号重复」时操作人会去核对自己填的号码，改半天仍然被拒
        assertThat(duplicate.path("message").asText()).as("提示要说清是同组合已登记：%s", duplicate).contains("同类型");
        assertThat(taxesOf(token, ownerId).size()).as("被拒的重复登记不落库").isEqualTo(1);

        assertThat(createTax(token, ownerId, new TaxPayload("US", "US-TX", "US_STATE_SALES", "TX-000-001", false))
                .path("code").asInt()).as("换一个州是另一条登记").isZero();
        assertThat(createTax(token, ownerId, new TaxPayload("US", null, "US_EIN", "12-3456789", false))
                .path("code").asInt()).as("换一个类型是另一条登记").isZero();

        JsonNode taxes = taxesOf(token, ownerId);
        assertThat(taxes.size()).as("三条并存：%s", taxes).isEqualTo(3);
        // 顺序也是契约的一部分：按国家、州省、类型升序，空州省（国家级税号）排在该国末尾
        assertThat(labelsOf(taxes)).as("按国家、州省、类型升序：%s", taxes)
                .containsExactly("US|US-CA|US_STATE_SALES", "US|US-TX|US_STATE_SALES", "US||US_EIN");
    }

    @Test
    @DisplayName("默认唯一：同国家再设一条默认，旧的被自动改为非默认，库里该国家仍只有一条默认")
    void shouldKeepSingleDefaultTaxPerCountry() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OT-DEFAULT", "税号默认庚", "TXG8", "ACTIVE");

        JsonNode uscc = createTax(token, ownerId, new TaxPayload("CN", null, "CN_USCC", USCC, true));
        assertThat(uscc.path("code").asInt()).as("首条默认税号应成功：%s", uscc).isZero();
        String usccId = uscc.path("data").path("id").asText();

        // 接口先清同国旧默认再写入，两步在同一事务里，因此这次不该撞 ux_owner_tax_default
        JsonNode vat = createTax(token, ownerId, new TaxPayload("CN", null, "VAT", "VAT-000001", true));
        assertThat(vat.path("code").asInt()).as("同国家第二条默认税号应成功：%s", vat).isZero();
        String vatId = vat.path("data").path("id").asText();

        JsonNode taxes = taxesOf(token, ownerId);
        assertThat(taxes.size()).as("切换默认不增删行：%s", taxes).isEqualTo(2);
        assertThat(defaultIdsOf(taxes)).as("至多一条默认，且必须是刚设的那条").containsExactly(vatId);
        assertThat(rowOf(taxes, usccId).path("isDefault").asBoolean())
                .as("旧默认必须被改成非默认，而不是两条并存：%s", taxes)
                .isFalse();

        // 直接数库：列表只能证明接口返回了什么，clearDefault 要证的是「全表该国家只剩一行默认」
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM owner_tax_registration
                        WHERE owner_id = ? AND country = 'CN' AND is_default AND deleted_at IS NULL
                        """, Long.class, ownerId))
                .as("同一货主同一国家在库里只允许一条默认税号")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("编辑与删除：改号码生效、删除后列表不再返回，对不存在的标识编辑或删除都返回 40400")
    void shouldUpdateAndSoftDeleteTax() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OT-EDIT", "税号编辑辛", "TXH9", "ACTIVE");
        // 取一个必然不存在的标识，而不是写死一个「目前还没用到」的小整数
        long missingTaxId = jdbcTemplate.queryForObject(
                "SELECT coalesce(max(id), 0) + 1000000 FROM owner_tax_registration", Long.class);

        JsonNode created = createTax(token, ownerId, new TaxPayload("CN", null, "CN_USCC", USCC, false));
        assertThat(created.path("code").asInt()).as("新增失败：%s", created).isZero();
        String taxId = created.path("data").path("id").asText();

        JsonNode updated = updateTax(token, ownerId, taxId, new TaxPayload("CN", null, "CN_USCC", USCC_EDITED, false));
        assertThat(updated.path("code").asInt()).as("编辑税号失败：%s", updated).isZero();
        JsonNode afterUpdate = taxesOf(token, ownerId);
        assertThat(afterUpdate.size()).as("编辑不增删行：%s", afterUpdate).isEqualTo(1);
        assertThat(afterUpdate.get(0).path("taxNumber").asText()).as("号码要真的换掉：%s", afterUpdate)
                .isEqualTo(USCC_EDITED);
        assertThat(afterUpdate.get(0).path("id").asText()).as("编辑不换标识").isEqualTo(taxId);

        // 「不存在」与「已删除」同口径：页签上看不到它，改它也就无从谈起
        JsonNode missingUpdate = updateTax(token, ownerId, String.valueOf(missingTaxId),
                new TaxPayload("CN", null, "CN_USCC", USCC, false));
        assertThat(missingUpdate.path("code").asInt()).as("编辑不存在的税号必须 40400：%s", missingUpdate)
                .isEqualTo(NOT_FOUND_CODE);
        JsonNode missingDelete = deleteTax(token, ownerId, String.valueOf(missingTaxId));
        assertThat(missingDelete.path("code").asInt()).as("删除不存在的税号必须 40400：%s", missingDelete)
                .isEqualTo(NOT_FOUND_CODE);

        assertThat(deleteTax(token, ownerId, taxId).path("code").asInt()).as("删除税号失败").isZero();
        assertThat(taxesOf(token, ownerId)).as("删除后列表不再返回该条").isEmpty();
        // 第二次删除与「不存在」同口径：回成功会让调用方以为这次是自己删掉的
        assertThat(updateTax(token, ownerId, taxId, new TaxPayload("CN", null, "CN_USCC", USCC, false))
                .path("code").asInt()).as("已删除的税号不得再被编辑").isEqualTo(NOT_FOUND_CODE);
    }

    @Test
    @DisplayName("越权：数据范围外的货主，列表与新增都返回 40400 且新增不落库")
    void shouldDenyOwnerOutsideDataScope() throws Exception {
        long visibleOwnerId = seedOwner("OWNER-OT-SCOPE-IN", "税号范围壬", "TXJ2", "ACTIVE");
        long hiddenOwnerId = seedOwner("OWNER-OT-SCOPE-OUT", "税号范围癸", "TXK3", "ACTIVE");
        long staffId = seedStaff(SCOPED_LOGIN_NAME, "税号范围员工");
        link(staffId, seedTaxEditorRole(SCOPED_ROLE, "CUSTOM", visibleOwnerId));
        String token = loginToken(SCOPED_LOGIN_NAME);

        // 对照组：范围里的货主能正常取到（空）列表。缺这一步，下面的 40400 可能只是缺权限点或路径写错
        assertThat(taxesOf(token, visibleOwnerId)).as("范围里的货主应当能取到（空的）税号列表").isEmpty();

        JsonNode hidden = getJson(token, taxesPath(hiddenOwnerId));
        assertThat(hidden.path("code").asInt()).as("范围外的货主与「不存在」同口径：%s", hidden)
                .isEqualTo(NOT_FOUND_CODE);
        // 空列表与「无权」是两件事：前者说这个货主没有登记税号，后者说你根本看不到这个货主
        assertThat(hidden.path("data").isNull()).as("无权时必须报错，不能回空列表：%s", hidden).isTrue();

        JsonNode rejected = createTax(token, hiddenOwnerId, new TaxPayload("CN", null, "CN_USCC", USCC, false));
        assertThat(rejected.path("code").asInt()).as("写侧同样要挡住范围外的货主：%s", rejected)
                .isEqualTo(NOT_FOUND_CODE);

        // 换拥有全部范围的超管读一次：那次被拒的写入确实没有落到库里
        assertThat(taxesOf(loginToken(OPERATOR_LOGIN_NAME), hiddenOwnerId)).as("被拒的写入不得落库").isEmpty();
    }

    @Test
    @DisplayName("审计：一次新增在货主编码下留一条「维护税号」，快照含税号与默认标记")
    void shouldRecordTaxChangeAuditUnderOwnerCode() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        String ownerCode = "OWNER-OT-AUDIT";
        long ownerId = seedOwner(ownerCode, "税号审计子", "TXL4", "ACTIVE");

        assertThat(createTax(token, ownerId, new TaxPayload("CN", null, "CN_USCC", USCC, true)).path("code").asInt())
                .as("新增税号失败")
                .isZero();

        JsonNode records = auditRecordsOf(token, ownerCode);
        assertThat(records.size()).as("一次新增只留一条：%s", records).isEqualTo(1);
        JsonNode record = records.get(0);
        // 事件 OWNER_TAX_CHANGED 的落库形态：对象是货主、动作是 UPDATE、摘要取事件模板 + 货主编码。
        // 记成独立的 OWNER_TAX 类型后，从货主详情追溯时这条会消失，而追溯入口恰恰在那里
        assertThat(record.path("bizType").asText()).isEqualTo("OWNER");
        assertThat(record.path("bizId").asText()).isEqualTo(ownerCode);
        assertThat(record.path("action").asText()).isEqualTo("UPDATE");
        assertThat(record.path("summary").asText()).isEqualTo("维护税号 " + ownerCode);
        // 新增没有变更前值；三种写入口由前后快照区分：新增前值为空、删除后值为空、修改两者都有
        assertThat(record.path("beforeValue").isNull()).as("新增的变更前值必须为空：%s", record).isTrue();
        assertThat(record.path("afterValue").path("taxNumber").asText()).isEqualTo(USCC);
        assertThat(record.path("afterValue").path("isDefault").asBoolean()).isTrue();
        assertThat(record.path("afterValue").path("taxType").asText()).isEqualTo("CN_USCC");
        assertThat(record.path("afterValue").path("province").isNull())
                .as("没有州省时键仍在、值为 null：%s", record)
                .isTrue();
    }

    /**
     * 税号子资源的路径。
     *
     * <p>抽出来是因为四个端点与越权用例都要拼同一个前缀：路径写错一次会让 40400 出现在不该出现的用例里，
     * 而那种失败看起来像业务 bug。
     *
     * @param ownerId 货主标识
     * @return 该货主的税号集合路径
     */
    private static String taxesPath(long ownerId) {
        return "/api/console/owners/" + ownerId + "/taxes";
    }

    /**
     * 新增一条税号，返回原始响应。
     *
     * <p>不在这里断言业务码：被拒的用例要的正是 4xxxx，由调用方按场景断言。
     *
     * @param token   操作人令牌
     * @param ownerId 货主标识，取自路径
     * @param payload 税号请求体
     * @return 接口响应
     * @throws Exception 请求构造或执行失败
     */
    private JsonNode createTax(String token, long ownerId, TaxPayload payload) throws Exception {
        return postJson(token, taxesPath(ownerId), objectMapper.writeValueAsString(payload));
    }

    /**
     * 覆盖一条税号的可编辑字段，返回原始响应。
     *
     * <p>不在这里断言业务码：对不存在的标识编辑要的正是 40400。
     *
     * @param token   操作人令牌
     * @param ownerId 货主标识，仅用于定位
     * @param taxId   税号标识
     * @param payload 税号请求体；国家与类型在编辑时不参与判定，但请求体里必须给出
     * @return 接口响应
     * @throws Exception 请求构造或执行失败
     */
    private JsonNode updateTax(String token, long ownerId, String taxId, TaxPayload payload) throws Exception {
        return putJson(token, taxesPath(ownerId) + "/" + taxId, objectMapper.writeValueAsString(payload));
    }

    /**
     * 删除一条税号，返回响应。
     *
     * <p>基类只编组了 GET/POST/PUT；删除的响应体要参与断言（重复删除得 40400），
     * 因此不能只断言 HTTP 状态。
     *
     * @param token   操作人令牌
     * @param ownerId 货主标识，仅用于定位
     * @param taxId   税号标识
     * @return 接口响应
     * @throws Exception 请求构造或执行失败
     */
    private JsonNode deleteTax(String token, long ownerId, String taxId) throws Exception {
        MvcResult result = mockMvc.perform(delete(taxesPath(ownerId) + "/" + taxId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * 取某货主的税号列表，并断言请求本身成功。
     *
     * <p>不把「列表为空」当成失败：空列表是合法结果，由调用方按场景断言；这里只把「请求失败」
     * 与「范围过滤掉」区分开，否则 404 到底来自过滤还是参数错误，看断言分不出来。
     *
     * @param token   操作人令牌
     * @param ownerId 货主标识
     * @return 响应里的 {@code data} 节点（税号数组）
     * @throws Exception 请求构造或响应解析失败
     */
    private JsonNode taxesOf(String token, long ownerId) throws Exception {
        JsonNode response = getJson(token, taxesPath(ownerId));
        assertThat(response.path("code").asInt()).as("查询税号列表失败：%s", response).isZero();
        return response.path("data");
    }

    /**
     * 把每行压成 {@code 国家|州省|类型}，用于断言列表顺序。
     *
     * @param taxes 税号数组
     * @return 每行的排序键；{@code province} 为 {@code null} 时该段为空
     */
    private static List<String> labelsOf(JsonNode taxes) {
        List<String> labels = new ArrayList<>();
        taxes.forEach(row -> labels.add(row.path("country").asText()
                + "|" + (row.path("province").isNull() ? "" : row.path("province").asText())
                + "|" + row.path("taxType").asText()));
        return labels;
    }

    /**
     * 列表里所有默认税号的标识。
     *
     * @param taxes 税号数组
     * @return 默认税号标识列表；没有默认时为空
     */
    private static List<String> defaultIdsOf(JsonNode taxes) {
        List<String> ids = new ArrayList<>();
        taxes.forEach(row -> {
            if (row.path("isDefault").asBoolean()) {
                ids.add(row.path("id").asText());
            }
        });
        return ids;
    }

    /**
     * 按标识取列表里的一行。
     *
     * <p>取不到时直接失败：返回一个空节点会让后续的字段断言读到一个不存在的东西而「看起来通过」，
     * 那种通过比失败更难查。
     *
     * @param taxes 税号数组
     * @param taxId 税号标识
     * @return 对应的那一行
     */
    private static JsonNode rowOf(JsonNode taxes, String taxId) {
        for (JsonNode row : taxes) {
            if (taxId.equals(row.path("id").asText())) {
                return row;
            }
        }
        return fail("列表里没有标识为 " + taxId + " 的税号：" + taxes);
    }

    /**
     * 造一个注册地在美国的货主。
     *
     * <p>先交给基类的 {@code seedOwner}（编码与 SKU 前缀的判重、复用都在那里），再把注册地址改成美国。
     * 税号规则只读请求体里的国家、不读货主的注册地址，但用例写的是「美国货主」，注册地就该是 US——
     * 否则下一个读这段代码的人得先确认「造数与场景是否一致」才判断得了断言有没有意义。
     *
     * @param code      货主编码
     * @param name      货主名称
     * @param skuPrefix SKU 前缀，四字符且全表唯一
     * @return 货主标识
     */
    private long seedUsOwner(String code, String name, String skuPrefix) {
        long ownerId = seedOwner(code, name, skuPrefix, "ACTIVE");
        jdbcTemplate.update("""
                UPDATE owner SET country = 'US', province = 'US-CA', city = 'Los Angeles', district = 'CA',
                                  postal_code = '90001', address_line = '1 Test Ave'
                WHERE id = ?
                """, ownerId);
        return ownerId;
    }

    /**
     * 建一个既持 {@code system:owner:list} 又持 {@code system:owner:tax:edit} 的角色。
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
    private long seedTaxEditorRole(String code, String scopeType, long... ownerIds) {
        long roleId = seedScopedRole(code, scopeType, ownerIds);
        jdbcTemplate.update("""
                INSERT INTO sys_role_menu (role_id, menu_id) SELECT ?, id FROM sys_menu
                WHERE perms = 'system:owner:tax:edit' ON CONFLICT (role_id, menu_id) DO NOTHING
                """, roleId);
        return roleId;
    }

    /**
     * 按货主编码追溯审计记录，并断言请求本身成功。
     *
     * <p>只查 {@code bizType=OWNER&bizId=<货主编码>}：税号日志挂在货主编码下，
     * 从货主详情追溯时要与建档、改状态、改联系人排在同一条链上。
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
     * 税号写入请求体的最小映射，新增与编辑共用。
     *
     * <p>不复用生产侧的 {@code OwnerTaxSaveRequest}：用同一个类型序列化再反序列化，
     * 会掩盖字段名被改而测试仍然通过的情况（与 {@code OwnerContactIntegrationTest} 的取向一致）。
     *
     * <p>它<b>表达不了「字段整段省略」</b>——{@code null} 会序列化成显式的 {@code null}。
     * 需要验证「省略」的那一步因此手写 JSON 字面量。
     *
     * @param country   国家，两位大写字母
     * @param province  州省，可为 {@code null}（序列化为显式 null）
     * @param taxType   税号类型，取 {@code TAX_ID_TYPE} 字典的 value
     * @param taxNumber 税号
     * @param isDefault 是否设为该国默认税号
     */
    private record TaxPayload(String country, String province, String taxType, String taxNumber,
                              Boolean isDefault) {
    }
}
