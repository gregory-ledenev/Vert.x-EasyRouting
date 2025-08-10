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
 * Annotation used to mark methods that convert input data from specific content types
 * into target objects. Such converters will be used to convert body values to appropriate objects. Output type
 * is explicitly defined by the converter method's return type.
 * <p>The contract for conversion methods: </p>
 *  <ul>
 *    <li>Must be a public static method.</li>
 *    <li>Must accept exactly one parameter: input content that corresponds to the content type defined by annotation.</li>
 *    <li>Must be non-void, where the return type defines the object's type to convert to.</li>
 *    <li>Must not throw checked exceptions.</li>
 *  </ul>
 * <p>For example, the following converter converts objects that correspond to the <i>"text/user-string"</i> content
 * type to objects of the {@code User} type:</p>
 * <pre>{@code
 * @ConvertsFrom("text/user-string")
 * public static User convertUserFromText(String text) {
 *     // Convert text to User object
 *     return new User(text);
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConvertsFrom {
    /**
     * Specifies the source content type for the conversion.
     *
     * @return the content type string that this converter consumes
     */
    String value();
}