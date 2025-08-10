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

import java.lang.annotation.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;

import static com.gl.vertx.easyrouting.HttpMethods.*;
import static com.gl.vertx.easyrouting.JWTUtil.ROLES;
import static com.gl.vertx.easyrouting.Result.CONTENT_TYPE;

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

        setupAnnotatedConverters(target);
    }

    private static void setupAnnotatedConverters(Object target) {
        if (target instanceof AnnotatedConvertersHolder annotatedConvertersHolder) {
            AnnotatedConverters annotatedConverters = annotatedConvertersHolder.getAnnotatedConverters();
            annotatedConverters.collectConverters(target);
        }
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
            HandlesStatusCode statusCodeAnnotation = method.getAnnotation(HandlesStatusCode.class);
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
        return new RoutingContextHandler(annotation, target);
    }

    private static class RoutingContextHandler implements Handler<RoutingContext> {
        private final Annotation annotation;
        private final Object target;
        private final AnnotatedConverters annotatedConverters;

        public RoutingContextHandler(Annotation annotation, Object target) {
            this.annotation = annotation;
            this.target = target;

            if (target instanceof AnnotatedConvertersHolder annotatedConvertersHolder) {
                this.annotatedConverters = annotatedConvertersHolder.getAnnotatedConverters();
            } else {
                this.annotatedConverters = new AnnotatedConverters();
                this.annotatedConverters.collectConverters(target);
            }
        }

        @Override
        public void handle(RoutingContext ctx) {
            try {
                MethodResult handlerMethod = getMethod(annotation,
                        ctx.request().params(),
                        ctx.request().method() == HttpMethod.POST ? ctx.request().formAttributes() : null);
                if (handlerMethod == null) {
                    logger.error("No handler method for: \"" + annotation + "\" and parameters: " + ctx.request().params().names());
                    ctx.response().setStatusCode(404).end();
                } else if (checkRequiredRoles(ctx, handlerMethod.method)) {
                    if (handlerMethod.hasBodyParam) {
                        // Use the already parsed body instead of reading it again
                        Buffer bodyBuffer = ctx.getBody();
                        try {
                            Object[] args = methodParameterValues(ctx, handlerMethod.method(), handlerMethod.parameterNames, handlerMethod.method().getParameterTypes(), ctx.request().params(), bodyBuffer);
                            invokeHandlerMethod(ctx, handlerMethod, args);
                        } catch (Exception e) {
                            ctx.response()
                                    .setStatusCode(500)
                                    .end("Internal Server Error");
                            LoggerFactory.getLogger(target.getClass()).
                                    error("Error processing request body", e);
                        }
                    } else {
                        Object[] args = methodParameterValues(ctx, handlerMethod.method(), handlerMethod.parameterNames, handlerMethod.method().getParameterTypes(), ctx.request().params(), null);
                        invokeHandlerMethod(ctx, handlerMethod, args);
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
        }

        private void invokeHandlerMethod(RoutingContext ctx, MethodResult handlerMethod, Object[] args) throws IllegalAccessException, InvocationTargetException {
            if (handlerMethod.method().isAnnotationPresent(Blocking.class)) {
                ctx.vertx().executeBlocking(promise -> {
                    // Blocking operation
                    Object result = null;
                    try {
                        result = handlerMethod.method().invoke(target, args);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    promise.complete(result);
                }, res -> {
                    if (res.succeeded()) {
                        processHandlerResult(handlerMethod.method(), ctx, res.result());
                    } else {
                        if (res.cause() instanceof RuntimeException)
                            throw (RuntimeException) res.cause();
                        else
                            throw new RuntimeException(res.cause());
                    }
                });
            } else {
                Object result = handlerMethod.method().invoke(target, args);
                processHandlerResult(handlerMethod.method(), ctx, result);
            }
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

        private MethodResult getMethod(Annotation annotation,
                                              MultiMap parameters,
                                              MultiMap formAttributes) {
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
                        } else if (parameter.getAnnotation(PathParam.class) != null) {
                            PathParam param = parameter.getAnnotation(PathParam.class);
                            otherParamCount++;
                            paramNames.add(param.value());
                        } else if (parameter.getAnnotation(UploadsParam.class) != null) {
                            otherParamCount++;
                            paramNames.add("uploads");
                        } else {
                            if (parameter.getName().matches("arg\\d+") && ! warnedAboutMissingParameterNames) {
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

        private Object[] methodParameterValues(RoutingContext ctx,
                                               Method method,
                                               String[] parameterNames,
                                               Class<?>[] parameterTypes,
                                               MultiMap requestParameters,
                                               Object body) {
            List<Object> result = new ArrayList<>();

            Parameter[] parameters = method.getParameters();

            for (int i = 0; i < parameterNames.length; i++) {
                Parameter parameter = parameters[i];

                if (parameter.getAnnotation(BodyParam.class) != null) {
                    result.add(convertBody(ctx.request().getHeader(CONTENT_TYPE), parameterTypes[i], body));
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

        private Object convertBody(String contentType, Class<?> parameterType, Object body) {
            if (body == null) {
                return null;
            }
            Object convertedBody = convertFrom(contentType, parameterType, body);
            return convertValue(convertedBody, parameterType);
        }

        static Object convertValue(Object value, Class<?> to) {
            if (to.isAssignableFrom(value.getClass()))
                return value; // No conversion needed

            if (to == String.class) {
                return value.toString();
            } else if (to == JsonObject.class) {
                return value instanceof JsonObject jsonObject ? jsonObject : new JsonObject(value.toString());
            } else if (to == JsonArray.class) {
                return value instanceof JsonArray jsonArray ? jsonArray : new JsonArray(value.toString());
            } else if (to == Integer.class || to == int.class) {
                return Integer.parseInt(value.toString());
            } else if (to == Long.class || to == long.class) {
                return Long.parseLong(value.toString());
            } else if (to == Double.class || to == double.class) {
                return Double.parseDouble(value.toString());
            } else if (to == Boolean.class || to == boolean.class) {
                return Boolean.parseBoolean(value.toString());
            } else if (to == Buffer.class) {
                return value instanceof Buffer buffer ? buffer : Buffer.buffer(value.toString());
            } else if (!to.isPrimitive() && !to.isArray()) {
                try {
                    return new JsonMapper().readValue(value.toString(), to);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to convert value to " + to.getName(), e);
                }
            }

            throw new IllegalArgumentException("Unsupported value type: " + to);
        }

        private Object convertFrom(String contentType, Class<?> type, Object value) {
            Object result = value;
            if (value != null && contentType != null)
                result = annotatedConverters.convert(result, contentType, type);
            return result;
        }

        private Object convertValue(String parameterName, Class<?> parameterType, MultiMap parameters, String defaultValue) {
            String value = parameters.get(parameterName);
            return convertValue(parameterType, value != null ? value : defaultValue);
        }

        private Object convertValue(Class<?> parameterType, String value) {
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
        private void processHandlerResult(Method method, RoutingContext ctx, Object result) {
            if (method.getReturnType() == void.class) {
                ctx.end();
            } else {
                Result handlerResult = result instanceof Result<?> ? (Result<?>) result : new Result(result);
                handlerResult.setResultClass(method.getReturnType());
                handlerResult.setAnnotations(method.getAnnotations());
                applyHttpHeaders(method, handlerResult);

                Object convertedResult = convertTo(target, handlerResult.getResult(), (String) handlerResult.getHeaders().get(CONTENT_TYPE));
                if (handlerResult.getResult() != convertedResult) {
                    handlerResult.setResult(convertedResult);
                    handlerResult.setResultClass(convertedResult.getClass());
                }

                handlerResult.handle(ctx);
            }
        }

        private Object convertTo(Object target, Object result, String contentType) {
            Object convertedResult = result;

            if (result != null && contentType != null)
                convertedResult = annotatedConverters.convert(result, result.getClass(), contentType);

            return convertedResult;
        }

        private static void applyHttpHeaders(Method method, Result<?> handlerResult) {
            HttpHeaders headers = method.getAnnotation(HttpHeaders.class);
            if (headers != null) {
                for (HttpHeader header : headers.value()) {
                    String[] headerParts = header.value().split(":");
                    if (headerParts.length == 2)
                        handlerResult.putHeader(headerParts[0].trim(), headerParts[1].trim());
                    else
                        logger.warn("Invalid header definition: " + header.value());
                }
            }

            ContentType contentType = method.getAnnotation(ContentType.class);
            if (contentType != null)
                handlerResult.putHeader(CONTENT_TYPE, contentType.value());
        }
    }


    /**
     * Interface for classes that hold and manage {@link AnnotatedConverters}.
     * Implementing classes can provide access to their converter collection,
     * allowing for centralized converter management and reuse.
     */
    public interface AnnotatedConvertersHolder {
        /**
         * Gets the AnnotatedConverters instance associated with this holder.
         *
         * @return the AnnotatedConverters instance
         */
        AnnotatedConverters getAnnotatedConverters();
    }

    /**
     * Manages annotated converter methods for content type conversions.
     * Provides caching and execution of converter methods marked with {@link ConvertsTo}
     * and {@link ConvertsFrom} annotations. Thread-safe implementation using synchronized collections.
     */
    public static class AnnotatedConverters {
        private final Map<String, Method> cache = Collections.synchronizedMap(new HashMap<>());

        /**
         * Collects and caches converter methods from the target object.
         * Scans for methods annotated with {@link ConvertsTo} and {@link ConvertsFrom},
         * storing them in the internal cache for later use.
         *
         * @param target the object to scan for converter methods
         */
        public void collectConverters(Object target) {
            Map<String, Method> result = new HashMap<>();

            for (Method method : target.getClass().getDeclaredMethods()) {
                var convertsTo = method.getAnnotation(ConvertsTo.class);
                var convertsFrom = method.getAnnotation(ConvertsFrom.class);

                if (convertsTo != null || convertsFrom != null) {
                    if (!checkMethodSignature(method))
                        continue;

                    if (convertsTo != null)
                        result.put(keyFor(convertsTo, method.getParameterTypes()[0]), method);
                    else
                        result.put(keyFor(convertsFrom, method.getReturnType()), method);
                }
            }

            cache.putAll(result);
        }

        private static boolean checkMethodSignature(Method method) {
            boolean result = Modifier.isStatic(method.getModifiers()) &&
                    Modifier.isPublic(method.getModifiers()) &&
                    method.getReturnType() != Void.class &&
                    method.getParameterCount() == 1;
            if (!result) {
                logger.warn("Method: " + method + " does not meet the requirements for a converter method. " +
                        "It must be static, public, have a single parameter, and return a non-void type.");
            }
            return result;
        }

        private String keyFor(ConvertsTo converter, Class<?> type) {
            return type + ":" + converter.value();
        }

        private String keyFor(ConvertsFrom converter, Class<?> type) {
            return converter.value() + ":" + type;
        }

        /**
         * Gets a converter method that converts from the specified class to the given content type.
         *
         * @param from source class to convert from
         * @param to   target content type to convert to
         * @return converter method if found, null otherwise
         */
        public Method getConverter(Class<?> from, String to) {
            return cache.get(keyFor(new ConvertsTo() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return ConvertsTo.class;
                }

                @Override
                public String value() {
                    return to;
                }
            }, from));
        }

        /**
         * Gets a converter method that converts from the specified content type to the given class.
         *
         * @param from source content type to convert from
         * @param to   target class to convert to
         * @return converter method if found, null otherwise
         */
        public Method getConverter(String from, Class<?> to) {
            return cache.get(keyFor(new ConvertsFrom() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return ConvertsFrom.class;
                }

                @Override
                public String value() {
                    return from;
                }
            }, to));
        }

        /**
         * Converts a value from a content type to a specified class using cached converter methods.
         *
         * @param value the value to convert
         * @param from  source content type
         * @param to    target class
         * @return converted value, or original value if no converter found
         * @throws RuntimeException if conversion fails
         */
        public Object convert(Object value, String from, Class<?> to) {
            Method method = getConverter(from, to);
            if (method != null) {
                try {
                    return method.invoke(null, RoutingContextHandler.convertValue(value, method.getParameterTypes()[0]));
                } catch (Exception e) {
                    logger.error("Error converting value using @ConvertsTo method: " + method.getName(), e);
                    throw new RuntimeException(e);
                }
            } else {
                return value;
            }
        }

        /**
         * Converts a value of a specific class to a specified content type using cached converter methods.
         *
         * @param value the value to convert
         * @param from  source class
         * @param to    target content type
         * @return converted value, or original value if no converter found
         * @throws RuntimeException if conversion fails
         */
        public Object convert(Object value, Class<?> from, String to) {
            Method method = getConverter(from, to);
            if (method != null) {
                try {
                    return method.invoke(null, value);
                } catch (Exception e) {
                    logger.error("Error converting value using @ConvertsFrom method: " + method.getName(), e);
                    throw new RuntimeException(e);
                }
            } else {
                return value;
            }
        }
    }
}
