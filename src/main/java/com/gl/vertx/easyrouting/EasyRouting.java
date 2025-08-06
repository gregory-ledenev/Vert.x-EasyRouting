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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;

import static com.gl.vertx.easyrouting.HttpMethods.*;
import static com.gl.vertx.easyrouting.JWTUtil.ROLES;

/**
 * EasyRouting provides annotation-based HTTP request handling for Vert.x web applications. It simplifies route
 * configuration by allowing developers to define routes using annotations and automatically handles parameter binding
 * and response processing.
 * @version 0.9.5
 * @since 0.9.0
 */
public class EasyRouting {
    /**
     * Current version of the EasyRouting library.
     */
    public static final String VERSION = "0.9.5";


    private static final Logger logger = LoggerFactory.getLogger(EasyRouting.class);

    public static final String REDIRECT = "redirect:";
    private static final String ERROR_HANDLING_ANNOTATED_METHOD = "Error handling annotated method: {0}({1}). Error: {2}";

    /**
     * Sets up HTTP request handlers for all supported HTTP methods (GET, POST, DELETE, PUT, PATCH) based on annotated
     * methods in the target object.
     *
     * @param router the Vert.x Router instance to configure
     * @param target the object containing annotated handler methods
     */
    public static void setupController(Router router, Object target) {
        Objects.requireNonNull(router);
        Objects.requireNonNull(target);

        setupController(router, GET.class, target);
        setupController(router, POST.class, target);
        setupController(router, DELETE.class, target);
        setupController(router, PUT.class, target);
        setupController(router, PATCH.class, target);

        setupFailureHandler(router, target);
    }

    /**
     * Applies JWT authentication with the common "HS256" algorithm to a specified route in the given router.
     * Sample use would be as simple as:<br><br>
     * {@code JWTUtil.applyAuth(vertx, router, "/api/*", "very long password");}
     * <br><br>
     * that applies JWT authentication to all routes
     * starting with "/api/".
     *
     * @param vertx      the Vert.x instance
     * @param router     the router to which the route will be added
     * @param path       the path for which JWT authentication should be applied
     * @param jwtSecret  the secret key used for signing JWT tokens. It can be a plain password or a string in PEM format
     * @return the created route with JWT authentication applied
     */
    public static Route applyJWTAuth(Vertx vertx, Router router,
                                     String path,
                                     String jwtSecret) {
        return JWTUtil.applyAuth(vertx, router, path, jwtSecret);
    }

    private static void setupFailureHandler(Router router, Object target) {
        router.route().failureHandler(ctx -> {
            Throwable failure = ctx.failure();
            if (failure instanceof HttpException httpEx) {
                String redirect = redirect(httpEx.getStatusCode(), target);
                if (redirect != null) {
                    String originalUri = ctx.request().uri();
                    ctx.redirect(redirect + "?redirect=" + URLEncoder.encode(originalUri, StandardCharsets.UTF_8));
                    return;
                }
            }
            ctx.next();
        });
    }

    private static String redirect(int statusCode, Object target) {
        String result = null;

        all:
        for (Method method : target.getClass().getDeclaredMethods()) {
            StatusCode statusCodeAnnotation = method.getAnnotation(StatusCode.class);
            if (statusCodeAnnotation != null && statusCodeAnnotation.value() == statusCode) {
                Annotation methodAnnotation = method.getAnnotation(GET.class);
                if (methodAnnotation != null) {
                    try {
                        result = getPathForAnnotation(methodAnnotation);
                    } catch (Exception e) {
                        LoggerFactory.getLogger(target.getClass()).error("Failed to get redirect path for method: " + methodAnnotation, e);
                        break all;
                    }
                }
            }
        }

        return result;
    }

    private static List<Method> listHandlerMethods(Class<? extends Annotation> annotationClass, Object target) {
        List<Method> methods = new ArrayList<>();
        for (Method method : target.getClass().getDeclaredMethods()) {
            Annotation annotation = method.getAnnotation(annotationClass);
            if (annotation != null) {
                methods.add(method);
            }
        }
        sortMethods(methods, annotationClass);
        return methods;
    }

    private static void sortMethods(List<Method> methods, Class<? extends Annotation> annotationClass) {
        methods.sort((m1, m2) -> {
            try {
                String path1 = getPathForAnnotation(m1.getAnnotation(annotationClass));
                String path2 = getPathForAnnotation(m2.getAnnotation(annotationClass));

                // Handle special cases for root paths
                if (path1.equals("/") && path2.equals("/*")) return -1;
                if (path1.equals("/*") && path2.equals("/")) return 1;

                // Root path should come last compared to non-root paths
                if (path1.equals("/") && !path2.equals("/") && !path2.equals("/*")) return 1;
                if (!path1.equals("/") && !path1.equals("/*") && path2.equals("/")) return -1;

                // Split paths into segments
                String[] segments1 = path1.split("/");
                String[] segments2 = path2.split("/");

                // Count non-empty segments
                int nonEmptyCount1 = countNonEmptySegments(segments1);
                int nonEmptyCount2 = countNonEmptySegments(segments2);

                // 1. Paths with more segments should be on top
                if (nonEmptyCount1 != nonEmptyCount2) {
                    return nonEmptyCount2 - nonEmptyCount1; // Reverse order for "more on top"
                }

                // 2. Paths with the same number of segments should be sorted alphabetically segment by segment
                int minSegments = Math.min(segments1.length, segments2.length);
                for (int i = 0; i < minSegments; i++) {
                    String seg1 = i < segments1.length ? segments1[i] : "";
                    String seg2 = i < segments2.length ? segments2[i] : "";

                    // Skip empty segments
                    if (seg1.isEmpty() && seg2.isEmpty()) continue;

                    // 3. Within the same path level, wildcards should be at the bottom
                    boolean isWildcard1 = seg1.equals("*");
                    boolean isWildcard2 = seg2.equals("*");

                    if (isWildcard1 && !isWildcard2) return 1;
                    if (!isWildcard1 && isWildcard2) return -1;

                    // For non-wildcard segments, use alphabetical order
                    if (!isWildcard1 && !isWildcard2) {
                        int comp = seg1.compareTo(seg2);
                        if (comp != 0) return comp;
                    }
                }

                // If we've compared all segments, and they're equal up to this point,
                // check if one path has a wildcard at the end and the other doesn't
                boolean endsWithWildcard1 = path1.endsWith("*");
                boolean endsWithWildcard2 = path2.endsWith("*");

                if (endsWithWildcard1 && !endsWithWildcard2) return 1;
                if (!endsWithWildcard1 && endsWithWildcard2) return -1;

                return 0;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static int countNonEmptySegments(String[] segments) {
        int count = 0;
        for (String segment : segments) {
            if (!segment.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Sets up HTTP request handlers for a specific HTTP method annotation type.
     *
     * @param router          the Vert.x Router instance to configure
     * @param annotationClass the HTTP method annotation class to process
     * @param target          the object containing annotated handler methods
     */
    private static void setupController(Router router, Class<? extends Annotation> annotationClass, Object target) {
        Set<String> installedHandlers = new HashSet<>();

        try {
            for (Method method : listHandlerMethods(annotationClass, target)) {
                Annotation annotation = method.getAnnotation(annotationClass);
                if (annotation != null) {
                    logger.info("Setting up method for annotation: " + annotation);
                    String annotationValue = getPathForAnnotation(annotation);
                    if (annotationValue != null) {
                        // skip already installed handlers for the same path. the annotation contains both path and method, so it is enough.
                        String installedHandlerKey = annotation.toString();
                        if (installedHandlers.contains(installedHandlerKey))
                            continue;
                        installedHandlers.add(installedHandlerKey);

                        if (annotationClass == GET.class)
                            router.get(annotationValue).handler(createHandler(annotation, target));
                        else if (annotationClass == POST.class)
                            router.post(annotationValue).handler(createHandler(annotation, target));
                        else if (annotationClass == DELETE.class)
                            router.delete(annotationValue).handler(createHandler(annotation, target));
                        else if (annotationClass == PUT.class)
                            router.put(annotationValue).handler(createHandler(annotation, target));
                        else if (annotationClass == PATCH.class)
                            router.patch(annotationValue).handler(createHandler(annotation, target));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to setup handlers for annotation: " + annotationClass, e);
            throw new RuntimeException(e);
        }
    }

    private static boolean checkRequiredRoles(RoutingContext ctx, Method method) {
        boolean result = true;

        String[] requiredRoles = requiredRoles(method);
        if (requiredRoles.length > 0 && ctx.user() != null) {
            JsonArray rolesArray = ctx.user().principal().getJsonArray(ROLES, new JsonArray());
            for (String requiredRole : requiredRoles) {
                if (!rolesArray.contains(requiredRole)) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    private static Handler<RoutingContext> createHandler(Annotation annotation, Object target) {
        return ctx -> {
            try {
                MethodResult handlerMethod = getMethod(annotation,
                        ctx.request().params(),
                        ctx.request().method() == HttpMethod.POST ? ctx.request().formAttributes() : null,
                        target);
                if (handlerMethod == null) {
                    logger.error("No handler method for: \"" + annotation + "\" and parameters: " + ctx.request().params());
                    ctx.response().setStatusCode(404).end();
                } else if (checkRequiredRoles(ctx, handlerMethod.method)) {
                    if (handlerMethod.hasBodyParam) {
                        // Use the already parsed body instead of reading it again
                        Buffer bodyBuffer = ctx.getBody();
                        try {
                            Object[] args = methodParameterValues(ctx, handlerMethod.method(), handlerMethod.parameterNames, handlerMethod.method().getParameterTypes(), ctx.request().params(), bodyBuffer);
                            Object result = handlerMethod.method().invoke(target, args);
                            processHandlerResult(handlerMethod.method(), ctx, result);
                        } catch (Exception e) {
                            ctx.response()
                                    .setStatusCode(500)
                                    .end("Internal Server Error");
                            LoggerFactory.getLogger(target.getClass()).
                                    error("Error processing request body", e);
                        }
                    } else {
                        Object[] args = methodParameterValues(ctx, handlerMethod.method(), handlerMethod.parameterNames, handlerMethod.method().getParameterTypes(), ctx.request().params(), null);
                        Object result = handlerMethod.method().invoke(target, args);
                        processHandlerResult(handlerMethod.method(), ctx, result);
                    }
                } else {
                    throw new HttpException(403, "Access denied"); // exception to let failure handler handle it
                }
            } catch (HttpException e) {
                errorHandlerInvocation(annotation, ctx.request().params().names(), e);
                throw e;
            } catch (Exception e) {
                errorHandlerInvocation(annotation, ctx.request().params().names(), e);
                throw new HttpException(500, e);
            }
        };
    }

    private static void errorHandlerInvocation(Annotation annotation, Set<String> parameterNames, Exception exception) {
        logger.error(MessageFormat.format(ERROR_HANDLING_ANNOTATED_METHOD,
                annotation,
                String.join(", ", parameterNames),
                exception));
    }

    record MethodResult(Method method, String[] parameterNames, boolean hasBodyParam) {
    }

    private static boolean warnedAboutMissingParameterNames = false;

    private static MethodResult getMethod(Annotation annotation,
                                          MultiMap parameters,
                                          MultiMap formAttributes,
                                          Object target) {
        MultiMap lowercaseParams = MultiMap.caseInsensitiveMultiMap();
        lowercaseParams.addAll(parameters);

        MultiMap lowercaseFormAttributes = formAttributes != null ? MultiMap.caseInsensitiveMultiMap() : null;
        if (lowercaseFormAttributes != null)
            lowercaseFormAttributes.addAll(formAttributes);

        for (Method method : target.getClass().getDeclaredMethods()) {
            Annotation methodAnnotation = method.getAnnotation(annotation.annotationType());
            if (methodAnnotation != null && methodAnnotation.equals(annotation)) {
                List<String> paramNames = new ArrayList<>();
                int matchedParamCount = 0;
                int optionalParamCount = 0;
                int otherParamCount = 0;
                boolean hasBodyParam = false;
                boolean isFormHandler = method.getAnnotation(Form.class) != null;

                Annotation[][] parameterAnnotations = method.getParameterAnnotations();
                for (Parameter parameter : method.getParameters()) {

                    if (parameter.getAnnotation(Param.class) != null) {
                        Param param = parameter.getAnnotation(Param.class);
                        paramNames.add(param.value());
                        String lowerCaseParam = param.value().toLowerCase();
                        if (lowercaseParams.get(lowerCaseParam) != null)
                            matchedParamCount++;
                        else if (isFormHandler && lowercaseFormAttributes != null && lowercaseFormAttributes.get(lowerCaseParam) != null)
                            otherParamCount++;
                    } else if (parameter.getAnnotation(OptionalParam.class) != null) {
                        OptionalParam param = parameter.getAnnotation(OptionalParam.class);
                        paramNames.add(param.value());
                        matchedParamCount++;
                        String lowerCaseParam = param.value().toLowerCase();
                        if (lowercaseParams.get(lowerCaseParam) == null)
                            optionalParamCount++;
                        else if (isFormHandler && lowercaseFormAttributes != null && lowercaseFormAttributes.get(lowerCaseParam) != null)
                            otherParamCount++;
                    } else if (parameter.getAnnotation(BodyParam.class) != null) {
                        BodyParam param = parameter.getAnnotation(BodyParam.class);
                        otherParamCount++;
                        hasBodyParam = true;
                        paramNames.add(param.value());
                    } if (parameter.getAnnotation(PathParam.class) != null) {
                        PathParam param = parameter.getAnnotation(PathParam.class);
                        otherParamCount++;
                        paramNames.add(param.value());
                    } if (parameter.getAnnotation(UploadsParam.class) != null) {
                        otherParamCount++;
                        paramNames.add("uploads");
                    } else {
                        if ("arg0".equals(parameter.getName()) && ! warnedAboutMissingParameterNames) {
                            warnedAboutMissingParameterNames = true;
                            logger.warn("Parameter names are missing. Either use @Param annotations or compile project with -parameters option");
                        }
                        paramNames.add(parameter.getName());
                        String lowerCaseParam = parameter.getName().toLowerCase();
                        if (lowercaseParams.get(lowerCaseParam) != null)
                            matchedParamCount++;
                        else if (isFormHandler && lowercaseFormAttributes != null && lowercaseFormAttributes.get(lowerCaseParam) != null)
                            otherParamCount++;
                    }
                }

                int totalParamCount = matchedParamCount + otherParamCount;
                if (method.getParameterCount() == totalParamCount && matchedParamCount == parameters.size() + optionalParamCount) {
                    return new MethodResult(method, paramNames.toArray(new String[0]), hasBodyParam);
                }
            }
        }
        return null;
    }

    private static Object[] methodParameterValues(RoutingContext ctx, Method method,
                                                  String[] parameterNames,
                                                  Class<?>[] parameterTypes,
                                                  MultiMap requestParameters,
                                                  Object body) {
        List<Object> result = new ArrayList<>();

        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameterNames.length; i++) {
            Parameter parameter = parameters[i];

            if (parameter.getAnnotation(BodyParam.class) != null) {
                result.add(convertBody(parameterTypes[i], body));
            } else if (parameter.getAnnotation(UploadsParam.class) != null) {
                result.add(ctx.fileUploads());
            } else if (parameter.getAnnotation(PathParam.class) != null) {
                result.add(ctx.normalizedPath());
            } else {
                OptionalParam optionalParam = parameter.getAnnotation(OptionalParam.class);
                if (optionalParam != null) {
                    result.add(convertValue(parameterNames[i], parameterTypes[i], requestParameters, optionalParam.defaultValue()));
                } else {
                    result.add(convertValue(parameterNames[i], parameterTypes[i], requestParameters, null));
                }
            }
        }

        return result.toArray(new Object[0]);
    }

    private static Object convertBody(Class<?> parameterType, Object body) {
        if (body == null) {
            return null;
        }

        if (parameterType == String.class) {
            return body.toString();
        } else if (parameterType == JsonObject.class) {
            return body instanceof JsonObject jsonObject ? jsonObject : new JsonObject(body.toString());
        } else if (parameterType == JsonArray.class) {
            return body instanceof JsonArray jsonArray ? jsonArray : new JsonArray(body.toString());
        } else if (parameterType == Integer.class || parameterType == int.class) {
            return Integer.parseInt(body.toString());
        } else if (parameterType == Long.class || parameterType == long.class) {
            return Long.parseLong(body.toString());
        } else if (parameterType == Double.class || parameterType == double.class) {
            return Double.parseDouble(body.toString());
        } else if (parameterType == Boolean.class || parameterType == boolean.class) {
            return Boolean.parseBoolean(body.toString());
        } else if (parameterType == Buffer.class) {
            return body instanceof Buffer buffer ? buffer : Buffer.buffer(body.toString());
        } else if (!parameterType.isPrimitive() && !parameterType.isArray()) {
            try {
                return new JsonMapper().readValue(body.toString(), parameterType);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to convert body to " + parameterType.getName(), e);
            }
        }

        throw new IllegalArgumentException("Unsupported body type: " + parameterType);
    }

    private static Object convertValue(String parameterName, Class<?> parameterType, MultiMap parameters, String defaultValue) {
        String value = parameters.get(parameterName);
        return convertValue(parameterType, value != null ? value : defaultValue);
    }

    private static Object convertValue(Class<?> parameterType, String value) {
        if (parameterType == Integer.class || parameterType == int.class)
            return value != null ? Integer.parseInt(value) : 0;
        if (parameterType == Short.class || parameterType == short.class)
            return value != null ? Short.valueOf(value) : 0;
        if (parameterType == Byte.class || parameterType == byte.class)
            return value != null ? Byte.valueOf(value) : 0;
        if (parameterType == Boolean.class || parameterType == boolean.class)
            return Boolean.valueOf(value);
        else if (parameterType == Double.class || parameterType == double.class)
            return value != null ? Double.parseDouble(value) : 0;
        else if (parameterType == Float.class || parameterType == float.class)
            return value != null ? Float.parseFloat(value) : 0;
        else
            return value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void processHandlerResult(Method method, RoutingContext ctx, Object result) {
        if (method.getReturnType() == void.class) {
            ctx.end();
        } else {
            if (result instanceof Result<?> handlerResult) {
                handlerResult.setResultClass(method.getReturnType());
                handlerResult.setAnnotations(method.getAnnotations());
                handlerResult.handle(ctx);
            } else {
                Result handlerResult = new Result(result);
                handlerResult.setResultClass(method.getReturnType());
                handlerResult.setAnnotations(method.getAnnotations());
                handlerResult.handle(ctx);
            }
        }
    }
}
