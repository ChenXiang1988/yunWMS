package com.yunwms.master.application.service;

import com.yunwms.master.application.command.ProvisionOwnerAccountCommand;
import com.yunwms.master.application.dto.ProvisionedOwnerAccount;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.master.domain.port.OwnerAccountLookup;
import com.yunwms.master.domain.port.OwnerAccountProvisioner;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.OptionalLong;

/**
 * 货主主账号开通的用例：3PL 员工为处于「合作中」的货主建一个可登录货主端的超级管理员账号。
 *
 * <p><b>校验顺序固定为「取操作人 → 货主存在 → 状态是合作中 → 该货主尚无主账号 → 姓名非空 → 建号」，
 * 顺序本身是语义，不可调换：</b>
 * <ul>
 *   <li>操作人先取：取不到就没有可追溯的责任人，而这次调用会写出一条能登录系统的账号；</li>
 *   <li>状态先于「是否已有账号」：一个已终止的货主，管理员最该知道的是「它已经终止了」，
 *       而不是「它已经有账号了」。顺序反过来时，终止货主的重复提交会得到一句「已开通主账号」，
 *       把人引去查账号，而真正该做的是看货主为什么终止；</li>
 *   <li>判重先于姓名：判重是这个接口最容易触发的一条拒绝（重复点两下、页面没刷新），
 *       先把它说清，用户就不用先改一个注定用不上的姓名；</li>
 *   <li>姓名非空放在建号前：建号一旦成功就无法回退（对端在自己的事务里提交），
 *       能靠一次判空避免的空账号不能留到建完再发现。</li>
 * </ul>
 *
 * <p><b>判重的两种情形必须分开提示（都是 409，但文案不同）：</b>
 * <ul>
 *   <li>登录名归属于当前货主 → 「该货主已开通主账号，登录名为 X」：处置是「不必再开」；</li>
 *   <li>登录名归属于别的货主 → 「登录名 X 已被其他账号占用」：处置是「先查清这个登录名被谁占了」，
 *       正常路径不可能出现，出现即说明数据被绕过规则写坏。</li>
 * </ul>
 * 合并成一句「登录名已被占用」时，管理员分不清「我重复点了」与「数据坏了」，两种处置也从一句话里消失了。
 *
 * <p><b>登录名取自货主系统编码，不由调用方给：</b>规格要求登录名由系统按货主编码生成、不得使用手机号。
 * 请求体里因此没有登录名字段，多传会被 {@code FAIL_ON_UNKNOWN_PROPERTIES} 拒绝——这不是遗漏，
 * 是「谁来决定登录名」的答案。
 *
 * <p><b>不开 {@code @Transactional}：</b>建号在 {@code wms-auth} 自己的事务里提交，本模块只有一次
 * 只读查询与一次端口调用。在 master 侧再开一个事务，既管不到对端事务（跨模块不同数据源配置下
 * 更不可能），也不会让「账号已建但货主状态被并发改了」变得可回滚；多一个空事务只会让人误以为
 * 整条链路原子。真正的并发后果是可接受的：两个请求同时通过判重，后到的那次撞
 * {@code ux_sys_user_login_name}，得到 40901——同一条账号不会建出两条。
 *
 * <p><b>不写审计日志（{@code sys_audit_log}）：</b>那属于审计能力（2.8）。账号侧由
 * {@code wms-auth} 记 {@code created_by}，本用例不重复记一遍。
 */
@Service
public class OwnerAccountProvisionService {

    private final OwnerRepository ownerRepository;
    private final OwnerAccountLookup ownerAccountLookup;
    private final OwnerAccountProvisioner ownerAccountProvisioner;
    private final CurrentOperator currentOperator;

    /**
     * @param ownerRepository          货主仓储端口，只用于判定货主存在与读取编码、状态
     * @param ownerAccountLookup       登录名反查端口，实现在 {@code wms-app} 的 bridge 包
     * @param ownerAccountProvisioner  建号端口，实现在 {@code wms-app} 的 bridge 包
     * @param currentOperator          当前操作人端口
     */
    public OwnerAccountProvisionService(OwnerRepository ownerRepository,
                                        OwnerAccountLookup ownerAccountLookup,
                                        OwnerAccountProvisioner ownerAccountProvisioner,
                                        CurrentOperator currentOperator) {
        this.ownerRepository = ownerRepository;
        this.ownerAccountLookup = ownerAccountLookup;
        this.ownerAccountProvisioner = ownerAccountProvisioner;
        this.currentOperator = currentOperator;
    }

    /**
     * 为货主开通主账号。
     *
     * <p>本方法不接收操作人标识：账号的 {@code created_by} 由 {@code wms-auth} 从同一次请求的会话里取。
     * 这里先取一次只为一个目的——会话失效时在本模块的写入口当场拒绝，而不是先读货主、再去对端建号，
     * 最后才在链路的另一端得到同一条 40100。
     *
     * @param command 开通入参
     * @return 新账号的标识与登录名（即货主编码）
     * @throws BizException 当前会话无效（40100）；货主不存在或已逻辑删除（40400）；
     *                      货主状态不是合作中、该货主已有主账号、登录名被其他账号占用（40900，
     *                      提示里分别带当前状态、已有登录名、被占用的登录名）；
     *                      主账号姓名为空（40000）；初始密码不合规（40000）；
     *                      登录名已被内部员工占用（40901，由建号用例透出）
     */
    public ProvisionedOwnerAccount provision(ProvisionOwnerAccountCommand command) {
        // ① 操作人：不保留它的值，本次调用的 created_by 由 wms-auth 自己取（见方法注释）
        currentOperator.currentUserId()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        // ② 货主存在。与编辑、状态流转、删除同一口径：写入口按「这一行存在吗」判 404，
        // 数据范围只约束读路径（列表、详情、影响清单）
        Owner owner = ownerRepository.findById(command.ownerId())
                .orElseThrow(() -> BizException.notFound("货主不存在"));

        // ③ 只有合作中可开号：规格「非合作中的货主不可开号」且必须提示当前状态
        if (owner.status() != OwnerStatus.ACTIVE) {
            // 入参本身合法，被拒绝的是货主的当前状态，因此落在 409 段位而不是 40000
            throw BizException.conflict("货主当前状态为 " + owner.status().name()
                    + "，只有合作中（ACTIVE）的货主可以开通主账号");
        }

        // ④ 该货主尚无主账号。登录名取货主系统编码（形如 OWNER0001，规格「登录名由系统生成」），
        // 因此「这个登录名归谁」同时回答了「有没有开过」与「是不是被占」
        String loginName = owner.code();
        requireLoginNameUsable(loginName, owner.id());

        // ⑤ 姓名非空。入口的 @NotBlank 只覆盖 HTTP；批量导入、初始化脚本同样受这条约束
        String displayName = OwnerTextNormalizer.toNullIfBlank(command.displayName());
        if (displayName == null) {
            throw BizException.badRequest("主账号姓名不能为空");
        }

        // ⑥ 建号。密码策略、主体与货主匹配、登录名唯一冲突的翻译都在对端用例里，这里只做委托
        long accountId = ownerAccountProvisioner.createMainAccount(
                owner.id(), loginName, displayName, command.initialPassword());
        return new ProvisionedOwnerAccount(accountId, loginName);
    }

    /**
     * 判定登录名能不能用来给当前货主开号。
     *
     * <p>两种拒绝共用 409 但文案不同：处置动作不同（「不必再开」与「查清被谁占了」），
     * 合并成一句时管理员分不清自己重复提交了还是数据坏了。
     *
     * <p>反查为空不等于「一定能建」：命中唯一索引仍会失败，那条路径由对端翻译成 40901。
     * 本方法只负责把「已知的冲突」说清楚。
     *
     * @param loginName 登录名，取自货主系统编码
     * @param ownerId   当前货主标识
     * @throws BizException 该货主已有主账号（40900，提示带登录名）；登录名归属其他货主（40900，提示带登录名）
     */
    private void requireLoginNameUsable(String loginName, long ownerId) {
        OptionalLong occupiedBy = ownerAccountLookup.findOwnerIdByLoginName(loginName);
        if (occupiedBy.isEmpty()) {
            return;
        }
        if (occupiedBy.getAsLong() == ownerId) {
            throw BizException.conflict("该货主已开通主账号，登录名为 " + loginName);
        }
        throw BizException.conflict("登录名 " + loginName + " 已被其他账号占用");
    }
}
