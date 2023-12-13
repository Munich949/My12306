package com.dlnu.index12306.framework.starter.log.core;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.SystemClock;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import com.dlnu.index12306.framework.starter.log.annotation.ILog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * {@link ILog} 日志打印 AOP 切面
 */
@Aspect
public class ILogPrintAspect {

    /**
     * 打印类或方法上的 {@link ILog}
     */
    @Around("@within(com.dlnu.index12306.framework.starter.log.annotation.ILog) || @annotation(com.dlnu.index12306.framework.starter.log.annotation.ILog)")
    public Object printMLog(ProceedingJoinPoint joinPoint) throws Throwable {

        long startTime = SystemClock.now();
        // 获取方法签名
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        // 获取日志记录器
        Logger log = LoggerFactory.getLogger(methodSignature.getDeclaringType());
        String beginTime = DateUtil.now();
        Object result = null;

        try {
            // 执行相应方法
            result = joinPoint.proceed();
        } finally {
            // 获取目标方法对象
            Method targetMethod = joinPoint.getTarget().getClass().getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());

            // 获取目标方法上的 ILog 注解，如果不存在，则获取目标类上的 ILog 注解
            ILog logAnnotation = Optional.ofNullable(targetMethod.getAnnotation(ILog.class)).orElse(joinPoint.getTarget().getClass().getAnnotation(ILog.class));
            if (logAnnotation != null) {
                ILogPrintDTO logPrint = new ILogPrintDTO();
                logPrint.setBeginTime(beginTime);
                if (logAnnotation.input()) {
                    logPrint.setInputParams(buildInput(joinPoint));
                }
                if (logAnnotation.output()) {
                    logPrint.setOutputParams(result);
                }
                // 获取请求的方法类型和请求URI
                String methodType = "", requestURI = "";
                try {
                    ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    assert servletRequestAttributes != null;
                    methodType = servletRequestAttributes.getRequest().getMethod();
                    requestURI = servletRequestAttributes.getRequest().getRequestURI();
                } catch (Exception ignored) {
                }

                // 打印日志信息
                log.info("[{}] {}, executeTime: {}ms, info: {}", methodType, requestURI, SystemClock.now() - startTime, JSON.toJSONString(logPrint));
            }
        }

        return result;
    }


    private Object[] buildInput(ProceedingJoinPoint joinPoint) {
        // 获取方法的输入参数数组
        Object[] args = joinPoint.getArgs();

        // 创建一个与输入参数数组相同长度的新数组，用于存放处理后的参数
        Object[] printArgs = new Object[args.length];

        for (int i = 0; i < args.length; i++) {
            // 判断当前参数是否为 HttpServletRequest 或 HttpServletResponse 类型，
            // 如果是，则不做处理，直接跳过
            if ((args[i] instanceof HttpServletRequest) || args[i] instanceof HttpServletResponse) {
                continue;
            }

            // 如果当前参数是 byte[] 类型，则将其替换为字符串 "byte array"
            if (args[i] instanceof byte[]) {
                printArgs[i] = "byte array";
            }
            // 如果当前参数是 MultipartFile 类型，则将其替换为字符串 "file"
            else if (args[i] instanceof MultipartFile) {
                printArgs[i] = "file";
            }
            // 否则，保留原始参数值
            else {
                printArgs[i] = args[i];
            }
        }

        return printArgs;
    }

}
