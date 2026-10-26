package com.yunwms.master.application.dto;

/**
 * 终止影响清单：该货主名下仍存在的商品数与账号数。
 *
 * <p>只放两个计数，不放商品明细或账号列表：规格要求的是「列出数量并要求二次确认」，
 * 明细属于商品页与账号页各自的读取能力（{@code implement-sku} / 3.3），在这里顺手多取一批
 * 只会让确认弹窗为了两行摘要去读两张表的全部行。
 *
 * <p><b>终止只改货主状态，不碰这两个数字背后的实体：</b>本类型描述的是「终止之后仍然留着的东西」。
 * 用例层因此不会把这两个计数用于级联删除或批量停用——规格明令不得自动修改商品档案状态。
 *
 * @param skuCount     在册商品数，排除已逻辑删除的商品
 * @param accountCount 仍存在的账号数，含已停用的账号，排除已逻辑删除的账号
 */
public record TerminationImpact(long skuCount, long accountCount) {
}
