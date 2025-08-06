package com.gl.vertx.easyrouting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to serve static files from the classpath resources.
 * When applied to a handler method, it indicates that the method should
 * serve files from resources relative to the specified class's location.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FileFromResource {
    /**
     * The class relative to which resources should be loaded.
     *
     * @return the class used as a base for resource loading
     */
    Class<?> value();
}
