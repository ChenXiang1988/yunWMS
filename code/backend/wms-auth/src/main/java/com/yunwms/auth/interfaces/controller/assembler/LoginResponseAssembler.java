package com.yunwms.auth.interfaces.controller.assembler;

import com.yunwms.auth.application.dto.AuthenticatedSubject;
import com.yunwms.auth.domain.model.AccessiblePortal;
import com.yunwms.auth.interfaces.controller.response.LoginResponse;
import org.springframework.stereotype.Component;

/**
 * 登录结果到响应体的转换。
 *
 * <p>转换集中在这里，是因为契约里的两处约定属于接口层，不属于领域或用例：
 * 标识以字符串传输（超过 2^53 的 Long 在 JS 里会丢精度），枚举以编码而非枚举名对外。
 */
@Component
public class LoginResponseAssembler {

    /**
     * 组装登录响应。
     *
     * @param subject 登录成功的主体信息
     * @return 可直接序列化的响应体
     */
    public LoginResponse toResponse(AuthenticatedSubject subject) {
        return new LoginResponse(
                subject.token(),
                subject.subjectType().name(),
                subject.ownerId() == null ? null : subject.ownerId().toString(),
                subject.displayName(),
                subject.roles(),
                subject.subjectType().accessiblePortals().stream()
                        .map(AccessiblePortal::code)
                        .toList());
    }
}
