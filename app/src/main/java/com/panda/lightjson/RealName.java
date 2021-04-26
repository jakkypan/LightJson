package com.panda.lightjson;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 如果java bean的字段定义跟json字段不一致时，可以通过此注解设置成json里的字段
 *
 * @author panda
 * created at 2021/3/12 7:24 PM
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RealName {
    String value();
}
