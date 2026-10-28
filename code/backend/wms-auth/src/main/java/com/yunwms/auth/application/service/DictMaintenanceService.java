package com.yunwms.auth.application.service;

import com.yunwms.auth.domain.model.DictStatus;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.DictMaintenance;
import com.yunwms.auth.domain.port.DictQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 字典项维护的用例：新增、编辑、启停、逻辑删除。
 *
 * <p>四条用例的骨架相同：取操作人 →（新增时判类型存在 / 其余三条判项存在）→ 落库。判存在放在落库之前，
 * 是因为写入语句清一色带 {@code deleted_at IS NULL}——对一条不存在的行，UPDATE 既不报错也不改动，
 * 接口会回「成功」而库里什么都没变。这种「点了保存、页面刷新后还是旧值」的失败没有任何日志可查，
 * 必须在用例层把它变成一个 404。
 *
 * <p><b>新增时先判 {@code dictType} 存在（40000）。</b>{@code sys_dict_item.dict_type} 上的外键确实拦得住
 * 孤儿项，但外键只会给出约束名，变不成「字典类型不存在：COUNTRY」这种能指路的提示；而且报出来的是
 * 一次 500 端位的失败，用户会以为是系统坏了。判存在的代价是一条 {@code EXISTS}，换来一条可操作的提示。
 * 反过来说，读侧对同一个错误的处理是 404（见 {@code DictQueryService#items}）：读一个不存在的类型是
 * 「路径指向的东西不在」，写到一个不存在的类型是「这次提交的参数不对」，两者不该共用一个码。
 *
 * <p><b>{@code value} 唯一冲突翻译成 40900，并在提示里带上类型与值原文。</b>
 * {@code ux_sys_dict_item_value} 是 {@code (dict_type, value) WHERE deleted_at IS NULL} 的部分唯一索引，
 * 同一类型下同值的未删除项只能有一个。这条唯一性<b>不在本类预判</b>：服务层「先查再插」在并发下无效，
 * 两个请求会同时查到「没有」，一个成功一个拿到数据库异常，与不查的痛苦相同却多一次往返
 * （与 {@code RoleCreationService}、{@code UserUpdateService} 同一口径）。部分索引的含义也由数据库解释，
 * 应用层再写一遍「已删除的同类项不算占用」，改索引那天就会与库不一致。
 * 不单独成码而用泛化的 {@link ErrorCode#CONFLICT}：提示语本身带类型与值原文，照它改就能过；
 * 将来字典页要把这条错误挂到 {@code value} 字段上时，再按 {@code LOGIN_NAME_TAKEN} 的模式单独成码。
 *
 * <p><b>删除用两条 404 区分两种「已经不在」。</b>删前判存在答的是「你要删的东西本来就没有」；
 * {@link DictMaintenance#softDelete} 返回 {@code false} 答的是「读到之后被别人删掉了，刷新再看」。
 * 后者不是并发冲突（{@code docs/coding.md}「持久化与副作用」要求非插入写入校验实际结果），
 * 但对用户而言是同一件需要重新操作的事，因此共用 40400，只把提示语写准。
 *
 * <p><b>启停是幂等的。</b>把 ACTIVE 改成 ACTIVE 是一次合法的无操作，不报错：管理员重复点「停用」
 * 不该收到一条错误，与 {@code UserStatusChangeService} 对同状态重放的处理一致。
 *
 * <p><b>本类不留痕，这是本批有意识的取舍。</b>审计的类目枚举 {@code AuditBizType} 目前只有
 * {@code OWNER} / {@code OWNER_CONTACT} / {@code OWNER_TAX} / {@code SKU} / {@code USER} / {@code ROLE}
 * 六项，没有字典；为字典项维护补一类等于把这次改动扩进审计模块，而本批的范围只有用例层。
 * 规格里「审计日志」那条只列举了主数据、权限变更、登录与标签打印，字典项不在其中，这是可以暂缓的前提。
 * 但风险必须写在明处：改错一个字典项（例如把 {@code US} 的中文名改成「加拿大」）之后，库里只剩
 * {@code updated_by} 与 {@code updated_at} 两个字段——查不到改前的值，也分不清这次改动是「改标签」
 * 还是「顺手停了用」。补留痕的前提是先有字典类目，届时四条用例各加一条 {@code AuditEntry} 即可
 * （写法见 {@code RoleModificationService}）。
 *
 * <p><b>四条用例都不开事务。</b>每条只有一条写语句，没有需要与它同生共死的第二件事（本批无审计）；
 * 套一个 {@code @Transactional} 只会让人以为这里还有别的东西需要原子提交。判存在的那次读因此与写入
 * 不在同一个快照里，这是刻意的：写语句各自带 {@code deleted_at IS NULL}，读后被人删掉时，
 * 编辑与启停退化成一次无操作（与端口约定一致），删除由返回值给出 404。
 *
 * <p><b>{@code dictType} 与 {@code value} 在本类既不判空也不裁剪。</b>必填与超长由入口的声明式校验给出，
 * 本类再判一次就是同一份数据校验两遍（{@code docs/coding.md}「校验与异常」）；绕过 HTTP 直接调用时
 * 由外键与列约束兜底。不裁剪的理由是这两者是业务数据里存的引用键：把 {@code " US "} 悄悄裁成
 * {@code "US"} 会让库里的值与提交的值不一致，而读侧按原值精确匹配，两边对不上时最难排查。
 *
 * @see DictMaintenance
 */
@Service
public class DictMaintenanceService {

    /** {@code sys_dict_item} 上 {@code (dict_type, value) WHERE deleted_at IS NULL} 的部分唯一索引名。 */
    private static final String DICT_ITEM_VALUE_UNIQUE_INDEX = "ux_sys_dict_item_value";

    private final DictMaintenance dictMaintenance;
    private final DictQuery dictQuery;
    private final CurrentSession currentSession;

    public DictMaintenanceService(DictMaintenance dictMaintenance,
                                  DictQuery dictQuery,
                                  CurrentSession currentSession) {
        this.dictMaintenance = dictMaintenance;
        this.dictQuery = dictQuery;
        this.currentSession = currentSession;
    }

    /**
     * 新增一个字典项，落库为启用状态。
     *
     * <p>状态、{@code created_at} 与 {@code updated_at} 都不由本类给：前者的默认值在列上
     * （{@code DEFAULT 'ACTIVE'}），后两者交给库的 {@code DEFAULT now()}，应用侧再取一次时钟
     * 会引入第二个时间来源（见 {@link DictMaintenance#insert}）。
     *
     * @param dictType 类型编码，按库里的值精确匹配
     * @param value    取值，同一类型下的未删除项里唯一；建好之后不可改
     * @param draft    可编辑字段，已由 {@link DictMaintenance.DictItemDraft} 归一并校验
     * @return 数据库生成的字典项标识
     * @throws BizException 当前会话无效（40100）；字典类型不存在或已删除（40000）；
     *                      该类型下已存在同值的未删除项（40900）
     */
    public long create(String dictType, String value, DictMaintenance.DictItemDraft draft) {
        long operatorId = requireOperatorId();

        if (!dictQuery.existsType(dictType)) {
            // 带类型原文：管理员要据此回字典类型页确认到底是拼错了还是那个类型被删了
            throw BizException.badRequest("字典类型不存在：" + dictType);
        }

        try {
            return dictMaintenance.insert(dictType, value, draft, operatorId);
        } catch (DataIntegrityViolationException e) {
            // 冲突时一行都没留下，那次新增没有发生，因此也没有任何后续动作要撤销
            throw translateConstraintViolation(e, dictType, value);
        }
    }

    /**
     * 覆盖字典项的可编辑字段：中文名、英文名、排序号与扩展属性。
     *
     * <p>改不到 {@code dictType}、{@code value} 与 {@code status}：前两个是业务数据的引用键
     * （{@code owner.owner_type} 这类列存的就是 {@code value}），改掉等于让历史数据指向一个不存在的项；
     * 状态走 {@link #changeStatus} 这个独立动作。
     *
     * @param id    字典项标识
     * @param draft 覆盖后的可编辑字段，已由 {@link DictMaintenance.DictItemDraft} 归一并校验
     * @throws BizException 当前会话无效（40100）；字典项不存在或已删除（40400）
     */
    public void update(long id, DictMaintenance.DictItemDraft draft) {
        long operatorId = requireOperatorId();
        requireExistingItem(id);
        dictMaintenance.update(id, draft, operatorId);
    }

    /**
     * 把字典项改成目标状态（启用 / 停用）。
     *
     * <p>与编辑分开成一个动作：停用的语义是「从下拉里消失」，它不该顺带改标签或排序，否则一次误点
     * 会同时改掉两样东西。已处于目标状态时照常返回成功，重复提交不报错。
     *
     * @param id     字典项标识
     * @param status 目标状态；{@code null} 表示调用方没决定要改成什么，由入口的声明式校验拦下，
     *               本类不重复判空
     * @throws BizException 当前会话无效（40100）；字典项不存在或已删除（40400）
     */
    public void changeStatus(long id, DictStatus status) {
        long operatorId = requireOperatorId();
        requireExistingItem(id);
        dictMaintenance.changeStatus(id, status, operatorId);
    }

    /**
     * 逻辑删除字典项：置 {@code deleted_at}，行仍在库里。
     *
     * <p>不做物理删除：{@code value} 可能被历史业务数据引用，删掉记录之后「这条当时写的是哪个取值」
     * 就无从追溯。删除也不看当前状态，已停用的项可以直接删；删掉之后同值可以重新新增
     * （{@code ux_sys_dict_item_value} 带 {@code deleted_at IS NULL} 条件，已删除的行不参与唯一性判定）。
     *
     * @param id 字典项标识
     * @throws BizException 当前会话无效（40100）；字典项不存在或已删除，或读到之后被其他请求删除（40400）
     */
    public void delete(long id) {
        long operatorId = requireOperatorId();
        requireExistingItem(id);

        if (!dictMaintenance.softDelete(id, operatorId)) {
            // 判存在与删除之间被别人删掉了：提示必须与「本来就没有」区分开，否则用户会以为看错了列表
            throw BizException.notFound("字典项已被其他请求删除，请刷新后重试");
        }
    }

    /**
     * 断言字典项存在且未删除。
     *
     * <p>集中成一个方法：编辑、启停、删除三条路径都要判，各写一遍「读一次再判空」迟早有一条漏掉，
     * 而漏掉的那条会把「改了一条不存在的行」当成成功返回。
     *
     * <p>用 {@link DictMaintenance#findById} 而不是读侧端口：判存在本来就是它列出的两个用途之一，
     * 而且它与随后的写语句用同一个 {@code deleted_at IS NULL} 口径，不会出现「判存在时看得见、
     * 写入时改不到」的分歧。{@link DictQuery} 也没有按标识取单条的能力——拿它按类型取全量再过滤，
     * 会把整个类型读进内存。
     *
     * @param id 字典项标识
     * @throws BizException 字典项不存在或已删除（40400）
     */
    private void requireExistingItem(long id) {
        if (dictMaintenance.findById(id).isEmpty()) {
            throw BizException.notFound("字典项不存在");
        }
    }

    /**
     * 把库约束冲突翻译成可展示的业务错误。
     *
     * <p>按约束名匹配而不是按 SQLState：违反唯一索引、外键、CHECK 的 SQLState 都是 23xxx，
     * 区分不了是哪一处；约束名不会随 {@code lc_messages} 或其他区域设置改变。
     *
     * <p>只翻译 {@code ux_sys_dict_item_value}：本用例的可写字段里只有它能撞上约束。
     * 外键 {@code sys_dict_item_dict_type_fkey} 由 {@link #create} 的判存在提前挡住——字典类型在本批
     * 没有维护入口，判存在通过之后没有路径能让那个类型消失；为此再写一条翻译，只是把「不可能发生」
     * 伪装成「已处理」。其余约束（长度、CHECK）同理原样抛出。
     *
     * @param e        数据库抛出的完整性约束冲突
     * @param dictType 本次新增的类型编码，用于拼出可照改的提示
     * @param value    本次新增的取值，用于拼出可照改的提示
     * @return 已翻译的业务异常；约束未知时为原始异常，由全局异常处理归到 50000
     */
    private static RuntimeException translateConstraintViolation(DataIntegrityViolationException e,
                                                                String dictType,
                                                                String value) {
        String cause = e.getMostSpecificCause().getMessage();
        if (cause == null) {
            return e;
        }
        if (cause.contains(DICT_ITEM_VALUE_UNIQUE_INDEX)) {
            return BizException.conflict("字典类型 " + dictType + " 下已存在取值 " + value + "，请换一个取值");
        }
        return e;
    }

    /**
     * 取当前操作人标识，写入 {@code created_by} / {@code updated_by}。
     *
     * <p>取不到会话时不能继续：那会把审计列写成空值，事后无从定位这次维护是谁做的
     * （与 {@code RoleModificationService} 同一处理）。
     *
     * @return 操作人标识
     * @throws BizException 当前会话没有有效登录用户（40100）
     */
    private long requireOperatorId() {
        return currentSession.currentUserId()
                // 鉴权过滤器已拦过未登录请求，走到这里取不到会话只能是账号在请求途中被删除或令牌已失效
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }
}
