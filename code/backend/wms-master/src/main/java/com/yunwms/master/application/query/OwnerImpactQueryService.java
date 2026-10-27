package com.yunwms.master.application.query;

import com.yunwms.master.application.dto.TerminationImpact;
import com.yunwms.master.domain.port.OwnerAccountCounter;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.master.domain.port.OwnerSkuCounter;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;

/**
 * 终止影响清单的读取用例：该货主下仍存在的商品数与账号数。
 *
 * <p><b>为什么它自己判「货主存在」而不让调用方先判：</b>两个入口都需要这个结论——确认弹窗要拿
 * 404 告诉前端「这条货主已经没了」，状态变更用例要保证不会给一个不存在的货主算影响。
 * 判定放在这里，两个入口就都只有一次调用；放到调用方去判，第二个入口迟早会漏。
 *
 * <p><b>两个计数分别来自两个模块，本类不合并它们的数据源：</b>商品数走
 * {@link OwnerSkuCounter}（{@code sku} 属本模块的主数据域），账号数走
 * {@link OwnerAccountCounter}（{@code sys_user} 属 {@code wms-auth}，实现在 {@code wms-app} 装配）。
 * 两次独立查询之间可能有人新增了一件商品，这只影响弹窗上的数字，不影响随后那次状态变更的正确性——
 * 状态变更不依赖这两个数字做任何判定，它只把数字写进「请先确认」的提示里。
 *
 * <p>不加 {@code @Transactional(readOnly = true)}：三次只读查询，没有需要原子提交的写入。
 */
@Service
public class OwnerImpactQueryService {

    private final OwnerRepository ownerRepository;
    private final OwnerSkuCounter ownerSkuCounter;
    private final OwnerAccountCounter ownerAccountCounter;

    /**
     * @param ownerRepository      货主仓储端口，只用于判定货主存在
     * @param ownerSkuCounter      商品计数端口
     * @param ownerAccountCounter  账号计数端口，实现在 {@code wms-app} 的 bridge 包
     */
    public OwnerImpactQueryService(OwnerRepository ownerRepository,
                                   OwnerSkuCounter ownerSkuCounter,
                                   OwnerAccountCounter ownerAccountCounter) {
        this.ownerRepository = ownerRepository;
        this.ownerSkuCounter = ownerSkuCounter;
        this.ownerAccountCounter = ownerAccountCounter;
    }

    /**
     * 取该货主的终止影响清单。
     *
     * <p>存在性判定走 {@code findVisibleById}：影响清单只返回两个数字，但数字本身能被拿来探测
     * 「这个标识对应一条真实货主」——范围外的货主必须与「不存在」返回同一个 404。
     *
     * @param ownerId 货主标识
     * @return 商品数与账号数；两者都没有时是两个 0，而不是空对象
     * @throws BizException 货主不存在、已逻辑删除或不在当前数据范围内（40400）
     */
    public TerminationImpact terminationImpact(long ownerId) {
        // 已逻辑删除、范围外的货主与「不存在」同口径：管理端看不到它，它也就没有影响清单可看
        ownerRepository.findVisibleById(ownerId)
                .orElseThrow(() -> BizException.notFound("货主不存在"));
        return new TerminationImpact(
                ownerSkuCounter.countByOwnerId(ownerId),
                ownerAccountCounter.countByOwnerId(ownerId));
    }
}
