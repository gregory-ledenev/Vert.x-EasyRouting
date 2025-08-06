package com.gl.vertx.easyrouting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to serve static files from a specified folder in the file system.
 * When applied to a handler method, it indicates that the method will serve files
 * from the specified directory path.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FileFromFolder {
    /**
     * The base directory path from which files will be served.
     *
     * @return the folder path as a String
     */
    String value();
}
