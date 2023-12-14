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

package com.dlnu.index12306.framework.starter.bases;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Application context holder.
 * 很多时候我们需要在非 Spring Bean 中使用到 Spring Bean
 * 依赖 Spring 提供的 ApplicationContextAware接口，
 * 来将 Spring IOC 容器的对象放到一个自定义容器中，并持有 Spring IOC 容器。
 * 这样就可以通过自定义容器访问 Spring IOC 容器获取 Spring Bean。
 */
public class ApplicationContextHolder implements ApplicationContextAware {

    private static ApplicationContext CONTEXT;

    /**
     * 根据类型获取 IOC 容器的 Bean
     */
    public static <T> T getBean(Class<T> clazz) {
        return CONTEXT.getBean(clazz);
    }

    /**
     * 根据类名获取 IOC 容器的 Bean
     */
    public static Object getBean(String name) {
        return CONTEXT.getBean(name);
    }

    /**
     * 根据类型和类名获取 IOC 容器的 Bean
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return CONTEXT.getBean(name, clazz);
    }

    /**
     * 根据类型获取一组 IOC 容器的 Bean
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        return CONTEXT.getBeansOfType(clazz);
    }

    /**
     * 查找 Bean 是否有注释
     */
    public static <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType) {
        return CONTEXT.findAnnotationOnBean(beanName, annotationType);
    }

    /**
     * 获取 ApplicationContext.
     */
    public static ApplicationContext getInstance() {
        return CONTEXT;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ApplicationContextHolder.CONTEXT = applicationContext;
    }
}
