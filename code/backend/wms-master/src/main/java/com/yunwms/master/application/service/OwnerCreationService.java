package com.yunwms.master.application.service;

import com.yunwms.master.application.command.CreateOwnerCommand;
import com.yunwms.master.application.dto.CreatedOwner;
import com.yunwms.master.domain.model.NewOwner;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.model.SkuPrefix;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.master.domain.port.DictLookup;
import com.yunwms.master.domain.port.OwnerCodeGenerator;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.master.domain.service.OwnerDictionaryValidator;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 货主建档的用例：保存草稿与直接提交共用一条链路，只有落库状态不同。
 *
 * <p><b>校验顺序固定为「取操作人 → 初始状态 → 领域模型（SKU 前缀、地址、名称等）→ 字典四条 →
 * 取号 → 落库」</b>，顺序本身是语义：
 * <ul>
 *   <li>操作人先取：他既参与鉴权，也是 {@code created_by} 的来源。没有他，后面判得再对也写不成一行；</li>
 *   <li>初始状态先于其他输入：它不是「某个字段的值不对」，而是「这次调用根本不该发生」，
 *       在 {@code POTENTIAL} / {@code TERMINATED} 上继续判前缀与字典，等于把一次接口误用
 *       报成一次字段错误；</li>
 *   <li>领域模型先于字典：格式错误（前缀含 {@code O}、美国缺州省）不需要查库就能判，
 *       让它先失败可以少一次字典往返，也让提示停在「输入格式」这一层；</li>
 *   <li>取号在全部校验之后：号一旦取走就作废（见 {@link OwnerCodeGenerator}），
 *       入参不合法时取号等于白白消耗号段，而号段是连续编号的可见长度。</li>
 * </ul>
 *
 * <p><b>唯一性一律交给数据库：</b>SKU 前缀、外部码、编码都不「先查再插」——两个并发请求会同时查到
 * 「不存在」，然后一个成功一个拿到数据库异常，与不查的痛苦相同却多了一次往返。
 * 冲突只在 {@code INSERT} 时暴露，捕获后按约束名翻译成 40904 / 40905 / 40906。
 * 客户名称可重复，本用例不为它加任何判重。
 *
 * <p><b>不开事务：</b>只有一条 {@code INSERT}。取号走的是独立提交（{@link OwnerCodeGenerator} 的契约），
 * 外层加一个事务既不会让取号跟着回滚，也不会让「号已用掉但货主没建成」变得可挽回；
 * 多一个空事务只会让人以为这里还有别的东西需要原子提交。
 *
 * <p>不写审计日志（{@code sys_audit_log}）：那属于审计能力（2.8），本用例只保证
 * {@code created_by} / {@code updated_by} 记下操作人。
 */
@Service
public class OwnerCreationService {

    private final OwnerRepository ownerRepository;
    private final OwnerCodeGenerator ownerCodeGenerator;
    private final OwnerDictionaryValidator ownerDictionaryValidator;
    private final CurrentOperator currentOperator;

    /**
     * @param ownerRepository         货主仓储端口
     * @param ownerCodeGenerator      取号端口，由 {@code wms-app} 接到编码规则引擎上
     * @param dictLookup              字典窄端口；领域服务是无状态的纯规则，随用例持有即可，
     *                                不必让它成为容器组件（那会让一个不依赖 Spring 的类被注解绑住）
     * @param currentOperator         当前操作人端口
     */
    public OwnerCreationService(OwnerRepository ownerRepository,
                                OwnerCodeGenerator ownerCodeGenerator,
                                DictLookup dictLookup,
                                CurrentOperator currentOperator) {
        this.ownerRepository = ownerRepository;
        this.ownerCodeGenerator = ownerCodeGenerator;
        this.ownerDictionaryValidator = new OwnerDictionaryValidator(dictLookup);
        this.currentOperator = currentOperator;
    }

    /**
     * 建档一个货主。
     *
     * @param command 建档入参；{@code initialStatus} 只接受 {@code DRAFT} 与 {@code ACTIVE}
     * @return 新货主的标识与系统编码
     * @throws BizException 当前会话无效（40100）；初始状态缺失或不可用于建档、SKU 前缀格式不合规、
     *                      地址必填项缺失、客户类型/国家/州省/邮编不满足字典规则（40000）；
     *                      编码被占用（40904）、SKU 前缀被占用（40905）、外部码被占用（40906）
     */
    public CreatedOwner create(CreateOwnerCommand command) {
        long operatorId = currentOperator.currentUserId()
                // 鉴权过滤器已拦过未登录请求，走到这里取不到会话只能是账号在请求途中被删除或令牌已失效；
                // 此时不能继续：created_by / updated_by 会写成空值，事后无从定位这条货主是谁建的
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        OwnerStatus initialStatus = requireCreatableStatus(command.initialStatus());

        NewOwner owner;
        try {
            owner = toNewOwner(command);
            // 字典规则与建档、编辑共用同一份实现，见 OwnerDictionaryValidator
            ownerDictionaryValidator.validate(owner);
        } catch (IllegalArgumentException e) {
            // 领域模型与领域服务都用 IllegalArgumentException 表达「输入不合法」：它们不认识 HTTP，
            // 也不该认识错误码。翻译成 40000 放在用例层，是这一层唯一能做协议转换的地方。
            throw BizException.badRequest(e.getMessage());
        }

        String code = ownerCodeGenerator.next();
        try {
            long id = ownerRepository.insert(owner, code, initialStatus, operatorId);
            return new CreatedOwner(id, code);
        } catch (DataIntegrityViolationException e) {
            throw OwnerConstraintTranslator.translateInsert(e, owner.skuPrefix().value());
        }
    }

    /**
     * 判定本次建档允许落到哪个初始状态。
     *
     * <p>只放行 {@code DRAFT} 与 {@code ACTIVE}：这是两条建档入口（保存草稿 / 直接提交）。
     * {@code POTENTIAL} 是「已提交但尚未合作」，它由提交动作产生；{@code SUSPENDED} 与 {@code TERMINATED}
     * 更只能是流转的结果——建档时直接落这三个状态，等于绕开了状态机（3.2）的入口条件，
     * 库里会出现一个从未「提交」过却已在合作中的货主。校验放在这里而不是请求注解上，
     * 是为了让非 HTTP 入口（批量导入、初始化脚本）同样受约束。
     *
     * @param initialStatus 请求里的初始状态，可为 {@code null}
     * @return 可落库的初始状态
     * @throws BizException 初始状态缺失或不在允许集合内
     */
    private static OwnerStatus requireCreatableStatus(OwnerStatus initialStatus) {
        if (initialStatus == null) {
            throw BizException.badRequest("建档初始状态不能为空，只能是 DRAFT（保存草稿）或 ACTIVE（合作中）");
        }
        if (initialStatus != OwnerStatus.DRAFT && initialStatus != OwnerStatus.ACTIVE) {
            throw BizException.badRequest("建档初始状态只能是 DRAFT（保存草稿）或 ACTIVE（合作中），实际提交："
                    + initialStatus.name());
        }
        return initialStatus;
    }

    /**
     * 把入参搬成待落库的货主。
     *
     * <p>先构造 {@link SkuPrefix} 再构造 {@link OwnerAddress}：格式错误优先于必填缺失暴露，
     * 与「错得越早、改起来越省事」同一取向——前缀错了要整条换一个，地址少一栏只是补一栏。
     *
     * <p>前缀**原样**传入，不做去空白、大小写转换或字符替换：规格要求拒绝含被排除字符的输入且
     * MUST NOT 自动替换，任何清洗都会让用户以为库里存的是他填的那个字符。
     *
     * @param command 建档入参
     * @return 待落库的货主
     * @throws IllegalArgumentException 前缀格式不合规、地址必填项缺失或名称/客户类型/结算主体为空
     */
    private static NewOwner toNewOwner(CreateOwnerCommand command) {
        SkuPrefix skuPrefix = new SkuPrefix(command.skuPrefix());
        OwnerAddress address = new OwnerAddress(
                OwnerTextNormalizer.toNullIfBlank(command.country()),
                OwnerTextNormalizer.toNullIfBlank(command.province()),
                OwnerTextNormalizer.toNullIfBlank(command.city()),
                OwnerTextNormalizer.toNullIfBlank(command.district()),
                OwnerTextNormalizer.toNullIfBlank(command.postalCode()),
                OwnerTextNormalizer.toNullIfBlank(command.addressLine()));
        return new NewOwner(
                OwnerTextNormalizer.toNullIfBlank(command.name()),
                OwnerTextNormalizer.toNullIfBlank(command.shortName()),
                OwnerTextNormalizer.toNullIfBlank(command.nameEn()),
                OwnerTextNormalizer.toNullIfBlank(command.externalCode()),
                skuPrefix,
                OwnerTextNormalizer.toNullIfBlank(command.ownerType()),
                OwnerTextNormalizer.toNullIfBlank(command.settlementEntity()),
                address,
                OwnerTextNormalizer.toNullIfBlank(command.remark()));
    }
}
