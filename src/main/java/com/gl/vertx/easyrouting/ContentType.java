package com.gl.vertx.easyrouting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to specify the Content-Type header for HTTP responses.
 * This annotation can be applied to handler methods to set the response content type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ContentType {
    /**
     * The content type value to be set in the response header.
     * @return the MIME type string (e.g., "text/plain", "application/json")
     */
    String value();
}
