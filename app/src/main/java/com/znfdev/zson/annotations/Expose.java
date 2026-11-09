package com.znfdev.zson.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a field as eligible for serialization/deserialization when the Zson
 *  instance was built with {@code excludeFieldsWithoutExposeAnnotation()}. */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Expose {
    boolean serialize() default true;
    boolean deserialize() default true;
}
