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

package com.gl.vertx.easyrouting;

import com.gl.vertx.easyrouting.annotations.*;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.text.MessageFormat;
import java.util.*;

/**
 * Controller class for handling RPC requests in a Vert.x application. Use {@code setupController()} method to register
 * the controller with a Vert.x Router as a {@code @POST} handler with a path specified by {@code @Rpc} annotation for
 * the target.
 */
public class RpcController {
    private final EasyRouting.RoutingContextHandler routingContextHandler;
    private final Object target;

    /**
     * Constructs a new RpcController with the specified target object.
     *
     * @param target the target object that contains the RPC methods to be handled. Use {@code @Rpc} annotation
     *               on the target object to specify the path for the controller.
     *
     */
    public RpcController(Object target, EasyRoutingContext easyRoutingContext) {
        this.target = target;
        routingContextHandler = new EasyRouting.RoutingContextHandler(null, target, easyRoutingContext);
    }

    /**
     * Handles incoming RPC requests by delegating to the routing context handler.
     *
     * @param ctx the routing context for the incoming request
     */
    @SuppressWarnings("unused")
    public void handleRpcRequests(RoutingContext ctx) {
        routingContextHandler.handle(ctx);
    }

    /**
     * Sets up the controller by registering it with the provided router. The controller will handle POST requests
     * at the path specified by the {@code @Rpc} annotation on the target object.
     *
     * @param router the Vert.x Router to register the controller with
     */
    public void setupController(Router router) {
        Rpc rpc = target.getClass().getAnnotation(Rpc.class);
        if (rpc != null) {
            router.post(rpc.path()).handler(this::handleRpcRequests);
            if (rpc.provideScheme())
                router.get(rpc.path()).handler(ctx -> ctx.response().end(getScheme()));
        }
    }

    /**
     * Generates a Java-like interface scheme for the RPC methods defined in the target object.
     * The generated interface includes method signatures for each RPC method, along with
     * necessary import statements for types used in method parameters and return types.
     *
     * @return a string representation of the generated Java interface scheme
     */
    public String getScheme() {
        TreeSet<String> classNames = new TreeSet<>();
        List<String> result = new ArrayList<>();

        Description classDescription = target.getClass().getAnnotation(Description.class);
        if (classDescription != null) {
            String[] lines = classDescription.value().split("\n");
            addDocumentation(result, lines, false);
        }
        result.add("public interface Service {");
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (! RpcContext.canExportMethod(method))
                continue;

            addClassName(method.getReturnType(), classNames);

            List<String> parameters = new ArrayList<>();
            for (Parameter parameter : method.getParameters()) {
                Class<?> type = parameter.getType();
                addClassName(type, classNames);
                parameters.add(type.getSimpleName() + " " + getParameterName(parameter));
            }
            Description methodDescription = method.getAnnotation(Description.class);
            if (methodDescription != null) {
                String[] lines = methodDescription.value().split("\n");
                addDocumentation(result, lines, true);
            }
            result.add(MessageFormat.format("    {0} {1}({2});",
                    method.getReturnType().getSimpleName(),
                    method.getName(),
                    String.join(", ", parameters)));
        }
        result.add("}");
        result.add(0, "");
        result.addAll(0, classNames);

        return String.join("\n", result);
    }

    private static void addDocumentation(List<String> result, String[] lines, boolean indent) {
        String spaces = "    ";
        result.add((indent ? spaces : "") + "/**");
        for (String line : lines)
            result.add((indent ? spaces : "") + " * " + line);
        result.add((indent ? spaces : "") + " */");
    }

    private static void addClassName(Class<?> type, TreeSet<String> classNames) {
        Class<?> importType = importType(type);
        if (importType != null)
            classNames.add(importType.getName() + ";");
    }

    private static Class<?> importType(Class<?> type) {
        Class<?> result = type;

        if (type.isArray())
            result = importType(type.getComponentType());
        else if (type.isPrimitive() || type.equals(Void.class))
            result = null;

        return result;
    }

    private static final Set<Class<? extends Annotation>> PARAMETER_ANNOTATIONS = Set.of(
            Param.class,
            PathParam.class,
//            OptionalParam.class,
            ContextParam.class,
            BodyParam.class
    );

    private static String getParameterName(Parameter parameter) {
        String name = parameter.getName();

        for (Class<? extends Annotation> annClass : PARAMETER_ANNOTATIONS) {
            Annotation annotation = parameter.getAnnotation(annClass);
            if (annotation != null) {
                try {
                    Method valueMethod = annClass.getMethod("value");
                    Object value = valueMethod.invoke(annotation);
                    if (value instanceof String && !((String) value).isEmpty()) {
                        return (String) value;
                    }
                } catch (Exception e) {
                    // do nothing
                }
            }
        }

        return name;
    }
}
