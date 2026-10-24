package com.yunwms.auth.application.dto;

import com.yunwms.auth.domain.model.SubjectType;

import java.util.List;

/**
 * 登录成功后的主体信息，供接口层组装响应。
 *
 * <p>与响应对象分开：这里不关心字段怎么序列化、ID 用什么形态传给前端，
 * 只表达「谁是登录者、他拿到哪个令牌、他有哪些角色」。
 *
 * @param token       本次签发的访问令牌
 * @param subjectType 主体类型
 * @param ownerId     所属货主；内部员工为空
 * @param displayName 展示名
 * @param roles       生效中的角色标识，没有角色时为空列表
 */
public record AuthenticatedSubject(
        String token,
        SubjectType subjectType,
        Long ownerId,
        String displayName,
        List<String> roles) {

    /** 角色列表随登录结果一起流转，复制一份避免调用方改到会话相关的结果。 */
    public AuthenticatedSubject {
        roles = List.copyOf(roles);
    }
}
