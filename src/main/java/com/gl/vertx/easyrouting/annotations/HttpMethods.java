/*
 *
 * Copyright 2025 Gregory Ledenev (gregory.ledenev37@gmail.com)
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the “Software”), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * /
 */

package com.gl.vertx.easyrouting.annotations;


import java.lang.annotation.*;
import java.lang.reflect.Method;

/**
 * Contains annotations for HTTP methods used in routing handlers. These annotations are used to mark methods that
 * handle specific HTTP requests.
 */
@SuppressWarnings("unused")
public class HttpMethods {
    /**
     * Marks a method as handling HTTP GET requests.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface GET {
        /**
         * @return the path pattern for this GET endpoint
         */
        String value();
        /**
         * @return the roles required to access this endpoint
         */
        String[] requiredRoles() default {};
    }

    /**
     * Marks a method as handling HTTP POST requests.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface POST {
        /**
         * @return the path pattern for this POST endpoint
         */
        String value();
        /**
         * @return the roles required to access this endpoint
         */
        String[] requiredRoles() default {};
    }

    /**
     * Marks a method as handling HTTP DELETE requests.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface DELETE {
        /**
         * @return the path pattern for this DELETE endpoint
         */
        String value();
        /**
         * @return the roles required to access this endpoint
         */
        String[] requiredRoles() default {};
    }

    /**
     * Marks a method as handling HTTP PUT requests.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface PUT {
        /**
         * @return the path pattern for this PUT endpoint
         */
        String value();
        /**
         * @return the roles required to access this endpoint
         */
        String[] requiredRoles() default {};

    }

    /**
     * Marks a method as handling HTTP PATCH requests.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface PATCH {
        /**
         * @return the path pattern for this PATCH endpoint
         */
        String value();
        /**
         * @return the roles required to access this endpoint
         */
        String[] requiredRoles() default {};
    }

    /**
     * Retrieves the roles required to access a specific endpoint based on its annotation.
     * Supported annotations include HTTP methods such as GET, POST, PUT, DELETE, and PATCH.
     *
     * @param annotation the annotation representing the HTTP method
     * @return an array of roles required for the endpoint, or null if the annotation is not supported
     */
    public static String[] getRolesForAnnotation(Annotation annotation) {
        if (annotation instanceof HttpMethods.GET get) return get.requiredRoles();
        if (annotation instanceof HttpMethods.POST post) return post.requiredRoles();
        if (annotation instanceof HttpMethods.PUT put) return put.requiredRoles();
        if (annotation instanceof HttpMethods.DELETE delete) return delete.requiredRoles();
        if (annotation instanceof HttpMethods.PATCH patch) return patch.requiredRoles();
        return null;
    }

    /**
     * Retrieves the path associated with a specific HTTP method annotation.
     * Supported annotations include GET, POST, PUT, DELETE, and PATCH.
     *
     * @param annotation the annotation representing the HTTP method
     * @return the path associated with the provided annotation, or null if the annotation is not supported
     */
    public static String getPathForAnnotation(Annotation annotation) {
        if (annotation instanceof HttpMethods.GET get) return get.value();
        if (annotation instanceof HttpMethods.POST post) return post.value();
        if (annotation instanceof HttpMethods.PUT put) return put.value();
        if (annotation instanceof HttpMethods.DELETE delete) return delete.value();
        if (annotation instanceof HttpMethods.PATCH patch) return patch.value();
        return null;
    }

    /**
     * Gets the required roles for a method.
     * @param method the method to get roles for
     * @return the required roles for the method
     */
    public static String[] requiredRoles(Method method) {
        try {
            for (Annotation annotation : method.getAnnotations()) {
                String[] roles = getRolesForAnnotation(annotation);
                if (roles != null) return roles;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get required roles", e);
        }
        return new String[0];
    }
}
