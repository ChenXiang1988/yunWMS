package com.yunwms.auth.application.query;

import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditPage;
import com.yunwms.auth.domain.port.AuditQuery;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计日志的读取用例：按对象追溯。
 *
 * <p>本类只做三件事：判必填项、把分页参数判在合法范围内、把对象标识归一（全空白按未填处理）。
 * 查询本身全部下沉到 {@link AuditQuery}，与 {@code RoleQueryService}、{@code UserQueryService} 同构。
 *
 * <p><b>没有删除与修改用例</b>：规格要求审计日志只追加不可修改，因此本模块不存在「删一条日志」
 * 这样的用例——能力上不提供，比提供一个「会被校验拒绝」的入口可靠。
 */
@Service
public class AuditQueryService {

    /** 每页条数上限。与 {@code docs/api-convention.md} 第四节一致：超出必须拒绝，不得静默截断。 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 每页条数默认值，与接口约定里的示例一致。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int DEFAULT_PAGE_NO = 1;

    private final AuditQuery auditQuery;

    public AuditQueryService(AuditQuery auditQuery) {
        this.auditQuery = auditQuery;
    }

    /**
     * 按对象取一页审计日志，按操作时间倒序。
     *
     * <p>分页参数的范围判定写在这里而不是接口层的 {@code @Min} / {@code @Max}：方法参数校验失败抛的是
     * {@code ConstraintViolationException}，而 {@code GlobalExceptionHandler} 里没有它的分支，
     * 会被兜底成 50000。本批不改 {@code wms-web}（不在路径白名单内），因此在这里抛 {@code BizException}，
     * 让错误码稳定落在 40000。与 {@code RoleQueryService} 的 Javadoc 同一理由。
     *
     * @param bizType  审计对象类型；必填
     * @param bizId    对象标识；必填，两侧空白会被裁掉，全空白按未填处理
     * @param pageNo   页码；为 {@code null} 时取 1
     * @param pageSize 每页条数；为 {@code null} 时取 20
     * @return 该对象的一页日志，含总数，按操作时间倒序
     * @throws BizException 对象类型或对象标识未填（40000）、页码小于 1（40000）、
     *                      每页条数不在 1 到 200 之间（40000）
     */
    @Transactional(readOnly = true)
    public AuditPage page(AuditBizType bizType, String bizId, Integer pageNo, Integer pageSize) {
        if (bizType == null) {
            throw BizException.badRequest("审计对象类型不能为空");
        }
        // 必填项先判：缺 bizId 时按「全表分页」跑一遍没有意义，而它正是这里要拦住的事
        String normalizedBizId = stripToNull(bizId);
        if (normalizedBizId == null) {
            throw BizException.badRequest("审计对象标识不能为空");
        }

        int resolvedPageNo = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageNo < 1) {
            throw BizException.badRequest("页码必须从 1 开始");
        }
        if (resolvedPageSize < 1 || resolvedPageSize > MAX_PAGE_SIZE) {
            // 拒绝而不是截断：静默截成 200 时前端会把「只看到 200 条」当成「一共 200 条」
            throw BizException.badRequest("每页条数必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        return auditQuery.findByObject(bizType, normalizedBizId, resolvedPageNo, resolvedPageSize);
    }

    /**
     * 去掉两侧空白，空串按未填处理。
     *
     * <p>不裁空白时，一个只有空格的标识会变成 {@code biz_id = ' '}：它永远查不到任何记录，
     * 调用方会以为「这个对象没有变更历史」，而实际是查询条件里多了不可见字符。
     *
     * @param value 原始值，可为 {@code null}
     * @return 裁剪后的值；{@code null}、空串或全空白时为 {@code null}
     */
    private static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
