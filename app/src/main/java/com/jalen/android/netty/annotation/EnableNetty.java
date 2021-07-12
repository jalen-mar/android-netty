package com.jalen.android.netty.annotation;

import com.jalen.android.bootstrap.annotation.AutoBoot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value = RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@AutoBoot
public @interface EnableNetty {
    String className() default "NettyPlugin";
    String packageName() default "com.jalen.android.netty";
    int[] port();
    int workerCapacity() default 0;
    int maxCapacity() default 128;
    boolean keepAlive() default true;
    boolean enableNagle() default true;
    int readBuffer() default 0;
    int writeBuffer() default 0;
}
