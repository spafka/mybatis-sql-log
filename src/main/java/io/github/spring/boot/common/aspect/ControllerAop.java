/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.github.spring.boot.common.aspect;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.Tuple2;
import io.vavr.collection.Stream;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.ibatis.javassist.ClassPool;
import org.apache.ibatis.javassist.CtClass;
import org.apache.ibatis.javassist.CtMethod;
import org.apache.ibatis.javassist.NotFoundException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.aspectj.MethodInvocationProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author spafka 2020-05-07
 */
@Slf4j
@Component
@Aspect
@Order(2)
public class ControllerAop {

    @Autowired
    HttpServletRequest request;
    @Autowired
    private ObjectMapper objectMapper;

    @Pointcut("@annotation(org.springframework.web.bind.annotation.RequestMapping) ||" +
            "@annotation(org.springframework.web.bind.annotation.GetMapping) ||" +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) ||" +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) ||" +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping)"
    )
    public void logAroundPointCut() {
    }


    @Around("logAroundPointCut()")
    public Object intoControllerLog(ProceedingJoinPoint point) throws Throwable {

        Throwable throwable = null;
        long start = System.currentTimeMillis();

        MethodInvocationProceedingJoinPoint mjp = (MethodInvocationProceedingJoinPoint) point;
        MethodSignature signature = (MethodSignature) mjp.getSignature();


        Method method = signature.getMethod();
        String clazz = method.getDeclaringClass().getName();


        Object[] args = point.getArgs();
        Parameter[] parameters = method.getParameters();


        val sb=new StringBuilder();

        ClassPool pool = ClassPool.getDefault();
        CtClass cc = pool.get(clazz);

        Class<?>[] parameterTypes = method.getParameterTypes();
        CtMethod methodX = cc.getDeclaredMethod(method.getName(), Arrays.stream(parameterTypes).map(x-> {
            try {
                return pool.get(x.getName());
            } catch (NotFoundException e) {
                return null;
            }
        }).toArray(CtClass[]::new));
        int line = methodX.getMethodInfo().getLineNumber(0);

        // 类名+方法名
        sb.append("at "+clazz+"."+method.getName())
                .append("(")
                .append(method.getDeclaringClass().getSimpleName()+".java")
                .append(":")
                .append(line)
                .append(")").toString();
        List<Tuple2<Parameter, Integer>> params = Stream.ofAll(Arrays.stream(parameters)).zipWithIndex().filter(x -> Try.of(
                () -> (args[x._2] instanceof Serializable)
                        && !(args[x._2] instanceof BindingResult)
                        && !(args[x._2] instanceof HttpServletRequest)
                        && !(args[x._2] instanceof HttpServletResponse
                        && (Try.of(() -> objectMapper.writeValueAsString(args[x._2])).isSuccess())
                )
        ).getOrElse(false)).collect(Collectors.toList());

        String requestURI = request.getRequestURI();
                sb.append(requestURI + " >>>>>>>>>\n");
        params.forEach(x -> {
            try {
                sb.append("    " + x._1.getName() + ": " + objectMapper.writeValueAsString(args[x._2]));
            } catch (Exception e) {
                //
            }
        });


        // 调用目标方法
        Object result = null;
        try {
            result = point.proceed();
        } catch (Throwable e) {
            throwable = e;
        }
        if (throwable == null) {
            String s1 = objectMapper.writeValueAsString(result);
            if (s1.length() > 1000) {
                sb.append("\n<<<<<<<<<< \n      ").append(s1, 0, 1000).append("..... \n");
            } else {
                sb.append("\n<<<<<<<<<< \n      ").append(s1).append("\n");
            }
            sb.append("cost ").append(System.currentTimeMillis() - start).append(" ms");
        }

        // 记录日志
        log.info(sb.toString());
        if (throwable != null) {
            throw throwable;
        }
        // 调用结果返回
        return result;

    }

}
