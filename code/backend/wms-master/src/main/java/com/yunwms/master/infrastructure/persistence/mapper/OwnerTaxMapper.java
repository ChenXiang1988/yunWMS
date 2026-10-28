package com.yunwms.master.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunwms.master.infrastructure.persistence.entity.OwnerTaxEntity;
import org.apache.ibatis.annotations.Param;

/**
 * {@code owner_tax_registration} 的映射接口。
 *
 * <p><b>读侧不在这里：</b>按货主取列表与按标识读都是单表等值查询，{@code docs/coding-java.md}
 * 「MyBatis-Plus」一节要求这类查询优先走 {@link BaseMapper} 与 Lambda Wrapper，写成 XML 只会多一个
 * 要同步维护的文件；逻辑删除条件由 {@code application.yml} 的全局配置自动补上，手写 SQL 反而
 * 拿不到这层补偿。
 *
 * <p><b>写侧三条为什么走 XML：</b>共同原因是「{@link BaseMapper} 写不出想要的语句」，各自的具体
 * 原因写在 {@code mapper/master/OwnerTaxMapper.xml} 对应语句上方。一句话概括：
 * {@code updateById} 按「非空字段才进 SET」生成语句，编辑税号时把 {@code province} 清空
 * （国家级税号没有州省）会变成一次静默的空操作；软删除与清默认标记还要同时写
 * {@code updated_at = now()} 与 {@code updated_by}，不是单条 {@code deleteById} 或
 * {@code updateById} 能表达的。
 *
 * <p>语句里凡是 WHERE 都显式带 {@code deleted_at IS NULL}：MyBatis-Plus 的逻辑删除只作用于
 * 它自己生成的语句，手写 SQL 漏了这一条，已软删除的税号会被读到、会被改活，而调用方看到的是成功。
 */
public interface OwnerTaxMapper extends BaseMapper<OwnerTaxEntity> {

    /**
     * 覆盖税号的可编辑列与审计列，{@code null} 值即写 {@code NULL}。
     *
     * <p>SET 列表写死 {@code province} / {@code tax_number} / {@code is_default}：可编辑范围就这三列，
     * 全量替换的语义只有写死才能保证——{@code province} 必须能由有值改为 {@code NULL}
     * （换登记层级时），{@code is_default} 必须能在两个方向上改。
     *
     * <p>SET 里没有 {@code owner_id}、{@code country}、{@code tax_type}：这三列是这一行的身份
     * （哪个货主、哪一国的哪种税号）。改它们等于换一条记录，业务上应删旧登新；写死在语句里，
     * 比依赖「调用方记得别传」可靠——即使上层把它们放进了模型，这条语句也改不到。
     * 也没有 {@code created_at} / {@code created_by} / {@code deleted_at}：创建痕迹只由 insert 写入，
     * 删除标记只能由下面那条 {@code softDelete} 写，让编辑有机会改 {@code deleted_at}
     * 等于留了一条「把已删除的税号悄悄改活」的路径。
     *
     * <p>返回的影响行数本方法的调用方不据以判定成功：端口已约定「税号不存在」由 {@code findById}
     * 回答，命中零行只可能是两次调用之间被软删除，不是需要纠正的失败，也不是并发冲突
     * （见 {@code docs/coding.md}「持久化与副作用」）。
     *
     * @param entity 覆盖后的字段值，必须带标识与操作人；{@code ownerId}、{@code country}、
     *               {@code taxType}、{@code createdBy} 不会被读取
     * @return 影响行数
     */
    int updateTax(OwnerTaxEntity entity);

    /**
     * 软删除：只置 {@code deleted_at} 与审计列。
     *
     * <p>{@code WHERE} 上的 {@code deleted_at IS NULL} 同时是「不重复删除」的判据：第二次删除
     * 命中零行，调用方据此回 404，而不是回一个看不出来发生过的成功。
     *
     * <p>软删除之后该货主在该国家下的「默认税号」名额会被释放：{@code ux_owner_tax_default}
     * 是带 {@code deleted_at IS NULL} 的部分唯一索引，因此删掉默认税号可以直接再设一个新的，
     * 本语句不需要顺带清谁。
     *
     * @param id        税号标识
     * @param updatedBy 操作人标识
     * @return 影响行数；0 表示这一行已被删除或不存在
     */
    int softDelete(@Param("id") long id, @Param("updatedBy") long updatedBy);

    /**
     * 把该货主在该国家下当前的默认税号置为非默认，写入 {@code updated_by} 与 {@code updated_at}。
     *
     * <p>{@code AND is_default} 让语句只在确实存在默认税号时命中：没有默认税号时命中零行是本方法
     * 的正常输入（端口已声明返回 {@code void}），不报错、也不由调用方判断。
     *
     * <p>按 {@code owner_id + country} 定位而不是按税号标识：默认是「货主在某国的当前状态」，
     * 调用方在「先清旧默认、再写新默认」里不该需要先查出旧默认的标识；命中行数由
     * {@code ux_owner_tax_default} 保证至多一行。{@code country} 是条件而不是可省的筛选项：
     * 换美国的默认税号不该顺手清掉中国的默认标记。
     *
     * @param ownerId   货主标识
     * @param country   国家，两位大写字母
     * @param updatedBy 操作人标识
     * @return 影响行数；0 表示该货主在该国家下没有默认税号，属正常结果
     */
    int clearDefault(
            @Param("ownerId") long ownerId,
            @Param("country") String country,
            @Param("updatedBy") long updatedBy);
}
