package com.yunwms.master.application.service;

import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;

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

    private OwnerConstraintTranslator() {
    }

    /**
     * 翻译建档时违反的库约束。
     *
     * <p>三个唯一索引都可能命中：编码由取号引擎给出（撞号说明取号有缺陷），SKU 前缀与外部码来自入参。
     *
     * @param e         数据库抛出的完整性约束冲突
     * @param skuPrefix 本次提交的 SKU 前缀原文，用于把值带进 40905 的提示；调用方保证非空
     * @return 已翻译的业务异常；约束未知时为原始异常，由全局异常处理归到 50000
     */
    static RuntimeException translateInsert(DataIntegrityViolationException e, String skuPrefix) {
        String cause = causeOf(e);
        if (cause == null) {
            return e;
        }
        if (cause.contains(OWNER_CODE_UNIQUE_INDEX)) {
            // 提示由错误码自带的文案给：编码不是用户填的，他唯一能做的就是重新提交
            return new BizException(ErrorCode.OWNER_CODE_TAKEN);
        }
        if (cause.contains(OWNER_SKU_PREFIX_UNIQUE_INDEX)) {
            return skuPrefixTaken(skuPrefix);
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
     * 构造前缀冲突的业务异常，提示里带上被占用的前缀原文。
     *
     * <p>不带值的话，用户只知道「这个前缀不行」，而 4 位字符的空间里能换的组合他无从判断——
     * 尤其是当前缀是从外部系统抄过来、对方只给了一个编号的时候。
     *
     * @param skuPrefix 被占用的前缀原文
     * @return 带具体前缀的 40905
     */
    private static BizException skuPrefixTaken(String skuPrefix) {
        return new BizException(ErrorCode.OWNER_SKU_PREFIX_TAKEN,
                ErrorCode.OWNER_SKU_PREFIX_TAKEN.defaultMessage() + "：" + skuPrefix);
    }
}
