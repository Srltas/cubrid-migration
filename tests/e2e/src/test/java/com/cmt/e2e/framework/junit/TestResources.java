package com.cmt.e2e.framework.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TestResources {
    /**
     * Resource path under {@code src/test/resources/tests/}.
     * Example: {@code error/basic}
     */
    String value();
}
