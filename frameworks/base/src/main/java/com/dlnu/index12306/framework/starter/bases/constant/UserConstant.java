package com.dlnu.index12306.framework.starter.bases.constant;

/**
 * 用户常量
 * <p>这些常量不止在用户组件库中使用。
 * 因为在网关中，将用户 Token 进行解析，并放到 HTTP Header 中，最终放到用户请求上下文，也需要用到这些用户常量。
 * 所以将这些用户常量封装到基础组件库中。</p>
 */
public final class UserConstant {

    /**
     * 用户 ID Key
     */
    public static final String USER_ID_KEY = "userId";

    /**
     * 用户名 Key
     */
    public static final String USER_NAME_KEY = "username";

    /**
     * 用户真实名称 Key
     */
    public static final String REAL_NAME_KEY = "realName";

    /**
     * 用户 Token Key
     */
    public static final String USER_TOKEN_KEY = "token";
}
