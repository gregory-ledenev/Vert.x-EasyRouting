package com.gl.vertx.easyrouting;

import java.lang.annotation.*;

/**
 * Interface defining annotations for HTTP header management in routing handlers.
 * Provides annotations to specify HTTP headers for request handling methods.
 */
public interface HttpHeaders {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(Headers.class)
    /**
     * Annotation to specify a single HTTP header that should be included in the response.
     * Can be used multiple times on the same method due to @Repeatable annotation.
     *
     * @return a String representing the header in "name: value" format
     */
    @interface Header {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    /**
     * Container annotation that holds multiple {@link Header} annotations.
     * This annotation is used automatically when multiple @Header annotations
     * are applied to the same method. Note: you cannot use both @Header and @Headers annotations, just use @Header
     * annotation multiple times.
     *
     * @return an array of Header annotations
     */
    @interface Headers {
        Header[] value();
    }
}
