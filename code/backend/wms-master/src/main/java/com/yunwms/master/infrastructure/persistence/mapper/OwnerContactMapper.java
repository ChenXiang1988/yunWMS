package com.yunwms.master.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunwms.master.infrastructure.persistence.entity.OwnerContactEntity;
import org.apache.ibatis.annotations.Param;

/**
 * {@code owner_contact} 的映射接口。
 *
 * <p><b>读侧不在这里：</b>列表、按标识读、计数、按角色判存在都是单表等值查询，
 * {@code docs/coding-java.md}「MyBatis-Plus」一节要求这类查询优先走 {@link BaseMapper} 与
 * Lambda Wrapper，写成 XML 只会多一个要同步维护的文件；逻辑删除条件由
 * {@code application.yml} 的全局配置自动补上，手写 SQL 反而拿不到这层补偿。
 *
 * <p><b>写侧三条为什么走 XML：</b>共同原因是「{@link BaseMapper} 写不出想要的语句」，各自的具体
 * 原因写在 {@code mapper/master/OwnerContactMapper.xml} 对应语句上方。一句话概括：
 * {@code updateById} 按「非空字段才进 SET」生成语句，编辑联系人时把 {@code email} 或
 * {@code remark} 清空会变成一次静默的空操作；软删除与清主标记还要同时写
 * {@code updated_at = now()} 与 {@code updated_by}，不是单条 {@code deleteById} 或
 * {@code updateById} 能表达的。
 *
 * <p>语句里凡是 WHERE 都显式带 {@code deleted_at IS NULL}：MyBatis-Plus 的逻辑删除只作用于
 * 它自己生成的语句，手写 SQL 漏了这一条，已软删除的联系人会读到、会被改活，而调用方看到的是成功。
 */
public interface OwnerContactMapper extends BaseMapper<OwnerContactEntity> {

    /**
     * 覆盖联系人的全部可编辑列与审计列，{@code null} 值即写 {@code NULL}。
     *
     * <p>SET 列表写死 {@code name} / {@code role_type} / {@code phone} / {@code email} /
     * {@code is_primary} / {@code remark}：联系人的可编辑范围就这六列，全量替换的语义只有写死
     * 才能保证。SET 里没有 {@code owner_id}、{@code created_at} / {@code created_by}、
     * {@code deleted_at}：归属创建后不可改，创建痕迹与删除标记各有专门语句，编辑不该有机会改到它们。
     *
     * <p>返回的影响行数本方法的调用方不据以判定成功：端口已经约定「联系人不存在」由
     * {@code findById} 回答，命中零行只可能是两次调用之间被软删除，不是需要纠正的失败，也不是
     * 并发冲突（见 {@code docs/coding.md}「持久化与副作用」）。
     *
     * @param entity 覆盖后的字段值，必须带标识与操作人；{@code ownerId}、{@code createdBy} 不会被读取
     * @return 影响行数
     */
    int updateContact(OwnerContactEntity entity);

    /**
     * 软删除：只置 {@code deleted_at} 与审计列。
     *
     * <p>{@code WHERE} 上的 {@code deleted_at IS NULL} 同时是「不重复删除」的判据：第二次删除
     * 命中零行，调用方据此回 404，而不是回一个看不出来发生过的成功。
     *
     * <p>软删除之后该货主的「主联系人」名额会被释放：{@code ux_owner_contact_primary} 是带
     * {@code deleted_at IS NULL} 的部分唯一索引，因此删掉主联系人可以直接再设一个新的，
     * 本语句不需要顺带清谁。
     *
     * @param id        联系人标识
     * @param updatedBy 操作人标识
     * @return 影响行数；0 表示这一行已被删除或不存在
     */
    int softDelete(@Param("id") long id, @Param("updatedBy") long updatedBy);

    /**
     * 把该货主当前的主联系人置为非主，写入 {@code updated_by} 与 {@code updated_at}。
     *
     * <p>{@code AND is_primary} 让语句只在确实存在主联系人时命中：没有主联系人时命中零行是
     * 本方法的正常输入（端口已声明返回 {@code void}），不报错、也不由调用方判断。
     *
     * <p>按 {@code owner_id} 定位而不是按联系人标识：主联系人是「货主的当前状态」，
     * 调用方在「先清旧主、再写新主」里不该需要先查出旧主的标识；命中行数由
     * {@code ux_owner_contact_primary} 保证至多一行。
     *
     * @param ownerId   货主标识
     * @param updatedBy 操作人标识
     * @return 影响行数；0 表示该货主当前没有主联系人，属正常结果
     */
    int clearPrimary(@Param("ownerId") long ownerId, @Param("updatedBy") long updatedBy);
}
