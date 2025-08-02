/*
Copyright 2025 Gregory Ledenev (gregory.ledenev37@gmail.com)

MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the “Software”), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package com.gl.vertx.easyrouting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contains annotations for HTTP methods used in routing handlers. These annotations are used to mark methods that
 * handle specific HTTP requests.
 */
public class HttpMethods {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    /**
     * Marks a method as handling HTTP GET requests.
     */
    public @interface GET {
        /**
         * @return the path pattern for this GET endpoint
         */
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    /**
     * Marks a method as handling HTTP POST requests.
     */
    public @interface POST {
        /**
         * @return the path pattern for this POST endpoint
         */
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    /**
     * Marks a method as handling HTTP DELETE requests.
     */
    public @interface DELETE {
        /**
         * @return the path pattern for this DELETE endpoint
         */
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    /**
     * Marks a method as handling HTTP PUT requests.
     */
    public @interface PUT {
        /**
         * @return the path pattern for this PUT endpoint
         */
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    /**
     * Marks a method as handling HTTP PATCH requests.
     */
    public @interface PATCH {
        /**
         * @return the path pattern for this PATCH endpoint
         */
        String value();
    }
}
