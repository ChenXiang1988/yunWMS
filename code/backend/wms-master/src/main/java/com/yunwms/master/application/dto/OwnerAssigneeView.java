package com.yunwms.master.application.dto;

import java.util.OptionalLong;

/**
 * 货主当前负责客服的显示模型：主、副负责人各至多一名，各以一个员工标识表示。
 *
 * <p><b>两个字段用可空的 {@link Long}，而不是仓储端口返回的 {@link OptionalLong}：</b>
 * 端口用 {@code OptionalLong} 是为了让「这个角色还没有人」在类型上必须被处理；到了这一层，
 * 这件事已经处理完了，「有没有人」在页面上就是「有值 / 空」，用 {@code null} 表达即可。
 * 保留 {@code OptionalLong} 会让装配器、响应映射与测试的每个消费点都多一次拆箱，
 * 而 record 的组件类型会原样成为契约的一部分——它一旦流到序列化边界，
 * 输出形状就取决于 {@code ObjectMapper} 是否注册了 JDK8 类型模块，那是一处与业务无关的配置依赖。
 *
 * <p><b>不带员工姓名：</b>姓名在 {@code sys_user}，归 {@code wms-auth}；本模块只经
 * {@code StaffLookup} 拿到「是不是在册员工」这一个是/否，拿不到档案字段。要展示姓名的调用方
 * 自己声明它需要的查询能力，本类型不替它把姓名抄一份存下来——抄下来的那份迟早与用户表不一致。
 *
 * <p>也不含历史与来源：这里回答的是「现在是谁」，变更过程由审计日志承担。
 *
 * @param ownerId         货主标识
 * @param primaryUserId   主负责人的员工标识；该货主尚未配置主负责人时为 {@code null}
 * @param secondaryUserId 副负责人的员工标识；该货主没有副负责人时为 {@code null}
 */
public record OwnerAssigneeView(
        long ownerId,
        Long primaryUserId,
        Long secondaryUserId) {
}
