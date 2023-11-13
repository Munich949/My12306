package com.dlnu.index12306.framework.starter.bases.constant;

/**
 * 全局过滤器顺序执行常量类
 * 每个基础组件的执行顺序需要在全局定义，
 * 每个组件开发者要有全局思维，
 * 定义类似于这种过滤器或拦截器再或者 AOP 时，
 * 需要从组件功能再结合全局组件功能考虑到执行顺序问题。
 */
public final class FilterOrderConstant {

    /**
     * 用户信息传递过滤器执行顺序排序
     */
    public static final int USER_TRANSMIT_FILTER_ORDER = 100;
}
