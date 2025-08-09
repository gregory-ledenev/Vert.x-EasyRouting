package com.gl.vertx.easyrouting;

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
 *   <li>Must be a public method</li>
 *   <li>Must accept exactly one parameter of any type that defines a type of the objects to convert from</li>
 *   <li>Must return a non-void type compatible with the specified content type</li>
 *   <li>Must not throw checked exceptions</li>
 * </ul>
 * <p>For example, the following converter converts objects of the {@code User} type to objects that correspond to
 * the <i>"text/plain"</i> content type:</p>
 * <pre>{@code
 *
 * @ConvertsTo("text/user-string")
 * public String convertUserToText(User user) {
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
