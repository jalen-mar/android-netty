package com.jalen.android.netty.annotation;

import com.jalen.android.bootstrap.annotation.Inject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value = RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@Inject
public @interface RestController {
    boolean single() default true;
    String name();
    int index() default 0;
}
