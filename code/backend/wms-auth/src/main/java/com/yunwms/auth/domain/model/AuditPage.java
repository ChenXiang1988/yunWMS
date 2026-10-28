package com.yunwms.auth.domain.model;

import java.util.List;

/**
 * 按对象追溯得到的一页审计日志，按操作时间倒序。
 *
 * <p>分页信息随记录一起回带，理由与 {@code RolePage} 相同：只回 {@code records} 与 {@code total} 时，
 * 前端得自己记住请求时填的是第几页，而分页请求与响应之间隔着一次用户操作。
 *
 * <p>本类型放在领域层而不是应用层，是因为 {@code AuditQuery} 端口直接返回它：端口签名里若出现
 * 应用层的类型，领域层就要反向依赖应用层。
 *
 * @param pageNo   页码，从 1 开始
 * @param pageSize 每页条数
 * @param total    该对象的日志总数，不是本页条数
 * @param records  本页日志，按操作时间倒序（同一时刻按标识倒序）；没有数据时为空列表
 */
public record AuditPage(int pageNo, int pageSize, long total, List<AuditLog> records) {
}
