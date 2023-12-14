/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dlnu.index12306.framework.starter.common.toolkit;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;

/**
 * 断言工具类
 */
public class Assert {

    /**
     * 判断表达式是否为真，如果不为真，则抛出 IllegalArgumentException 异常
     *
     * @param expression 判断表达式
     * @param message    异常信息
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 判断表达式是否为真，如果不为真，则抛出 IllegalArgumentException 异常
     * 默认异常信息为 "[Assertion failed] - this expression must be true"
     *
     * @param expression 判断表达式
     */
    public static void isTrue(boolean expression) {
        isTrue(expression, "[Assertion failed] - this expression must be true");
    }

    /**
     * 判断对象是否为 null，如果不为 null，则抛出 IllegalArgumentException 异常
     *
     * @param object  对象
     * @param message 异常信息
     */
    public static void isNull(Object object, String message) {
        if (object != null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 判断对象是否为 null，如果不为 null，则抛出 IllegalArgumentException 异常
     * 默认异常信息为 "[Assertion failed] - the object argument must be null"
     *
     * @param object 对象
     */
    public static void isNull(Object object) {
        isNull(object, "[Assertion failed] - the object argument must be null");
    }

    /**
     * 判断对象是否不为 null，如果为 null，则抛出 IllegalArgumentException 异常
     *
     * @param object  对象
     * @param message 异常信息
     */
    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 判断对象是否不为 null，如果为 null，则抛出 IllegalArgumentException 异常
     * 默认异常信息为 "[Assertion failed] - this argument is required; it must not be null"
     *
     * @param object 对象
     */
    public static void notNull(Object object) {
        notNull(object, "[Assertion failed] - this argument is required; it must not be null");
    }

    /**
     * 判断集合是否为空，如果为空，则抛出 IllegalArgumentException 异常
     *
     * @param collection 集合
     * @param message    异常信息
     */
    public static void notEmpty(Collection<?> collection, String message) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 判断集合是否为空，如果为空，则抛出 IllegalArgumentException 异常
     * 默认异常信息为 "[Assertion failed] - this collection must not be empty: it must contain at least 1 element"
     *
     * @param collection 集合
     */
    public static void notEmpty(Collection<?> collection) {
        notEmpty(collection,
                "[Assertion failed] - this collection must not be empty: it must contain at least 1 element");
    }

    /**
     * 判断Map是否为空，如果为空，则抛出 IllegalArgumentException 异常
     *
     * @param map     Map
     * @param message 异常信息
     */
    public static void notEmpty(Map<?, ?> map, String message) {
        if (CollectionUtils.isEmpty(map)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 判断Map是否为空，如果为空，则抛出 IllegalArgumentException 异常
     * 默认异常信息为 "[Assertion failed] - this map must not be empty; it must contain at least one entry"
     *
     * @param map Map
     */
    public static void notEmpty(Map<?, ?> map) {
        notEmpty(map, "[Assertion failed] - this map must not be empty; it must contain at least one entry");
    }

    /**
     * 判断字符串是否为空，如果为空，则抛出 IllegalArgumentException 异常
     *
     * @param str     字符串
     * @param message 异常信息
     */
    public static void notEmpty(String str, String message) {
        if (StringUtils.isEmpty(str)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 判断字符串是否为空，如果为空，则抛出 IllegalArgumentException 异常
     * 默认异常信息为 "[Assertion failed] - this string must not be empty"
     *
     * @param str 字符串
     */
    public static void notEmpty(String str) {
        if (StringUtils.isEmpty(str)) {
            notEmpty(str, "[Assertion failed] - this string must not be empty");
        }
    }

    /**
     * 判断字符串是否为空白，如果为空白，则抛出 IllegalArgumentException 异常
     *
     * @param str     字符串
     * @param message 异常信息
     */
    public static void notBlank(String str, String message) {
        if (org.apache.commons.lang3.StringUtils.isBlank(str)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 判断字符串是否为空白，如果为空白，则抛出 IllegalArgumentException 异常
     * 默认异常信息为 "[Assertion failed] - this string must not be blank"
     *
     * @param str 字符串
     */
    public static void notBlank(String str) {
        notBlank(str, "[Assertion failed] - this string must not be blank");
    }

    /**
     * 判断字符串是否有文本内容，如果没有文本内容，则抛出 IllegalArgumentException 异常
     *
     * @param text    字符串
     * @param message 异常信息
     */
    public static void hasText(String text, String message) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 判断字符串是否有文本内容，如果没有文本内容，则抛出 IllegalArgumentException 异常
     * 默认异常信息为 "[Assertion failed] - this String argument must have text; it must not be null, empty, or blank"
     *
     * @param text 字符串
     */
    public static void hasText(String text) {
        hasText(text,
                "[Assertion failed] - this String argument must have text; it must not be null, empty, or blank");
    }
}
