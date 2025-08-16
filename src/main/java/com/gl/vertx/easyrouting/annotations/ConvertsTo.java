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
 * Annotation used to mark methods that converts the input value
 * into a result according to a specified content type. Such converters will be used to convert result values. Input type
 * is explicitly defined by the converter method's single parameter.
 * <p>The contract for conversion methods: </p>
 * <ul>
 *   <li>Must be a public static method</li>
 *   <li>Must accept exactly one parameter of any type that defines a type of the objects to convert from</li>
 *   <li>Must return a non-void type compatible with the specified content type</li>
 *   <li>Must not throw checked exceptions</li>
 * </ul>
 * <p>For example, the following converter converts objects of the {@code User} type to objects that correspond to
 * the <i>"text/plain"</i> content type:</p>
 * <pre>{@code
 *
 * @ConvertsTo("text/user-string")
 * public static String convertUserToText(User user) {
 *     return user.toString();
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConvertsTo {
    /**
     * Specifies the target content type for the conversion.
     *
     * @return the content type string that this converter produces
     */
    String value();
}
