package com.gl.vertx.easyrouting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Annotation used to specify a response when a method returns null.
 * When applied to a method, if the method returns null, the specified
 * value and status code will be sent as the response instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NotNullResult {
    /**
     * The message to return when the method result is null.
     *
     * @return the message to be sent in the response
     */
    String value() default "";

    /**
     * The HTTP status code to return when the method result is null.
     *
     * @return the HTTP status code for the response, defaults to 204 (No Content)
     */
    int statusCode() default 204;
}
