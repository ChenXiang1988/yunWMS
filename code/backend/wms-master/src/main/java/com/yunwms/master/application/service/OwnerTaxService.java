package com.yunwms.master.application.service;

import com.yunwms.master.application.audit.OwnerAuditSnapshots;
import com.yunwms.master.application.command.SaveOwnerTaxCommand;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerAuditEvent;
import com.yunwms.master.domain.model.OwnerTaxDraft;
import com.yunwms.master.domain.model.OwnerTaxRegistration;
import com.yunwms.master.domain.port.AuditTrail;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.master.domain.port.DictLookup;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.master.domain.port.OwnerTaxRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 货主税务登记的增删改查用例：可见性判定、类型与国家匹配、州省规则、税号格式、默认切换与审计留痕都在这里。
 *
 * <p><b>数据范围只有一条规则：税号的可见性等于其货主的可见性。</b>税号是货主的子资源，有自己的权限点
 * （{@code system:owner:tax:edit}，见 {@code V042}），但没有独立的可见范围。因此四条用例都先过
 * {@link OwnerRepository#findVisibleById}——<b>读侧与写侧一样</b>。编辑与删除只拿得到税号标识，
 * 归属与类型是库里那一行的事实，只能先读出来；但读到之后、任何返回或写入之前必须完成可见性判定，
 * 不通过时这次读取的结果直接丢弃。这两个入口的顺序固定为「先按税号标识取归属，再判货主可见」，
 * 与 {@code OwnerContactService} 逐条相同：按存在性放行等于让范围外的货主被改。
 *
 * <p><b>校验顺序固定为「取操作人 → 货主可见 → 类型与国家匹配 → 州省规则 → 税号格式 → 默认切换 →
 * 落库 → 留痕」：</b>操作人先取，他既是 {@code created_by}/{@code updated_by} 的来源，也是清旧默认那条
 * 语句要写的操作人；货主可见先于字段规则，因为「这个陌生的类型不对」这句话只有在货主确实可见时
 * 才对调用方有意义；依赖字典与类型语义的几条规则排在默认切换之前，因为它们是纯内存判断加至多两次
 * 字典查询，不合法时不该先动到同国另一行的默认标记。
 *
 * <p><b>编辑（{@link #update}）判的是库里那一行的国家与类型，不是入参里的：</b>这两列是行的身份
 * （{@link OwnerTaxRepository#update} 的 SET 列表里没有它们），改它们等于换一条登记，应删旧登新。
 * 这几条规则照旧执行，只是取值来源不同——「类型是否适用该国家」「州省是否属于该国」判的是这一行当前的
 * 组合，「州销售税号有没有州省」与「格式是否合法」判的是本次提交的值配这一行的类型。
 * 因此一次编辑在字典项停用之后会被拒（40000），
 * 与 {@code OwnerContactService} 对停用角色的处理同口径：停用意味着不再被新数据引用，
 * 而换掉一个号码也是新数据。代价要说清楚：这类记录此后只能删除或改回启用项，不能就地改号码。
 *
 * <p><b>州省的两条规则单独成一步，因为它们判的是「这次提交的组合本身成不成立」：</b>州销售税号
 * （{@code US_STATE_SALES}）必须有州省，州省一旦非空就必须属于所选国家。前者只对这一个类型生效，
 * 不由 {@link OwnerTaxDraft} 无条件要求：国家级税号（统一社会信用代码、跨国的 VAT）本就没有州省，
 * 而「哪种类型要求填州省」随 {@code TAX_ID_TYPE} 字典的语义而变，模型只判列的形态。这条规则也不能省：
 * {@code ux_owner_tax_unique} 用 {@code COALESCE(province, '')} 把空州省归一后判重，不填州省的
 * 州销售税号会让「加州一条、德州一条」被判成重复登记，而「不收某个州的销售税」这个意思本来就无法表达。
 * 后者与货主注册地址同一口径（{@code OwnerDictionaryValidator}）：层级的权威在字典项的 {@code ext.parent}，
 * 由 {@link DictLookup#isProvinceOfCountry} 回答，不让调用方记住「省码以国家码开头」这类巧合。
 * 两条都把整串空白当作未填，与 {@link OwnerTaxDraft} 的归一口径一致：否则「只填了空格」会在这一层被放行、
 * 到模型层归一成 {@code null} 落库，最后一条没有州省的州销售税号照样进库。顺序在「类型与国家匹配」之后，
 * 因为类型不适用该国家时，「换个类型或换个国家」比「州省不对」更接近真正的错处；在「税号格式」之前，
 * 因为号码写错只改几位，而国家与州省配错要换的是整个选择。
 *
 * <p><b>中国统一社会信用代码的 18 位规则只在本类落一次：</b>按类型分支判（{@code CN_USCC} 才套正则），
 * 而不是无条件校验——其余类型的长度与字符集随各国税制而变，没有统一形态可判。规则不放进
 * {@code OwnerTaxDraft}、也不放进请求 DTO：前者不认识「类型语义」，后者只做协议转换；
 * 两处都能拿到同一条规则时，迟早会对「小写字母算不算」给出不同答案。
 *
 * <p><b>默认税号切换固定为「先 {@link OwnerTaxRepository#clearDefault} 再写」，不由仓储隐式完成：</b>
 * {@code ux_owner_tax_default} 是唯一权威，端口把顺序写进了契约。{@code isDefault} 为 {@code false}
 * 时不清任何东西——把一条税号改成非默认，不该顺手动同国另一行。
 *
 * <p><b>三个写方法都开事务：</b>清旧默认与写入是同一次业务动作的两个半边，分开提交会留下
 * 「该国家一个默认税号也没有」的中间态；审计又必须与业务变更同生共死（{@link AuditTrail} 的实现
 * 加入本方法的事务），否则会留下「税号改了但没留痕」与「留了痕但税号没改」两种都要人工核对的残局。
 * {@link #list} 不加事务：只有两次只读查询，各看各的快照，加注解不会让它们更一致
 * （与 {@code OwnerQueryService} 同一处理）。
 *
 * <p><b>并发撞上两个唯一索引时翻译成 40900：</b>「先清后写」只保证单笔请求不留中间态，挡不住两笔
 * 并发的「设为默认」——后写的一笔会被 {@code ux_owner_tax_default} 拒绝，那是一次能被如实解释的
 * 正常失败（别人先设了）。同一货主在同一国家、同一州省下的同类型重复登记由
 * {@code ux_owner_tax_unique} 拒绝。约束名到错误码的映射归 {@link OwnerConstraintTranslator}，
 * 本类不判约束名；{@code catch} 只包住仓储写入那一行，扩大到整个方法会把审计等其他来源的
 * {@link DataIntegrityViolationException} 一并报成「已有同类型税号」，那是谎报。
 *
 * <p><b>三条写入口共用一个审计事件，对象类型是 {@code OWNER}：</b>理由与代价见
 * {@link OwnerAuditEvent#OWNER_TAX_CHANGED}——管理端从货主详情的「税号」页签追溯这些记录，
 * 记成独立类型会让它们从「按货主编码」的链条里消失。编码取自这次可见性判定的返回值，
 * 不为审计多查一次货主。
 *
 * <p>字段级的前后值对比不在本类做：审计记完整的变更前后快照（{@link OwnerAuditSnapshots#ofTax}），
 * 差异由读日志的人（或他的工具）算，与货主、联系人、负责客服的用例同一口径。
 */
@Service
public class OwnerTaxService {

    /** 税号类型的字典类型编码，与 {@code V042__seed_tax_type_dict.sql} 的 {@code dict_type} 一致。 */
    private static final String TAX_TYPE_DICT = "TAX_ID_TYPE";

    /**
     * 中国统一社会信用代码的字典项取值。
     *
     * <p>写成常量而不是散在判断里：它是「18 位规则要判哪一种类型」的唯一依据，
     * 与 {@code V042} 种下的字典项 {@code value} 逐字一致；拼错时规则会静默地对一种不存在的类型生效，
     * 而 18 位校验看起来仍然「有人写了」。
     */
    private static final String CN_USCC_TAX_TYPE = "CN_USCC";

    /**
     * 美国州销售税号的字典项取值，与 {@code V042} 种下的 {@code US_STATE_SALES} 逐字一致。
     *
     * <p>写成常量而不是散在判断里：它是「州省必须填」这条规则的唯一判据。拼错时规则会对一种不存在的
     * 类型生效——校验代码看起来仍然「有人写了」，而真正的州销售税号一条也拦不住。
     */
    private static final String US_STATE_SALES_TAX_TYPE = "US_STATE_SALES";

    /**
     * 统一社会信用代码的形态：18 位大写字母或数字。
     *
     * <p>按规格「校验 18 位字母数字格式」取大写字母：统一社会信用代码本身的字符集不含小写字母，
     * 而是否把小写归一成大写属于入口该不该做的事，与「这条规则是什么」是两件事——
     * 这里只判形态，不做大小写转换（与 {@code OwnerTaxRegistration#COUNTRY_PATTERN} 同一取向：
     * 悄悄改写会让审计里「提交的值」与「库里的值」对不上）。
     */
    private static final Pattern CN_USCC_PATTERN = Pattern.compile("^[0-9A-Z]{18}$");

    private final OwnerTaxRepository ownerTaxRepository;
    private final OwnerRepository ownerRepository;
    private final DictLookup dictLookup;
    private final CurrentOperator currentOperator;
    private final AuditTrail auditTrail;

    /**
     * @param ownerTaxRepository 税号仓储端口
     * @param ownerRepository    货主仓储端口；只用于可见性判定与取审计用的货主编码
     * @param dictLookup         字典窄端口，判 {@code taxType} 是否适用该国家、州省是否属于该国家
     * @param currentOperator    当前操作人端口
     * @param auditTrail         审计留痕端口，由 {@code wms-app} 接到审计能力上
     */
    public OwnerTaxService(OwnerTaxRepository ownerTaxRepository,
                           OwnerRepository ownerRepository,
                           DictLookup dictLookup,
                           CurrentOperator currentOperator,
                           AuditTrail auditTrail) {
        this.ownerTaxRepository = ownerTaxRepository;
        this.ownerRepository = ownerRepository;
        this.dictLookup = dictLookup;
        this.currentOperator = currentOperator;
        this.auditTrail = auditTrail;
    }

    /**
     * 取某货主名下的全部税号。
     *
     * <p>先判可见性再取列表，不能反过来：反过来等于「先把范围外的数据读出来，再决定要不要给出去」。
     * 不可见时的正确结果也不是空列表——空列表的意思是「这个货主没有登记税号」，
     * 而事实是「你无权看这个货主」，两者对调用方的下一步动作完全不同。
     *
     * <p>不分页也不排序：单个货主的税号是个位数量级，顺序由端口固定为
     * {@code country, province, tax_type} 升序（见 {@link OwnerTaxRepository#findByOwnerId}）。
     *
     * @param ownerId 货主标识
     * @return 该货主的未删除税号，按国家、州省、类型升序；没有时返回空列表
     * @throws BizException 货主不存在、已逻辑删除或不在当前数据范围内（40400）
     */
    public List<OwnerTaxRegistration> list(long ownerId) {
        requireVisibleOwner(ownerId);
        return ownerTaxRepository.findByOwnerId(ownerId);
    }

    /**
     * 新增一条税务登记。
     *
     * <p>draft 在清旧默认之前组装：它的构造是纯内存动作，字段不合法时不该先动到同国那一行。
     * 「先清旧默认、再写新默认」的顺序不受影响——清理仍在写入之前。
     *
     * @param command 新增入参；其中的 {@code isDefault} 决定是否切换该国家的默认税号
     * @return 新税号，标识为数据库生成值
     * @throws BizException 当前会话无效（40100）；货主不存在、已逻辑删除或不在当前数据范围内（40400）；
     *                      税号类型为空白或与所填国家不匹配、州销售税号未填州省、州省不属于所填国家、
     *                      {@code CN_USCC} 不是 18 位大写字母或数字（40000）；同一货主在同一国家、
     *                      同一州省下已有同类型税号，或并发下另一笔请求先设了该国家的默认税号（40900）
     * @throws IllegalArgumentException 国家形态不合法、税号类型或税号为空、任一字段超出列宽，
     *                                  说明入口没有校验就调了本方法（见 {@link OwnerTaxDraft}）
     */
    @Transactional
    public OwnerTaxRegistration create(SaveOwnerTaxCommand command) {
        long operatorId = currentOperatorId();
        Owner owner = requireVisibleOwner(command.ownerId());
        requireTaxTypeApplicableToCountry(command.taxType(), command.country());
        requireProvinceForStateSalesTax(command.taxType(), command.province());
        requireProvinceOfCountry(command.province(), command.country());
        requireTaxNumberFormat(command.taxType(), command.taxNumber());

        OwnerTaxDraft draft = new OwnerTaxDraft(command.country(), command.province(),
                command.taxType(), command.taxNumber(), command.isDefault());
        if (draft.isDefault()) {
            // 不靠仓储隐式清：见类注释。该国家还没有默认税号时它是无操作，因此不必先查一次
            ownerTaxRepository.clearDefault(owner.id(), draft.country(), operatorId);
        }

        long id;
        try {
            id = ownerTaxRepository.insert(owner.id(), draft, operatorId);
        } catch (DataIntegrityViolationException e) {
            // 只包这一条写语句：下面审计的库异常不该被解释成「已有同类型税号」（见类注释）
            throw OwnerConstraintTranslator.translateTaxWrite(e);
        }
        OwnerTaxRegistration created = toRegistration(id, owner.id(), draft);
        // before 为 null：新增没有变更前值。bizId 用货主编码而不是主键——审计要能被「按编码追溯」查到
        auditTrail.record(OwnerAuditEvent.OWNER_TAX_CHANGED, owner.code(), null,
                OwnerAuditSnapshots.ofTax(created));
        return created;
    }

    /**
     * 覆盖一条税务登记的可编辑字段：州省、号码与默认标记。
     *
     * <p>归属、国家与类型以库里那一行为准：{@code command} 里同名的三个字段不参与判定，
     * 写语句的 SET 列表里也没有它们，因此不存在「一次编辑把税号搬到另一个货主名下或另一个类型名下」
     * 的路径。州省的两条规则照此取值：只有州省取本次提交的值（它是可编辑列），
     * 「该类型是否要求州省」与「州省是否属于该国」判的仍是这一行的类型与国家。
     *
     * @param taxId   税号标识
     * @param command 编辑入参
     * @return 更新后的税号
     * @throws BizException 当前会话无效（40100）；税号不存在或已逻辑删除（40400）；
     *                      该税号的货主不在当前数据范围内（40400）；
     *                      该行的类型已不适用于其国家（字典项停用或适用国家被改）、该行是州销售税号
     *                      而本次未填州省、州省不属于该行的国家、{@code CN_USCC}
     *                      不是 18 位大写字母或数字（40000）；同一货主在同一国家、同一州省下已有同类型
     *                      税号，或并发下另一笔请求先设了该国家的默认税号（40900）
     * @throws IllegalArgumentException 州省或号码为空、任一字段超出列宽，说明入口没有校验就调了本方法
     */
    @Transactional
    public OwnerTaxRegistration update(long taxId, SaveOwnerTaxCommand command) {
        long operatorId = currentOperatorId();
        OwnerTaxRegistration existing = requireTax(taxId);
        // 归属只认库里那一行：先取到 ownerId 才判得了可见性，判定不通过时这次读取的结果直接丢弃
        Owner owner = requireVisibleOwner(existing.ownerId());
        requireTaxTypeApplicableToCountry(existing.taxType(), existing.country());
        requireProvinceForStateSalesTax(existing.taxType(), command.province());
        requireProvinceOfCountry(command.province(), existing.country());
        requireTaxNumberFormat(existing.taxType(), command.taxNumber());

        // 国家与类型取 existing：它们不落库，传进去只参与校验（见 OwnerTaxDraft）
        OwnerTaxDraft draft = new OwnerTaxDraft(existing.country(), command.province(),
                existing.taxType(), command.taxNumber(), command.isDefault());
        if (draft.isDefault()) {
            // 本条本来就已是默认时，清理会先把它自己置为非默认、随后这次写入又置回默认；净效果为零，
            // 多出的那条 UPDATE 落在同一行上，且不产生额外的审计记录
            ownerTaxRepository.clearDefault(existing.ownerId(), existing.country(), operatorId);
        }

        try {
            ownerTaxRepository.update(existing.id(), draft, operatorId);
        } catch (DataIntegrityViolationException e) {
            // 与新增同一处理：只包这一条写语句，审计失败不能被说成「已有同类型税号」
            throw OwnerConstraintTranslator.translateTaxWrite(e);
        }
        OwnerTaxRegistration updated = toRegistration(existing.id(), existing.ownerId(), draft);
        auditTrail.record(OwnerAuditEvent.OWNER_TAX_CHANGED, owner.code(),
                OwnerAuditSnapshots.ofTax(existing), OwnerAuditSnapshots.ofTax(updated));
        return updated;
    }

    /**
     * 软删除一条税务登记。
     *
     * <p>删除会释放该货主在该国家下的默认税号名额（部分唯一索引带 {@code deleted_at IS NULL}），
     * 因此删掉默认税号之后可以直接设一个新的，不需要先清一次。
     *
     * @param taxId 税号标识
     * @throws BizException 当前会话无效（40100）；税号不存在、已逻辑删除，或在读到之后被另一个请求
     *                      删掉（40400）；该税号的货主不在当前数据范围内（40400）
     */
    @Transactional
    public void delete(long taxId) {
        long operatorId = currentOperatorId();
        OwnerTaxRegistration existing = requireTax(taxId);
        Owner owner = requireVisibleOwner(existing.ownerId());

        if (!ownerTaxRepository.softDelete(existing.id(), operatorId)) {
            // 读到之后、写入之前这一行被另一个请求删掉了：此刻它确实不存在，如实回答 404。
            // 回成功会掩盖「两次删除之间隔了一段时间」，也让调用方以为是自己删的
            throw BizException.notFound("税号不存在");
        }

        // after 为 null：删除没有变更后快照。before 是这一行从页签上消失之后唯一的副本
        auditTrail.record(OwnerAuditEvent.OWNER_TAX_CHANGED, owner.code(),
                OwnerAuditSnapshots.ofTax(existing), null);
    }

    /**
     * 取当前请求的操作人标识。
     *
     * @return 登录用户标识
     * @throws BizException 当前请求没有有效会话（40100）
     */
    private long currentOperatorId() {
        // 与货主、联系人、负责客服的写用例同一理由：取不到操作人就不能继续，
        // 审计列写成空值后无从追溯这次变更
        return currentOperator.currentUserId()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 判定货主对当前请求可见，并顺手取回审计要用到的系统编码。
     *
     * @param ownerId 货主标识
     * @return 可见的货主
     * @throws BizException 货主不存在、已逻辑删除或不在当前数据范围内（40400）
     */
    private Owner requireVisibleOwner(long ownerId) {
        return ownerRepository.findVisibleById(ownerId)
                // 范围外的货主与「不存在」同口径：两者的区别本身就是一条能拿来枚举他方数据的信息
                .orElseThrow(() -> BizException.notFound("货主不存在"));
    }

    /**
     * 按标识读取未删除的税号。
     *
     * @param taxId 税号标识
     * @return 税号
     * @throws BizException 税号不存在或已逻辑删除（40400）
     */
    private OwnerTaxRegistration requireTax(long taxId) {
        return ownerTaxRepository.findById(taxId)
                // 已逻辑删除的税号与「不存在」同口径：页签上看不到它，改它也就无从谈起
                .orElseThrow(() -> BizException.notFound("税号不存在"));
    }

    /**
     * 校验税号类型适用该国家。
     *
     * <p>两个空白判定单独写，而不是交给 {@link DictLookup#isItemApplicableTo}：那个端口的契约要求
     * 调用方保证参数非空白（它把空参数当编程错误抛 {@code IllegalArgumentException}），
     * 而漏填类型或国家是业务输入问题，该回 40000 并带一句人能读的提示。这一步属于
     * 「类型必须取自字典且与国家匹配」这条规则，不是「必填」的第二次校验——必填由
     * {@code OwnerTaxDraft} 判，这里判的是两者能不能配上。
     *
     * <p>取值原样送字典比对，不裁两侧空格：字典项的值就是 {@code CN_USCC} 这种整串，
     * 在这里裁一次会让「用来判定的值」与「落库的值」不是同一个串。带空格的 {@code " CN_USCC "}
     * 因此被拒，正是应有的结果。
     *
     * @param taxType 税号类型，新增时来自入参，编辑时来自库里那一行
     * @param country 国家，新增时来自入参，编辑时来自库里那一行
     * @throws BizException 类型或国家为空白，或该类型不在字典的适用清单内（40000）
     */
    private void requireTaxTypeApplicableToCountry(String taxType, String country) {
        if (taxType == null || taxType.isBlank()) {
            throw BizException.badRequest("税号类型不能为空");
        }
        if (country == null || country.isBlank()) {
            throw BizException.badRequest("国家不能为空");
        }
        if (!dictLookup.isItemApplicableTo(TAX_TYPE_DICT, taxType, country)) {
            // 提示里带类型与国家的原文：只写「不匹配」的话，操作人不知道该换类型还是换国家；
            // 带字典类型名，他才知道去哪里看「这种税号到底适用哪些国家」
            throw BizException.badRequest("税号类型 " + taxType + " 与所填国家 " + country
                    + " 不匹配，请从字典 " + TAX_TYPE_DICT + " 的适用清单中选择");
        }
    }

    /**
     * 校验州销售税号填了州省。
     *
     * <p>只管 {@code US_STATE_SALES} 一个类型：州销售税号是按州登记的，没有州就不成立；其余类型
     * （统一社会信用代码、EIN、跨国的 VAT）可以是国家级登记，州省留给调用方按需选择。判据是类型取值
     * 本身，而不是字典里某个「要求州省」的属性——字典只声明适用国家，不声明必填字段，
     * 凭空读一个不存在的键只会让这条规则看起来配置化了、实际永远不生效。
     *
     * <p>空白与未填同口径：{@link OwnerTaxDraft} 会把整串空白归一为 {@code null}，这里若放过空白，
     * 「只填了空格」的州销售税号就会以「没有州省」的形态落库，而 {@code ux_owner_tax_unique} 又把空州省
     * 归一后判重，两条不同州的销售税号会被判成重复登记。
     *
     * @param taxType  税号类型，新增时来自入参，编辑时来自库里那一行
     * @param province 本次提交的州省
     * @throws BizException 类型为 {@code US_STATE_SALES} 而州省为空或整串空白（40000）
     */
    private static void requireProvinceForStateSalesTax(String taxType, String province) {
        if (US_STATE_SALES_TAX_TYPE.equals(taxType) && (province == null || province.isBlank())) {
            throw BizException.badRequest("州销售税号必须选择州省");
        }
    }

    /**
     * 校验州省属于所选国家。
     *
     * <p>州省为空时直接通过：它本就不是必填列，国家级税号没有州省。而
     * {@link DictLookup#isProvinceOfCountry} 的契约要求两个参数非空白（空参数属于编程错误，不是业务
     * 输入问题），因此「有没有填」必须先在这里判过——省掉这一判，一次「没填州省」就会变成
     * {@code IllegalArgumentException}，也就是一个 50000。空白同样视为未填，理由与
     * {@link #requireProvinceForStateSalesTax} 相同。
     *
     * <p>取值原样送字典比对，不裁两侧空格：与 {@link #requireTaxTypeApplicableToCountry} 同一取向——
     * 裁一次会让「用来判定的值」与「落库的值」不是同一个串，{@code " US-CA "} 被悄悄当成
     * {@code US-CA} 通过，库里留下的却是原值。
     *
     * <p>失败提示同时提到「不属于」与「启用项」：端口对「州省不存在」「该项已停用」「该项属于别的国家」
     * 三种情况一律回 {@code false}，提示若只说「不属于」，操作人会去改国家，而真正的原因可能是
     * 那条字典项被停用了。
     *
     * @param province 本次提交的州省，可为 {@code null} 或整串空白
     * @param country  国家，新增时来自入参，编辑时来自库里那一行
     * @throws BizException 州省不属于该国家，或已不在字典 {@code PROVINCE} 的启用项中（40000）
     */
    private void requireProvinceOfCountry(String province, String country) {
        if (province == null || province.isBlank()) {
            return;
        }
        if (!dictLookup.isProvinceOfCountry(province, country)) {
            throw BizException.badRequest("州省 " + province + " 不属于所选国家 " + country
                    + "，或已不在字典 PROVINCE 的启用项中，请重新选择");
        }
    }

    /**
     * 校验税号格式：{@code CN_USCC} 必须是 18 位大写字母或数字，其余类型不判。
     *
     * <p>只判这一条是刻意的：{@code US_EIN} 是 2 位州码加 7 位数字、{@code US_STATE_SALES} 各州形态
     * 不同、{@code VAT} 各国的长度与前缀都不一样，把这些写进代码等于把「哪个国家怎么编号」
     * 固化成每扩一国都要改代码发版。规格只为统一社会信用代码提了格式要求，就只实现这一条。
     *
     * @param taxType   税号类型
     * @param taxNumber 本次提交的税号
     * @throws BizException 类型为 {@code CN_USCC} 而号码不是 18 位大写字母或数字（40000）
     */
    private static void requireTaxNumberFormat(String taxType, String taxNumber) {
        if (!CN_USCC_TAX_TYPE.equals(taxType)) {
            return;
        }
        if (taxNumber != null && CN_USCC_PATTERN.matcher(taxNumber).matches()) {
            return;
        }
        // 带原文：18 位里错在哪一位看不出来，但「提交的是什么」必须能对上，否则操作人无从自查
        throw BizException.badRequest("统一社会信用代码须为 18 位大写字母或数字，实际：" + taxNumber);
    }

    /**
     * 用「写进去的值」还原税号，供返回与审计使用。
     *
     * <p>不回读一次库：落库的就是这份值，回查只多一次往返，还允许「读到的是别人改后的值」
     * 混进本次快照（与 {@link OwnerAuditSnapshots#ofUpdated} 同一取向）。
     *
     * <p>抽成方法是因为新增与编辑两处写的是同一份七元顺序：逐处手写时把州省与类型写反不会被编译拦下，
     * 而两者都是字符串，报错要等到某次读取才发现。
     *
     * @param id      税号标识
     * @param ownerId 所属货主标识
     * @param draft   本次落库的字段值
     * @return 税号
     * @throws IllegalArgumentException 字段不满足不变量，说明写入前已绕过了 {@link OwnerTaxDraft}
     */
    private static OwnerTaxRegistration toRegistration(long id, long ownerId, OwnerTaxDraft draft) {
        return new OwnerTaxRegistration(id, ownerId, draft.country(), draft.province(),
                draft.taxType(), draft.taxNumber(), draft.isDefault());
    }
}
