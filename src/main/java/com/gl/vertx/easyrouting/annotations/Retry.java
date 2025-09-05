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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that should be retried on failure. Methods annotated with @Retry will automatically retry
 * execution if they throw an exception (which is not in the exclusion list), up to the specified maximum number of
 * retries with a configurable delay between attempts.
 * <p>
 * Note:
 * <ol>
 *  <li>This annotation automatically includes @Blocking behavior.</li>
 *  <li>Intermediate exceptions thrown during retry attempts will not be forwarded to handlers. Only the last exception
 *  will be.</li>
 * <ol/>
 */
@Retention(RetentionPolicy.RUNTIME)
@Blocking
@Target(ElementType.METHOD)
public @interface Retry {
    /**
     * Maximum number of retry attempts.
     * @return the maximum number of retries (default: 3)
     */
    int maxRetries() default 3;
    
    /**
     * Delay between retry attempts in milliseconds.
     * @return the delay in milliseconds (default: 100)
     */
    long delay() default 100;

    /**
     * Exception types that should not trigger retries.
     * If any of these exception types are thrown, the method will fail immediately
     * without attempting retries.
     * @return array of exception classes to exclude from retry logic (default: empty)
     */
    Class<?>[] excludeExceptions() default {};
}
