package com.cmt.e2e.framework.junit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TestResources {
    /**
     * src/test/resources/tests/ 하위의 리소스 경로를 지정
     * 예: "error/basic"
     */
    String value();
}
