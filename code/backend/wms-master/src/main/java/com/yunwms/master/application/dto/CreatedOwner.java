package com.yunwms.master.application.dto;

/**
 * 建档结果：新货主的标识与系统编码。
 *
 * <p>编码随结果返回而不是让前端再查一次：它是这次提交产生的主键之外唯一能让人核对「建的是哪一个」的值。
 * 标识与编码在用例里都是既成事实，是否以字符串序列化由接口层决定，本对象不掺协议约定。
 *
 * @param id   数据库生成的货主标识
 * @param code 由编码规则生成的系统编码，形如 {@code OWNER0001}
 */
public record CreatedOwner(long id, String code) {
}
