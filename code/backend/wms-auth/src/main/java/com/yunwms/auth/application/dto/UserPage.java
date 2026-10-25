package com.yunwms.auth.application.dto;

import com.yunwms.auth.domain.model.UserSummary;

import java.util.List;

/**
 * 用户列表的一页。
 *
 * <p>把 {@code pageNo} / {@code pageSize} 一并回带，是为了让前端能直接用响应里的分页信息渲染
 * 页码控件：只回 {@code records} 与 {@code total} 时，前端得自己记住请求时填的是第几页，
 * 而分页请求与响应之间是会隔着一次用户操作的。
 *
 * <p>本类型停留在应用层，不直接序列化：标识转字符串属于接口层约定，由响应体的装配器负责。
 *
 * @param pageNo   页码，从 1 开始
 * @param pageSize 每页条数
 * @param total    满足筛选条件的总数，不是本页条数
 * @param records  本页用户；没有数据时为空列表
 */
public record UserPage(int pageNo, int pageSize, long total, List<UserSummary> records) {
}
