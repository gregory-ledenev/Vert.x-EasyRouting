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
 *    <li>Must be a public (and static if needed) method.</li>
 *    <li>Must accept exactly one parameter: input content that corresponds to the content type defined by annotation.</li>
 *    <li>Must be non-void, where the return type defines the object's type to convert to.</li>
 *    <li>Must not throw checked exceptions.</li>
 *  </ul>
 * <p>For example, the following converter converts objects that correspond to the <i>"text/user-string"</i> content
 * type to objects of the {@code User} type:</p>
 * <pre>{@code
 * @ConvertsFrom("text/user-string")
 * public User convertUserFromText(String text) {
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