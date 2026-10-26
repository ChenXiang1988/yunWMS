package com.yunwms.master.application.service;

import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;

/**
 * 删除货主的用例：只允许删草稿，执行软删除。
 *
 * <p><b>为什么只有草稿能删：</b>删除是「这条记录不该存在」的表述，而草稿是唯一还没有产生任何
 * 外部后果的状态——没有主账号（3.3 未落地，且规格要求草稿不生成）、建不了商品档案（规格
 * 「暂停货主禁止新增业务」把草稿一并算了进去）。已提交的货主一旦删掉，历史单据、商品档案与
 * 账号都会指向一条界面上不存在的记录，因此它们必须先走状态流转（终止）再谈清理。
 * 提示里要带上当前状态：「不可删除」不带原因时，用户只会反复点同一个按钮。
 *
 * <p><b>软删除而不是物理删除：</b>置 {@code deleted_at}。列表与详情因此看不到它（仓储的读取都带
 * 逻辑删除条件），而 {@code ux_owner_code} 与 {@code ux_owner_sku_prefix} 都是<b>全表</b>唯一索引，
 * 编码与前缀仍被占用——新建档时撞上会得到 40904 / 40905，这正是规格「编码与 SKU 前缀永不复用」
 * 的落点。物理删除会释放它们，让同一个编码出现第二条含义不同的记录。
 *
 * <p><b>不加「草稿下不得有商品与账号」的前置检查：</b>草稿按规格本来就建不了这两样，真出现了说明
 * 数据已被绕过规则写坏。把它变成一条删除前置条件，等于用「删不掉」去报告一个与删除无关的数据问题，
 * 而管理员此时唯一合理的动作仍然是删掉这条从未生效的草稿。软删除也不会触发外键级联，
 * 那些异常数据继续留在库里，由商品与账号各自的归属校验（{@code owner.deleted_at}）去处理。
 *
 * <p><b>已删除再删返回 404：</b>读取侧已把「已逻辑删除」与「不存在」视为同一件事（{@code findById}
 * 与详情接口同口径），第二次删除因此与查详情得到同一个 40400。这仍是幂等的——重试不会产生第二次
 * 写入、不会报 500，也不会把一条已删记录重新写活；只是「它现在已经不存在了」这件事必须如实回答。
 *
 * <p>不开事务：只有一条 {@code UPDATE}。
 */
@Service
public class OwnerDeletionService {

    private final OwnerRepository ownerRepository;
    private final CurrentOperator currentOperator;

    /**
     * @param ownerRepository 货主仓储端口
     * @param currentOperator 当前操作人端口
     */
    public OwnerDeletionService(OwnerRepository ownerRepository, CurrentOperator currentOperator) {
        this.ownerRepository = ownerRepository;
        this.currentOperator = currentOperator;
    }

    /**
     * 删除一条草稿货主。
     *
     * @param ownerId 货主标识
     * @throws BizException 当前会话无效（40100）；货主不存在或已被删除（40400）；
     *                      当前状态不是草稿（40900，提示里带当前状态）
     */
    public void delete(long ownerId) {
        long operatorId = currentOperator.currentUserId()
                // 与建档、编辑同一理由：deleted_at 之外还要留下「谁删的」，取不到操作人就不能继续
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        Owner existing = ownerRepository.findById(ownerId)
                .orElseThrow(() -> BizException.notFound("货主不存在"));

        if (existing.status() != OwnerStatus.DRAFT) {
            // 入参本身合法，被拒绝的是当前状态，因此落在 409 段位而不是 40000
            throw BizException.conflict("货主当前状态为 " + existing.status().name()
                    + "，只有草稿（DRAFT）可以删除；如需停止合作请使用状态流转（终止）");
        }

        if (!ownerRepository.softDelete(existing.id(), operatorId)) {
            // 读到之后、写入之前这一行被另一个请求删掉了：此刻它确实不存在，如实回答 404，
            // 而不是回成功——回成功会掩盖「两次删除之间隔了一段时间」这件事
            throw BizException.notFound("货主不存在");
        }
    }
}
