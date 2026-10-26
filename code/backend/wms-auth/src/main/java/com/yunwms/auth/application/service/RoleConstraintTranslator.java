package com.yunwms.auth.application.service;

import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 角色写入侧的库约束翻译：把数据库抛出的完整性约束冲突翻成可展示的业务错误。
 *
 * <p>为什么单独成类：建角色与编辑角色都会撞上同一条菜单外键，两处各写一遍「约束名 → 错误码」的映射，
 * 改一处忘一处时表现成「同样的入参，一个接口回 400、另一个回 50000」。规则必须有唯一所有者。
 *
 * <p>按约束名匹配而不是按 SQLState：违反唯一索引、外键、CHECK 的 SQLState 都是 23xxx，
 * 区分不了是哪一处；约束名不会随 {@code lc_messages} 或其他区域设置改变，是唯一稳定的标识。
 *
 * <p>不认识的约束原样返回：那是本用例的假设被打破了（例如新加了 CHECK），猜一个错误码返回给用户，
 * 等于把「我们不知道自己违反了哪条规则」伪装成一次正常的拒绝。
 */
final class RoleConstraintTranslator {

    /** {@code sys_role.code} 的全表唯一索引，不带 {@code deleted_at} 条件。 */
    private static final String ROLE_CODE_UNIQUE_INDEX = "ux_sys_role_code";

    /** {@code sys_role_menu.menu_id} 的外键：插入时拦下不存在的菜单或权限点。 */
    private static final String ROLE_MENU_FOREIGN_KEY = "sys_role_menu_menu_id_fkey";

    private RoleConstraintTranslator() {
    }

    /**
     * 翻译角色写入违反的库约束。
     *
     * @param e 数据库抛出的完整性约束冲突
     * @return 已翻译的业务异常；约束未知时为原始异常，由全局异常处理归到 50000
     */
    static RuntimeException translate(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        if (cause == null) {
            return e;
        }
        if (cause.contains(ROLE_CODE_UNIQUE_INDEX)) {
            // 40902 而不是泛化的 40900：前端要能把这个提示挂在编码字段上；
            // 提示语里点明「含已删除的角色」，因为软删除之后编码仍被唯一索引占着
            return new BizException(ErrorCode.ROLE_CODE_TAKEN);
        }
        if (cause.contains(ROLE_MENU_FOREIGN_KEY)) {
            // 与编码冲突相反：这里是入参引用了一个不存在的菜单或权限点，改一个 id 就能过，
            // 因此归到 400 段位而不是「必须先去处理已有数据」的 409
            return BizException.badRequest("指定的菜单或权限点不存在");
        }
        return e;
    }
}
