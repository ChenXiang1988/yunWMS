package com.yunwms.auth.interfaces.controller.assembler;

import com.yunwms.auth.application.dto.CreatedUser;
import com.yunwms.auth.interfaces.controller.response.CreatedUserResponse;
import org.springframework.stereotype.Component;

/**
 * 建号结果到响应体的转换。
 *
 * <p>转换集中在这里，是因为「标识以字符串传输」属于接口层约定，不属于用例：
 * 用例只知道用户标识是一个数字，是否序列化成字符串由契约决定。
 */
@Component
public class UserResponseAssembler {

    /**
     * 组装建号响应。
     *
     * @param createdUser 建号结果
     * @return 可直接序列化的响应体
     */
    public CreatedUserResponse toResponse(CreatedUser createdUser) {
        return new CreatedUserResponse(Long.toString(createdUser.id()), createdUser.loginName());
    }
}
