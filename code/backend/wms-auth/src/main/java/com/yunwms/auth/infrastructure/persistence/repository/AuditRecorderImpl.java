package com.yunwms.auth.infrastructure.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.SourcePortal;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.infrastructure.persistence.entity.SysAuditLogEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysAuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * {@link AuditRecorder} 的实现：把一条日志内容补全操作人信息后落库。
 *
 * <p><b>为什么操作人与来源端在这里取。</b>端口刻意不接收这些参数（理由见 {@link AuditRecorder}），
 * 因此本类是唯一知道「当前请求是谁、从哪个端来」的地方：会话来自 {@link CurrentSession}，
 * 来源地址与来源端来自当前 Servlet 请求。二者都取不到时仍然写入——系统内部触发（定时任务、
 * 回调）没有请求上下文，但那次操作照样要留痕，否则「谁在什么时候改了这条数据」的链条上会出现
 * 一个无声的缺口。
 *
 * <p><b>来源端先按请求路径前缀判定，判不出来时才回退到当前会话的主体类型。</b>路径能判定时它就是唯一依据：
 * 主体类型回答的是「这个账号能进哪个端」，与「这次请求从哪个端发出」不是同一件事；推导在跨端访问放开的那天
 * 会静默出错，而写进审计的错误结论事后无法与真实记录区分（见 {@code V038} 的注释）。
 *
 * <p><b>回退只在路径不含任何端信息时生效，不会覆盖路径给出的结论。</b>两端共用 {@code /api/auth/login}
 * 一个 URL，而登录这条事实又必须写出端（规格「审计日志」要求每条日志含来源端）。此时会话正由本次请求
 * 刚建立，会话里的主体类型是唯一可用的依据：{@code STAFF} 只可能从仓网端登录，{@code OWNER} 只可能从
 * 货主端登录。登录失败没有会话，回退同样取不到，来源端如实留空，由调用方在摘要里说明。
 *
 * <p><b>回退只读会话，不读客户端自称的端。</b>{@code Referer}、{@code Origin} 与客户端传来的端标识
 * 都能被请求方随意填写，把它们写进审计等于让被审计者自己填来源。宁可留空并说明，也不要一个
 * 无法与真实记录区分的结论。
 *
 * <p><b>写入加入调用方的事务</b>（{@code REQUIRED}）：审计与业务变更要么一起成功、要么一起回滚。
 * 单独提交会得到「业务改了但没留痕」或「留了痕但业务没改」两种残局。
 */
@Repository
public class AuditRecorderImpl implements AuditRecorder {

    /** 仓网端接口的路径前缀。 */
    private static final String CONSOLE_PATH_PREFIX = "/api/console/";

    /** 货主端接口的路径前缀。 */
    private static final String PORTAL_PATH_PREFIX = "/api/portal/";

    /**
     * 没有会话时写入的姓名。
     *
     * <p>{@code operator_name} 是 {@code NOT NULL} 列，而「取不到人」不等于「不记这一条」：
     * 审计的价值在于记下了。写这个值是在陈述事实——这条日志不是某个登录用户触发的。
     */
    private static final String SYSTEM_OPERATOR_NAME = "系统";

    /**
     * 有会话、但会话里没有姓名时写入的姓名。
     *
     * <p>只有一种情形会走到这里：会话由**本功能上线之前**的版本签发（令牌有效期 2 小时，跨过部署即可
     * 自愈）。不写「系统」是因为那时确实有一个真人，写成系统会永久留下一条错误的归因；
     * {@code operator_id} 仍在，责任人可查。
     */
    private static final String UNKNOWN_OPERATOR_NAME = "未知操作人";

    private final SysAuditLogMapper sysAuditLogMapper;
    private final CurrentSession currentSession;
    private final ObjectMapper objectMapper;

    public AuditRecorderImpl(SysAuditLogMapper sysAuditLogMapper,
                             CurrentSession currentSession,
                             ObjectMapper objectMapper) {
        this.sysAuditLogMapper = sysAuditLogMapper;
        this.currentSession = currentSession;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void record(AuditEntry entry) {
        HttpServletRequest request = currentRequestOrNull();
        // 会话读不出人时 currentUserId 为空（未登录，或线程不在请求里），此时操作人落空、姓名取系统值：
        // 审计的价值在于记下了，而不是因为取不到人就不记
        OptionalLong currentUserId = currentSession.currentUserId();
        Long operatorId = currentUserId.isPresent() ? currentUserId.getAsLong() : null;

        SysAuditLogEntity row = new SysAuditLogEntity();
        // 枚举入库显式存 name()：库里该列是 varchar 且有 CHECK，存 ordinal 会在枚举顺序调整后静默错位
        row.setBizType(entry.bizType().name());
        row.setBizId(entry.bizId());
        row.setAction(entry.action().name());
        row.setOperatorId(operatorId);
        row.setOperatorName(resolveOperatorName(operatorId));
        row.setOperatorIp(request == null ? null : request.getRemoteAddr());
        SourcePortal sourcePortal = resolveSourcePortal(request);
        row.setSourcePortal(sourcePortal == null ? null : sourcePortal.name());
        row.setSummary(entry.summary());
        row.setBeforeValue(toJson(entry.beforeValue(), entry, "变更前快照"));
        row.setAfterValue(toJson(entry.afterValue(), entry, "变更后快照"));
        sysAuditLogMapper.append(row);
    }

    /**
     * 取当前请求；没有请求上下文（系统内部触发、异步线程）时为空。
     *
     * @return 当前 Servlet 请求，可能为 {@code null}
     */
    private static HttpServletRequest currentRequestOrNull() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        return servletAttributes.getRequest();
    }

    /**
     * 判定这条日志的来源端：路径前缀优先，判不出来时回退到会话的主体类型。
     *
     * <p>没有请求上下文时直接返回空，连会话都不问：定时任务、回调所在的线程没有「端」这个概念，
     * 那个线程上也拿不到 Sa-Token 上下文，问了只会得到一条与本次操作无关的推断。
     *
     * @param request 当前请求；为 {@code null} 时表示没有请求上下文
     * @return 来源端；无请求上下文、路径不含端信息且会话里没有主体类型时为 {@code null}
     */
    private SourcePortal resolveSourcePortal(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        SourcePortal fromPath = sourcePortalOfPath(request);
        return fromPath != null ? fromPath : sourcePortalOfSession();
    }

    /**
     * 按请求路径前缀判定来源端。
     *
     * <p>先减掉 context path 再比对：应用被部署在子路径下时，{@code getRequestURI()} 会带上它
     * （{@code /wms/api/console/...}），不减掉的话每一条日志的来源端都会静默变成空值——
     * 而空值在语义上是「这次操作没有端的概念」，与「没认出来」混在一起无法分辨。
     *
     * @param request 当前请求，调用方保证非空
     * @return 来源端；路径不属于两端时为 {@code null}
     */
    private static SourcePortal sourcePortalOfPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String path = contextPath == null || contextPath.isEmpty()
                ? request.getRequestURI()
                : request.getRequestURI().substring(contextPath.length());
        if (path.startsWith(CONSOLE_PATH_PREFIX)) {
            return SourcePortal.CONSOLE;
        }
        if (path.startsWith(PORTAL_PATH_PREFIX)) {
            return SourcePortal.PORTAL;
        }
        return null;
    }

    /**
     * 路径判不出来时的回退：按当前会话的主体类型推断端。
     *
     * <p>映射用 switch 表达式穷举，不写 {@code default}：将来若出现第三种主体类型，这里会编译失败，
     * 逼人明确它从哪个端登录，而不是悄悄落到某个默认值上——一条推断错的来源端写进审计后，
     * 事后无法与真实记录区分。
     *
     * @return 来源端；当前请求没有有效会话、或会话里没有主体类型时为 {@code null}
     */
    private SourcePortal sourcePortalOfSession() {
        Optional<SubjectType> subjectType = currentSession.currentSubjectType();
        if (subjectType.isEmpty()) {
            return null;
        }
        return switch (subjectType.get()) {
            case STAFF -> SourcePortal.CONSOLE;
            case OWNER -> SourcePortal.PORTAL;
        };
    }

    /**
     * 取写入用的操作人姓名。
     *
     * <p>姓名取自登录时写进会话的快照（见 {@code SaTokenSessionTokenIssuer} 的写入点），
     * 而不是每次写入回查 {@code sys_user}：审计要记的是「谁在什么时候做了什么」，
     * 快照能保证人员之后改名、甚至被逻辑删除都不影响历史记录；回查还会让每条审计多一次 SELECT，
     * 且账号已被删除时查不到人，只能退回一个更不准确的占位值。
     *
     * @param operatorId 当前会话的用户标识；没有会话时为 {@code null}
     * @return 姓名，恒不为 {@code null}
     */
    private String resolveOperatorName(Long operatorId) {
        Optional<String> displayName = currentSession.currentUserDisplayName();
        if (displayName.isPresent()) {
            return displayName.get();
        }
        return operatorId == null ? SYSTEM_OPERATOR_NAME : UNKNOWN_OPERATOR_NAME;
    }

    /**
     * 把快照序列化成 {@code jsonb} 文本。
     *
     * <p>失败时带上对象类型与标识再抛：出问题的是某一次写入的具体快照，没有这两个值就只能翻日志找
     * 「是哪条业务在写审计」。原始异常保留为 cause，堆栈里仍有 Jackson 的定位信息。
     *
     * @param snapshot 快照；为 {@code null} 时返回 {@code null}，落库为 SQL NULL（新增没有「变更前」）
     * @param entry    所属日志内容，用于失败信息
     * @param label    快照名，用于失败信息
     * @return JSON 文本，或 {@code null}
     * @throws IllegalStateException 快照无法序列化成 JSON 对象
     */
    private String toJson(Map<String, Object> snapshot, AuditEntry entry, String label) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("审计日志的" + label + "无法序列化：对象类型="
                    + entry.bizType() + "，对象标识=" + entry.bizId(), ex);
        }
    }
}
