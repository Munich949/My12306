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

package com.dlnu.index12306.framework.starter.bases.safa;

import org.springframework.beans.factory.InitializingBean;

/**
 * FastJson 安全模式，开启后关闭类型隐式传递
 * Fastjson 的 "autoType" 特性是指在反序列化过程中，
 * 允许将 JSON 字符串自动转换为指定的 Java 类型。
 * 它提供了一种方便的方式，使得开发人员可以直接将 JSON 数据转换为相应的 Java 对象，而无需手动指定目标类。
 * 然而，这个特性也存在一定的安全风险。攻击者可以构造恶意的 JSON 数据，
 * 其中包含对不受信任的类的引用。当 "autoType" 特性被启用时，
 * Fastjson 会尝试根据 JSON 字符串中的类信息实例化相应的对象，
 * 从而可能导致潜在的安全问题，例如远程代码执行攻击。
 */
public class FastJsonSafeMode implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        System.setProperty("fastjson2.parser.safeMode", "true");
    }
}
