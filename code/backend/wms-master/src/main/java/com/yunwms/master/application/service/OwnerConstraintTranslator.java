package com.yunwms.master.application.service;

import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.function.Function;

/**
 * 货主写入侧的库约束翻译：把数据库抛出的完整性约束冲突翻成可展示的业务错误。
 *
 * <p><b>为什么单独成类：</b>建档与编辑都会撞上 {@code ux_owner_external_code}，两处各写一遍
 * 「约束名 → 错误码」的映射，改一处忘一处时表现成「同一个外部码，建档回 40906、编辑回 50000」。
 * 规则必须有唯一所有者。
 *
 * <p><b>为什么按约束名匹配而不是按 SQLState：</b>违反唯一索引、外键、CHECK 的 SQLState 都是 23xxx，
 * 区分不了是哪一处；约束名不会随 {@code lc_messages} 或其他区域设置改变，是唯一稳定的标识。
 *
 * <p><b>不认识的约束原样返回：</b>那是本用例的假设被打破了（例如新加了 CHECK），猜一个错误码
 * 返回给用户，等于把「我们不知道自己违反了哪条规则」伪装成一次正常的拒绝。
 */
final class OwnerConstraintTranslator {

    /** {@code owner.code} 的全表唯一索引。 */
    private static final String OWNER_CODE_UNIQUE_INDEX = "ux_owner_code";

    /** {@code owner.sku_prefix} 的全表唯一索引，不带 {@code deleted_at} 条件。 */
    private static final String OWNER_SKU_PREFIX_UNIQUE_INDEX = "ux_owner_sku_prefix";

    /** {@code owner.external_code} 的部分唯一索引，口径是未删除的货主。 */
    private static final String OWNER_EXTERNAL_CODE_UNIQUE_INDEX = "ux_owner_external_code";

    /** {@code owner_contact.owner_id} 的部分唯一索引，口径是未删除且 {@code is_primary} 的联系人。 */
    private static final String OWNER_CONTACT_PRIMARY_UNIQUE_INDEX = "ux_owner_contact_primary";

    /** {@code owner_assignee} 的 {@code (owner_id, relation_type)} 唯一索引，保证同一货主每角色至多一行。 */
    private static final String OWNER_ASSIGNEE_ROLE_UNIQUE_INDEX = "ux_owner_assignee_role";

    /**
     * {@code owner_tax_registration} 的部分唯一索引，口径是未删除的
     * {@code (owner_id, country, COALESCE(province, ''), tax_type)}。
     *
     * <p>命中它说明同一货主在同一国家、同一州省下的同类型税号已经存在，
     * 而不是「号码重复」——两行号码不同但类型相同同样会被拒。
     */
    private static final String OWNER_TAX_UNIQUE_INDEX = "ux_owner_tax_unique";

    /** {@code owner_tax_registration} 的部分唯一索引，口径是未删除且 {@code is_default} 的行，键为 {@code (owner_id, country)}。 */
    private static final String OWNER_TAX_DEFAULT_UNIQUE_INDEX = "ux_owner_tax_default";

    /**
     * 主联系人冲突的提示：必须点明「已有主联系人」，并把下一步动作写出来。
     *
     * <p>只说「数据冲突」时操作人拿不到任何可执行的信息：他不知道是别人刚刚设了主联系人，
     * 也就不会想到先刷新列表再重试。
     */
    private static final String PRIMARY_CONTACT_TAKEN_MESSAGE = "该货主已有主联系人，请刷新列表后重新提交";

    /**
     * 主负责人冲突的提示：必须点明「已有主负责人」，并把下一步动作写出来。
     *
     * <p>与主联系人同源：操作人看到「数据冲突」时无从判断该改什么，只有告诉他别人刚占了这个位置、
     * 要先刷新看到最新的负责人，他才不会把同一份请求反复提交。
     */
    private static final String PRIMARY_ASSIGNEE_TAKEN_MESSAGE = "该货主已有主负责人，请刷新后重试";

    /**
     * 重复登记的提示：必须点明「同一国家/州省下的同类型」，而不是「税号重复」。
     *
     * <p>唯一索引的键里没有 {@code tax_number}——两行号码不同但类型相同同样被拒。说成「税号重复」时，
     * 操作人会去核对自己填的号码，改了半天号码仍然被拒；说清是「同类型已登记」，他才知道该改类型、
     * 该改州省，还是该去编辑已有那条。
     */
    private static final String TAX_REGISTRATION_TAKEN_MESSAGE = "该货主在此国家/州省下已有同类型税号";

    /**
     * 默认税号冲突的提示：必须点明「该国家已有默认税号」，并把下一步动作写出来。
     *
     * <p>与主联系人、主负责人同源：只说「数据冲突」时操作人拿不到任何可执行的信息。
     * 这里还要带「刷新后重试」：冲突只可能来自另一个人刚刚把同国另一条设成了默认，
     * 刷新之前他看到的页签是旧数据，再提交一次还是会撞。
     */
    private static final String DEFAULT_TAX_TAKEN_MESSAGE = "该货主在该国家已有默认税号，请刷新后重试";

    private OwnerConstraintTranslator() {
    }

    /**
     * 翻译建档时违反的库约束。
     *
     * <p>三个唯一索引都可能命中：编码由取号引擎给出（撞号说明取号有缺陷），SKU 前缀与外部码来自入参。
     *
     * <p><b>占用方反查只在前缀冲突时才调用：</b>它是「已经确定要报 40905」之后的一次补充查询，
     * 不是判重的前置查询。写成入参是 {@link Function} 而不是直接传编码，就是为了让另外两种冲突
     * 不会白白多跑一次库。
     *
     * <p><b>反查能执行，依赖建档链路不开事务：</b>PostgreSQL 在同一事务里撞死一条语句后会把整个事务
     * 置为 aborted，后续查询一律失败。建档是单条 {@code INSERT}（自动提交），失败语句只回滚它自己，
     * 因此这里补一次读取是安全的；若给 {@code OwnerCreationService#create} 加上 {@code @Transactional}，
     * 这条反查会稳定报「current transaction is aborted」，那时必须改成在事务外补提示。
     *
     * @param e              数据库抛出的完整性约束冲突
     * @param skuPrefix      本次提交的 SKU 前缀原文，用于把值带进 40905 的提示；调用方保证非空
     * @param occupierLookup 按前缀反查占用方编码；只在命中前缀唯一索引时调用，允许返回空
     * @return 已翻译的业务异常；约束未知时为原始异常，由全局异常处理归到 50000
     */
    static RuntimeException translateInsert(DataIntegrityViolationException e,
                                            String skuPrefix,
                                            Function<String, Optional<String>> occupierLookup) {
        String cause = causeOf(e);
        if (cause == null) {
            return e;
        }
        if (cause.contains(OWNER_CODE_UNIQUE_INDEX)) {
            // 提示由错误码自带的文案给：编码不是用户填的，他唯一能做的就是重新提交
            return new BizException(ErrorCode.OWNER_CODE_TAKEN);
        }
        if (cause.contains(OWNER_SKU_PREFIX_UNIQUE_INDEX)) {
            return skuPrefixTaken(skuPrefix, occupierLookup);
        }
        return externalCodeTakenOrOriginal(e, cause);
    }

    /**
     * 翻译编辑时违反的库约束。
     *
     * <p>只有外部码这一条：编辑的 SET 列表里没有编码与 SKU 前缀（见 {@code OwnerMapper#updateProfile}），
     * 它们的唯一索引在这条链路上不可能被违反。若真命中了，说明写语句被改坏——那时原样抛出，
     * 让它在 50000 上暴露，比按「前缀已被占用」回一个用户无法理解的提示要好。
     *
     * @param e 数据库抛出的完整性约束冲突
     * @return 已翻译的业务异常；约束未知时为原始异常，由全局异常处理归到 50000
     */
    static RuntimeException translateUpdate(DataIntegrityViolationException e) {
        String cause = causeOf(e);
        if (cause == null) {
            return e;
        }
        return externalCodeTakenOrOriginal(e, cause);
    }

    /**
     * 翻译联系人写入撞上的主联系人唯一索引，其余约束原样返回。
     *
     * <p><b>为什么会撞上：</b>「先清旧主、再写新主」是两个步骤，并发下两笔「设我为主」可能都越过了
     * 清理步骤，后写的一笔被 {@code ux_owner_contact_primary} 拒绝。这是一次能被如实解释的正常失败
     * ——别人先设了主联系人——因此翻译成业务错误，而不是让人在 50000 上看到一条 SQL 报错。
     *
     * <p><b>为什么复用泛化的 {@link ErrorCode#CONFLICT} 而不新增专用码：</b>该码的语义正是
     * 「当前状态与提交冲突，重试可能成功」，与这次失败吻合；为一个低频并发场景扩错误码表，
     * 会让前端多一个只在极少数时刻才出现的分支。区分度由提示文案承担，见 {@link #PRIMARY_CONTACT_TAKEN_MESSAGE}。
     * 联系人写入还受外键与 CHECK 约束，那些不属于本用例可解释的失败，仍按类注释的原则原样上抛。
     *
     * <p><b>调用方必须只把仓储写入那一行包进 {@code try}：</b>本方法假定入参异常只可能来自那一条写语句。
     * 连审计等其他写入一起包住，一次「审计表写不进去」就会被报成「该货主已有主联系人」——那是谎报，
     * 而且会把真正的存储故障藏起来。
     *
     * <p><b>这里不查库：</b>翻译只需要约束名。因此它与 {@link #translateInsert} 的前缀反查不同——
     * 即使这条写入发生在事务里、PostgreSQL 已把事务置为 aborted，本方法也不会再发出一条必然失败的查询。
     *
     * @param e 数据库抛出的完整性约束冲突
     * @return 已翻译的业务异常；约束未知时为原始异常，由全局异常处理归到 50000
     */
    static RuntimeException translateContactWrite(DataIntegrityViolationException e) {
        String cause = causeOf(e);
        if (cause == null) {
            return e;
        }
        if (cause.contains(OWNER_CONTACT_PRIMARY_UNIQUE_INDEX)) {
            return new BizException(ErrorCode.CONFLICT, PRIMARY_CONTACT_TAKEN_MESSAGE);
        }
        return e;
    }

    /**
     * 翻译负责客服写入撞上的角色唯一索引，其余约束原样返回。
     *
     * <p><b>为什么会撞上：</b>{@code replace} 的语义是「删同角色旧行 + 插新行」，并发下两笔「改主负责人」
     * 可能都走完了删除那一步（各自删掉自己看到的那一行，删除命中零行也算无操作），后写的一笔插入时
     * 被 {@code ux_owner_assignee_role} 拒绝。这是一次能被如实解释的正常失败——别人先改了同一角色——
     * 因此翻译成业务错误，而不是让人在 50000 上看到一条 SQL 报错。
     *
     * <p><b>为什么复用泛化的 {@link ErrorCode#CONFLICT} 而不新增专用码：</b>该码的语义正是
     * 「当前状态与提交冲突，重试可能成功」，与这次失败吻合；为一个低频并发场景扩错误码表，
     * 会让前端多一个只在极少数时刻才出现的分支。区分度由提示文案承担，见
     * {@link #PRIMARY_ASSIGNEE_TAKEN_MESSAGE}。负责人写入还受外键与 {@code ck_assignee_relation} 约束，
     * 那些不属于本用例可解释的失败，仍按类注释的原则原样上抛。
     *
     * <p><b>调用方必须只把主角色那一次 {@code replace} 包进 {@code try}：</b>本方法假定入参异常只可能来自
     * 那一条写语句，而提示说的是「已有主负责人」。把副负责人那次写入、或审计等其他写入一起包住，
     * 一次「审计表写不进去」就会被报成「该货主已有主负责人」——那是谎报，而且会把真正的存储故障藏起来。
     * 副负责人撞上同一索引时也不走这里：它的失败不叫「已有主负责人」，按未知约束上抛比编一句假话好。
     *
     * <p><b>这里不查库：</b>翻译只需要约束名。因此与 {@link #translateInsert} 的前缀反查不同——
     * 这条写入发生在事务里，撞约束之后 PostgreSQL 已把事务置为 aborted，本方法不会再发出一条必然失败的查询。
     *
     * @param e 数据库抛出的完整性约束冲突
     * @return 已翻译的业务异常；约束未知时为原始异常，由全局异常处理归到 50000
     */
    static RuntimeException translateAssigneeWrite(DataIntegrityViolationException e) {
        String cause = causeOf(e);
        if (cause == null) {
            return e;
        }
        if (cause.contains(OWNER_ASSIGNEE_ROLE_UNIQUE_INDEX)) {
            return new BizException(ErrorCode.CONFLICT, PRIMARY_ASSIGNEE_TAKEN_MESSAGE);
        }
        return e;
    }

    /**
     * 翻译税号写入撞上的两条唯一索引，其余约束原样返回。
     *
     * <p><b>{@code ux_owner_tax_unique} 与 {@code ux_owner_tax_default} 的处理并列，因为提示不同：</b>
     * 前者说的是「这个组合已经登记过」，后者说的是「该国家已有默认税号」。两者都回 40900
     * （{@link ErrorCode#CONFLICT} 的语义是「当前状态与提交冲突，重试可能成功」），
     * 区分度由文案承担；把两者合成一句「数据冲突」，操作人就无从判断是改类型还是先刷新。
     *
     * <p><b>为什么会在并发下撞上：</b>{@code ux_owner_tax_default} 是「先清旧默认、再写新默认」
     * 两个步骤，两笔并发的「设为默认」可能都越过了清理步骤，后写的一笔被拒。{@code ux_owner_tax_unique}
     * 则是两笔并发的新增提交了同一组合——两条请求只有一条能生效，另一条刷新后再决定改类型还是编辑已有那条。
     * 两者都是能被如实解释的正常失败，因此不按 50000 暴露，也不为此新增专用码
     * （只在低频并发场景出现的码，会让前端多一个几乎走不到的分支）。
     *
     * <p><b>调用方必须只把仓储写入那一行包进 {@code try}：</b>本方法假定入参异常只可能来自那一条写语句。
     * 清旧默认、审计等其他写入各自含义不同，一起包住会把「审计表写不进去」报成「已有同类型税号」——
     * 那是谎报，也会把真正的存储故障藏起来。税号写入还受外键与 {@code ck_tax_country} 约束，
     * 那些不属于本用例可解释的失败，仍按类注释的原则原样上抛。
     *
     * <p><b>这里不查库：</b>翻译只需要约束名，因此不像 {@link #translateInsert} 那样反查占用方——
     * 这条写入发生在事务里，撞约束之后 PostgreSQL 已把事务置为 aborted，再发一条查询必然失败。
     *
     * @param e 数据库抛出的完整性约束冲突
     * @return 已翻译的业务异常；约束未知时为原始异常，由全局异常处理归到 50000
     */
    static RuntimeException translateTaxWrite(DataIntegrityViolationException e) {
        String cause = causeOf(e);
        if (cause == null) {
            return e;
        }
        if (cause.contains(OWNER_TAX_UNIQUE_INDEX)) {
            return new BizException(ErrorCode.CONFLICT, TAX_REGISTRATION_TAKEN_MESSAGE);
        }
        if (cause.contains(OWNER_TAX_DEFAULT_UNIQUE_INDEX)) {
            return new BizException(ErrorCode.CONFLICT, DEFAULT_TAX_TAKEN_MESSAGE);
        }
        return e;
    }

    /**
     * 取最具体的原因消息。
     *
     * @param e 数据库抛出的完整性约束冲突
     * @return 原因消息；取不到时为空，调用方据此原样上抛
     */
    private static String causeOf(DataIntegrityViolationException e) {
        return e.getMostSpecificCause().getMessage();
    }

    /**
     * 翻译外部码冲突，其余约束原样返回。
     *
     * @param e     数据库抛出的完整性约束冲突
     * @param cause 最具体的原因消息
     * @return 已翻译的业务异常；约束未知时为原始异常
     */
    private static RuntimeException externalCodeTakenOrOriginal(DataIntegrityViolationException e, String cause) {
        if (cause.contains(OWNER_EXTERNAL_CODE_UNIQUE_INDEX)) {
            return new BizException(ErrorCode.OWNER_EXTERNAL_CODE_TAKEN);
        }
        return e;
    }

    /**
     * 构造前缀冲突的业务异常，提示里带上被占用的前缀原文与占用方编码。
     *
     * <p>不带前缀原文的话，用户只知道「这个前缀不行」，而 4 位字符的空间里能换的组合他无从判断——
     * 尤其是当前缀是从外部系统抄过来、对方只给了一个编号的时候。
     *
     * <p><b>为什么还要带占用方编码：</b>规格要求「拒绝保存并指出占用方编码」。被占用的那个货主
     * 往往属于别的业务线，操作人自己看不到它；给出编码，他才能去找对应的人核对是不是抄错了前缀，
     * 而不是在一张自己无权查看的表上反复试探。
     *
     * <p><b>反查为空时退回只带前缀的原文案：</b>前缀在这一刻确实被占用（数据库刚拒绝过），
     * 查不到只可能是那一行在两次调用之间被物理删除。为这种情形抛异常或硬编一个「未知占用方」，
     * 会把一次本可解释的拒绝变成一次错误——文案少一个括号，代价远小于一个 50000。
     *
     * @param skuPrefix      被占用的前缀原文
     * @param occupierLookup 按前缀反查占用方编码
     * @return 带前缀原文（以及可查到时带占用方编码）的 40905
     */
    private static BizException skuPrefixTaken(String skuPrefix,
                                               Function<String, Optional<String>> occupierLookup) {
        String message = ErrorCode.OWNER_SKU_PREFIX_TAKEN.defaultMessage() + "：" + skuPrefix;
        return occupierLookup.apply(skuPrefix)
                // 占用方是已软删除的货主时同样会被查出来：前缀是全表唯一，删除不释放占用。
                // 提示里因此不加「（已删除）」这类限定——说清是谁占的就够了，用户不需要知道它的状态
                .map(occupier -> new BizException(ErrorCode.OWNER_SKU_PREFIX_TAKEN,
                        message + "（占用方 " + occupier + "）"))
                .orElseGet(() -> new BizException(ErrorCode.OWNER_SKU_PREFIX_TAKEN, message));
    }
}
