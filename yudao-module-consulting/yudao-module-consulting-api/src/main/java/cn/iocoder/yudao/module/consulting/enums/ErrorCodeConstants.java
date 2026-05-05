package cn.iocoder.yudao.module.consulting.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * consulting 模块错误码
 * <p>
 * 错误码范围：1-012-000-000 ~ 1-012-999-999
 */
public interface ErrorCodeConstants {

    // ========== 通用错误 ==========
    ErrorCode NO_CROSS_TENANT_PERMISSION = new ErrorCode(1_012_000_001, "无跨租户访问权限");
    ErrorCode CLIENT_NOT_EXISTS = new ErrorCode(1_012_000_002, "客户不存在");
    ErrorCode NO_CLIENT_ACCESS = new ErrorCode(1_012_000_003, "无权访问该客户数据");
    ErrorCode CLIENT_NAME_EXISTS = new ErrorCode(1_012_000_004, "客户名称已存在");
    ErrorCode CONTACT_NOT_EXISTS = new ErrorCode(1_012_000_005, "联系人不存在");

    // ========== 咨询项目 ==========
    ErrorCode ENGAGEMENT_NOT_EXISTS = new ErrorCode(1_012_000_006, "咨询项目不存在");
    ErrorCode ENGAGEMENT_CODE_EXISTS = new ErrorCode(1_012_000_007, "项目编号已存在");
    ErrorCode ENGAGEMENT_PHASE_INVALID = new ErrorCode(1_012_000_008, "项目阶段无效（必须在1-6之间）");
    ErrorCode ENGAGEMENT_PHASE_SKIP = new ErrorCode(1_012_000_009, "不能跳过阶段，当前阶段为{0}，目标阶段{1}");
    ErrorCode ENGAGEMENT_PHASE_ALREADY_DONE = new ErrorCode(1_012_000_010, "阶段{0}已经完成");
    ErrorCode ENGAGEMENT_PHASE_NOT_IN_PROGRESS = new ErrorCode(1_012_000_011, "项目不在进行中状态，无法推进阶段");

}
