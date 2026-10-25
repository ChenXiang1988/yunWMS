package com.yunwms.auth.application.command;

import com.yunwms.auth.domain.model.SubjectType;

import java.util.List;

/**
 * 管理员建号的入参。
 *
 * <p>携带创建所需的原始值，不做任何清洗：去空白、空串按未填处理这类口径属于业务规则，
 * 与建号校验同处用例层；放在协议层会让「接口能建出什么账号」取决于调用方是不是 HTTP。
 *
 * @param loginName       登录名，员工由管理员指定
 * @param displayName     展示名
 * @param subjectType     主体类型，决定 ownerId 与 email 的必填性
 * @param ownerId         所属货主；内部员工必须为空，货主账号必填
 * @param email           邮箱；内部员工必填
 * @param wecomUserId     企业微信 userid，可为空
 * @param initialPassword 初始密码明文，仅在本进程内存中短暂存在
 * @param roleIds         要分配的角色标识，可为空表示不分配
 */
public record CreateUserCommand(
        String loginName,
        String displayName,
        SubjectType subjectType,
        Long ownerId,
        String email,
        String wecomUserId,
        String initialPassword,
        List<Long> roleIds) {
}
