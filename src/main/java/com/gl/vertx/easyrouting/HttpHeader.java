package com.gl.vertx.easyrouting;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(HttpHeaders.class)
        /**
         * Annotation to specify a single HTTP header that should be included in the response.
         * Can be used multiple times on the same method due to @Repeatable annotation.
         *
         * @return a String representing the header in "name: value" format
         */
public @interface HttpHeader {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
        /**
         * Container annotation that holds multiple {@link HttpHeaders.Header} annotations.
         * This annotation is used automatically when multiple @Header annotations
         * are applied to the same method. Note: you cannot use both @Header and @Headers annotations, just use @Header
         * annotation multiple times.
         *
         * @return an array of Header annotations
         */
@interface HttpHeaders {
    HttpHeader[] value();
}
