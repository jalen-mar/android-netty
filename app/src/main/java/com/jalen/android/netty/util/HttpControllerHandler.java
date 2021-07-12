package com.jalen.android.netty.util;

import com.jalen.android.bootstrap.beans.BeanFactory;
import com.jalen.android.bootstrap.exception.ReflectException;
import com.jalen.android.bootstrap.reflect.AnnotationUtil;
import com.jalen.android.bootstrap.reflect.MethodUtil;
import com.jalen.android.netty.annotation.RequestMapping;
import com.jalen.android.netty.handler.HttpMessageHandler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class HttpControllerHandler {
    public static HttpMessageHandler.Action findController(String uri, String method) {
        int paramIndex = uri.indexOf('?');
        if (paramIndex != -1) {
            uri = uri.substring(0, paramIndex);
        }
        BeanFactory factory = BeanFactory.getInstance();
        HttpMessageHandler.Action action = factory.getBean(uri);
        if (action != null) {
            return action;
        }
        String[] actionItem = uri.split("/");
        StringBuilder builder = new StringBuilder();
        for (String item : actionItem) {
            if (item.length() != 0) {
                builder.append('/').append(item);
                Object targetObject = factory.getBean(builder.toString());
                if (targetObject != null) {
                    Class<?> targetClass = targetObject.getClass();
                    Method[] methods = targetClass.getMethods();
                    for (Method targetMethod : methods) {
                        Annotation annotation = AnnotationUtil.getAnnotation(targetMethod, RequestMapping.class);
                        if (annotation != null) {
                            try {
                                if (!method.equals(MethodUtil.invoke(annotation, "method"))) {
                                    continue;
                                }
                                String name = MethodUtil.invoke(annotation, "name");
                                if (!uri.substring(builder.toString().length()).equals(name)) {
                                    continue;
                                }
                                action = new HttpMessageHandler.Action(targetObject, targetMethod);
                                factory.setBean(uri, action);
                                return action;
                            } catch (ReflectException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
