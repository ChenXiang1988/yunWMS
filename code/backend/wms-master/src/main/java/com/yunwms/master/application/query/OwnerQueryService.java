package com.yunwms.master.application.query;

import com.yunwms.master.application.dto.OwnerDetail;
import com.yunwms.master.application.dto.OwnerPage;
import com.yunwms.master.application.service.OwnerTextNormalizer;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 货主列表与详情的读取用例。
 *
 * <p>与 {@code OwnerCreationService} / {@code OwnerUpdateService} 分开：读侧不写审计字段、
 * 不判唯一性，混在一个类里会让「这个类的每个方法是不是都在改数据」变成一个要看方法体才能回答的问题。
 *
 * <p><b>本类只做四件事：</b>把分页参数判在合法范围内、把状态编码解成枚举、把关键字归一、
 * 把「货主不存在」转成 404。查询本身全部下沉到 {@link OwnerRepository}，本类不拼 SQL。
 *
 * <p><b>不加 {@code @Transactional(readOnly = true)}：</b>本类只有两次只读查询，没有需要原子提交的
 * 第二处写入，也没有「计数与分页必须看到同一快照」的要求——两个独立语句在默认隔离级别下本来就
 * 各看各的快照，加注解不会让它们更一致，只会多一层事务包装。
 *
 * <p><b>本类不自己筛数据范围：</b>列表的计数与分页、详情的单行读取都由持久化层按当前请求
 * 追加货主范围条件（MyBatis 数据权限拦截器）。在这里再按 userId 判一次会得到两个落点：
 * 一处改了另一处没改时，列表少了而 total 还是全部——两个数字对不上，且不报错。
 */
@Service
public class OwnerQueryService {

    /** 每页条数上限。与 {@code docs/api-convention.md} 第四节一致：超出必须拒绝，不得静默截断。 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 每页条数默认值，与接口约定里的示例一致。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int DEFAULT_PAGE_NO = 1;

    private final OwnerRepository ownerRepository;

    public OwnerQueryService(OwnerRepository ownerRepository) {
        this.ownerRepository = ownerRepository;
    }

    /**
     * 取一页货主。
     *
     * <p><b>分页参数的范围判定写在这里，而不是接口层的 {@code @Min} / {@code @Max}：</b>
     * 方法参数校验失败抛的是 {@code ConstraintViolationException}，而 {@code GlobalExceptionHandler}
     * 里没有它的分支，会被兜底成 50000。本批不改 {@code wms-web}（不在路径白名单内），
     * 因此在这里抛 {@code BizException}，让错误码稳定落在 40000（与 {@code RoleQueryService} 同一处理）。
     *
     * <p><b>状态编码在本层解析，而不是让 Spring 按 {@code @RequestParam} 的类型转换去转：</b>
     * 转换失败抛的是 {@code MethodArgumentTypeMismatchException}，落到一句泛化的「请求格式不正确」，
     * 用户看不到是哪个值不合法；更关键的是本层因此无法对「非法状态」作出判断，
     * 而这条判定属于用例语义，不该只存在于 HTTP 入口。编码的取值集合仍只有一处定义：
     * {@link OwnerStatus#fromCode}，读库与读请求参数走的是同一个方法。
     *
     * <p><b>客户类型不做字典校验：</b>它按 {@code OWNER_TYPE} 字典的 value 传入，但存量数据可能引用
     * 已被停用的字典项——那种货主在列表里是真实存在的，用字典校验筛选条件会让「按类型查历史数据」
     * 直接报错。筛选条件的口径因此是「库里有没有这个值」，而不是「字典里还有没有这个值」。
     *
     * @param pageNo   页码；为 {@code null} 时取 1
     * @param pageSize 每页条数；为 {@code null} 时取 20
     * @param keyword  按编码或名称模糊匹配；为 {@code null}、空串或全空白表示不筛选
     * @param ownerType 客户类型，取字典 value；为 {@code null}、空串或全空白表示不筛选
     * @param status   状态编码，取枚举名；为 {@code null}、空串或全空白表示不筛选
     * @return 一页货主，含总数
     * @throws BizException 页码小于 1、每页条数不在 1 到 200 之间（40000）；
     *                      状态编码不是已知的枚举名（40000）
     */
    public OwnerPage page(Integer pageNo, Integer pageSize, String keyword, String ownerType, String status) {
        int resolvedPageNo = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        // 页码与条数先判：它们不合法时整条查询根本不该发出，也就不该有任何 SQL
        if (resolvedPageNo < 1) {
            throw BizException.badRequest("页码必须从 1 开始");
        }
        if (resolvedPageSize < 1 || resolvedPageSize > MAX_PAGE_SIZE) {
            // 拒绝而不是截断：静默截成 200 时前端会把「只看到 200 条」当成「一共 200 条」
            throw BizException.badRequest("每页条数必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        OwnerStatus resolvedStatus = resolveStatus(status);

        String normalizedKeyword = OwnerTextNormalizer.toNullIfBlank(keyword);
        String normalizedOwnerType = OwnerTextNormalizer.toNullIfBlank(ownerType);
        long total = ownerRepository.count(resolvedStatus, normalizedOwnerType, normalizedKeyword);
        long offset = (long) (resolvedPageNo - 1) * resolvedPageSize;
        List<OwnerPage.Summary> records = ownerRepository
                .findPage(resolvedStatus, normalizedOwnerType, normalizedKeyword, resolvedPageSize, offset)
                .stream()
                .map(OwnerQueryService::toSummary)
                .toList();
        return new OwnerPage(resolvedPageNo, resolvedPageSize, total, records);
    }

    /**
     * 取单个货主的详情。
     *
     * <p>只查一次：详情需要的字段全部在主档里，联系人、税号与账号是另外三个页签（3.5 / 3.6 / 3.3），
     * 本批不读它们的表——为一个尚未实现的页签提前联表，只会让这条查询在那些表写满数据后变慢。
     *
     * <p>读的是 {@code findVisibleById} 而不是 {@code findById}：详情必须按当前请求的数据范围判定可见性，
     * 范围外的货主与「不存在」返回同一个 404——两者的区别本身就是一条信息，能拿来枚举他方数据。
     *
     * @param ownerId 货主标识
     * @return 货主详情
     * @throws BizException 货主不存在、已逻辑删除或不在当前数据范围内（40400）
     */
    public OwnerDetail detail(long ownerId) {
        Owner owner = ownerRepository.findVisibleById(ownerId)
                // 已逻辑删除的货主、范围外的货主与「不存在」同口径：管理端看不到它，接口因此返回同一个结果
                .orElseThrow(() -> BizException.notFound("货主不存在"));
        return new OwnerDetail(
                toSummary(owner),
                owner.nameEn(),
                owner.shortName(),
                owner.externalCode(),
                // 前缀取值对象的原文：值对象已保证格式，接口层不再做任何转换
                owner.skuPrefix().value(),
                owner.settlementEntity(),
                owner.address(),
                owner.remark(),
                owner.createdAt());
    }

    /**
     * 把状态编码解成枚举。
     *
     * <p>空白按「不筛选」处理：{@code ?status=} 这种写法来自把下拉框的默认项留空的表单，
     * 把它当成非法值拒绝，用户会看到「什么都没选也报错」。
     *
     * @param status 状态编码，可为 {@code null}
     * @return 状态枚举；未传时返回 {@code null}
     * @throws BizException 编码不是已知的枚举名（40000）
     */
    private static OwnerStatus resolveStatus(String status) {
        String normalized = OwnerTextNormalizer.toNullIfBlank(status);
        if (normalized == null) {
            return null;
        }
        try {
            return OwnerStatus.fromCode(normalized);
        } catch (IllegalArgumentException e) {
            // 领域层用 IllegalArgumentException 表达「编码未知」，它不认识 HTTP 与错误码；
            // 翻译成 40000 放在用例层，是这一层唯一能做协议转换的地方（与建档同一处理）
            throw BizException.badRequest(e.getMessage());
        }
    }

    /**
     * 把聚合搬成列表行。
     *
     * <p>逐字段搬而不是把聚合直接放进 DTO：聚合里带着 {@code createdBy} / {@code updatedBy}
     * 与 SKU 前缀值对象，列表行不该有机会把它们带出去，接口层也就不必记得「别序列化那两个字段」。
     *
     * @param owner 货主聚合
     * @return 列表行
     */
    private static OwnerPage.Summary toSummary(Owner owner) {
        return new OwnerPage.Summary(
                owner.id(),
                owner.code(),
                owner.name(),
                owner.ownerType(),
                owner.status(),
                owner.updatedAt());
    }
}
