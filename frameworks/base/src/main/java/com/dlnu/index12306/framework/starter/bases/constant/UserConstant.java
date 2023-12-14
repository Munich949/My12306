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
