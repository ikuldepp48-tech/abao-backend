package cn.iocoder.yudao.module.restaurant.enums;

import cn.iocoder.yudao.framework.common.enums.RpcConstants;

/**
 * restaurant 模块的 API 常量
 */
public class ApiConstants {

    /** 服务名，需和 spring.application.name 保持一致 */
    public static final String NAME = "restaurant-server";

    /** RPC API 前缀 */
    public static final String PREFIX = RpcConstants.RPC_API_PREFIX + "/restaurant";

    /** API 版本 */
    public static final String VERSION = "1.0.0";
}
