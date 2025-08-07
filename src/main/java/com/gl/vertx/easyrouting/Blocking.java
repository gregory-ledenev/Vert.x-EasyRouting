package com.gl.vertx.easyrouting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that should be executed as blocking operations.
 * When applied to a method, it indicates that the method's execution
 * will be automatically processed on a worker thread rather than the event loop,
 * preventing the event loop from being blocked. Use this annotation to mark
 * operations that may take a long time and safely add any blocking code to such methods.
 *
 * <p>Usage example:</p>
 * <pre>
 *     {@code @Blocking}
 *     public void someBlockingOperation() {
 *         // Method code that may block
 *     }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Blocking {
}
