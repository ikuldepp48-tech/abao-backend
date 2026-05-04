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

}
