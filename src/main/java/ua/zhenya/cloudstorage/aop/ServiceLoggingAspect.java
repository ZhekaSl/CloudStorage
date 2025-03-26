package ua.zhenya.cloudstorage.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
public class ServiceLoggingAspect {

    @Pointcut("execution(public * ua.zhenya.cloudstorage.service.impl.ResourceServiceImpl.*(..))")
    public void resourceServicePublicMethods() {}

    @Around("resourceServicePublicMethods()")
    public Object logServiceMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        Object[] methodArgs = joinPoint.getArgs();

        log.info("Enter: {}.{}() with argument[s] = {}", className, methodName, Arrays.toString(methodArgs));
        long startTime = System.currentTimeMillis();
        Object result;
        try {
            result = joinPoint.proceed();
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            log.info("Exit: {}.{}() with result = {}. Execution time = {} ms", className, methodName, result, duration);
            return result;
        } catch (Throwable throwable) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            log.error("Exception in {}.{}() with cause = '{}' and message = '{}'. Execution time = {} ms",
                    className, methodName,
                    throwable.getCause() != null ? throwable.getCause() : "NULL",
                    throwable.getMessage(), duration,
                    throwable);
            throw throwable;
        }
    }
}
