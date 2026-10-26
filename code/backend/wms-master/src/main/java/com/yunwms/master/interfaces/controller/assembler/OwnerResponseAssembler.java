package com.yunwms.master.interfaces.controller.assembler;

import com.yunwms.master.application.dto.CreatedOwner;
import com.yunwms.master.interfaces.controller.response.CreatedOwnerResponse;
import org.springframework.stereotype.Component;

/**
 * 货主用例结果到响应体的转换。
 *
 * <p>「标识以字符串传输」属于接口层约定，不属于用例：用例只认数字标识，是否序列化成字符串
 * 由契约决定。转换集中在这里，3.1b-2 的列表与详情也接同一处，不必各写一遍。
 */
@Component
public class OwnerResponseAssembler {

    /**
     * 组装建档响应。
     *
     * @param createdOwner 建档结果
     * @return 可直接序列化的响应体
     */
    public CreatedOwnerResponse toResponse(CreatedOwner createdOwner) {
        return new CreatedOwnerResponse(Long.toString(createdOwner.id()), createdOwner.code());
    }
}
