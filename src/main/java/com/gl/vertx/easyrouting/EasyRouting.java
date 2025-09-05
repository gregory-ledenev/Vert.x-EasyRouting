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
import com.gl.vertx.easyrouting.annotations.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.common.template.TemplateEngine;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.gl.vertx.easyrouting.JWTUtil.ROLES;
import static com.gl.vertx.easyrouting.Result.CONTENT_TYPE;
import static com.gl.vertx.easyrouting.Result.CT_APPLICATION_JSON;
import static com.gl.vertx.easyrouting.annotations.HttpMethods.*;

/**
 * EasyRouting provides annotation-based HTTP request handling for Vert.x web applications. It simplifies route
 * configuration by allowing developers to define routes using annotations and automatically handles parameter binding
 * and response processing.
 *
 * @version 0.9.17
 * @since 0.9.17
 */
public class EasyRouting {
    /**
     * Current version of the EasyRouting library.
     */
    public static final String VERSION = "0.9.17";
    public static final String REDIRECT = "redirect:";
    private static final Logger logger = LoggerFactory.getLogger(EasyRouting.class);
    private static final String ERROR_HANDLING_ANNOTATED_METHOD = "Error handling annotated method: {0}({1}). Error: {2}";
    private static final String KEY_RPC_REQUEST = "rpcRequest";

    /**
     * Sets up HTTP request handlers for all supported HTTP methods (GET, POST, DELETE, PUT, PATCH, ANY) based on
     * annotated methods in the target object.
     *
     * @param router the Vert.x Router instance to configure
     * @param target the object containing annotated handler methods
     */
    public static void setupController(Router router, Object target) {
        setupController(router, target, null);
    }

    /**
     * Sets up HTTP request handlers for all supported HTTP methods (GET, POST, DELETE, PUT, PATCH, ANY) based on
     * annotated methods in the target object.
     *
     * @param router             the Vert.x Router instance to configure
     * @param target             the object containing annotated handler methods
     * @param easyRoutingContext EasyRouting context used to provide template engine, circuit breakers, etc.
     */
    public static void setupController(Router router, Object target, EasyRoutingContext easyRoutingContext) {
        Objects.requireNonNull(router);
        Objects.requireNonNull(target);
        if (easyRoutingContext == null && ! (target instanceof EasyRoutingContext))
            throw new IllegalArgumentException("Either target should implement EasyRoutingContext or context must be explicitly specified");

        setupController(router, ANY.class, target, easyRoutingContext);
        setupController(router, GET.class, target, easyRoutingContext);
        setupController(router, POST.class, target, easyRoutingContext);
        setupController(router, DELETE.class, target, easyRoutingContext);
        setupController(router, PUT.class, target, easyRoutingContext);
        setupController(router, PATCH.class, target, easyRoutingContext);

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
     * Applies JWT authentication with the common "HS256" algorithm to a specified route in the given router. Sample use
     * would be as simple as:<br><br> {@code JWTUtil.applyAuth(vertx, router, "/api/*", "very long password");}
     * <br><br>
     * that applies JWT authentication to all routes starting with "/api/".
     *
     * @param vertx     the Vert.x instance
     * @param router    the router to which the route will be added
     * @param path      the path for which JWT authentication should be applied
     * @param jwtSecret the secret key used for signing JWT tokens. It can be a plain password or a string in PEM
     *                  format
     * @return the created route with JWT authentication applied
     */
    @SuppressWarnings("UnusedReturnValue")
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

        for (Method method : target.getClass().getDeclaredMethods()) {
            HandlesStatusCode statusCodeAnnotation = method.getAnnotation(HandlesStatusCode.class);
            if (statusCodeAnnotation != null && statusCodeAnnotation.value() == statusCode) {
                Annotation methodAnnotation = method.getAnnotation(GET.class);
                if (methodAnnotation != null) {
                    try {
                        result = getPathForAnnotation(methodAnnotation);
                    } catch (Exception e) {
                        LoggerFactory.getLogger(target.getClass()).error("Failed to get redirect path for method: " + methodAnnotation, e);
                        break;
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

    private static void setupRpcRequestsHandler(Router router, Object target, EasyRoutingContext easyRoutingContext) {
        Rpc rpc = target.getClass().getAnnotation(Rpc.class);
        if (rpc != null) {
            router.post(rpc.path()).handler(createHandler(rpc, target, easyRoutingContext));
        }
    }

    /**
     * Sets up HTTP request handlers for a specific HTTP method annotation type.
     *
     * @param router          the Vert.x Router instance to configure
     * @param annotationClass the HTTP method annotation class to process
     * @param target          the object containing annotated handler methods
     * @param easyRoutingContext context
     */
    private static void setupController(Router router, Class<? extends Annotation> annotationClass, Object target,
                                        EasyRoutingContext easyRoutingContext) {
        Set<String> installedHandlers = new HashSet<>();

        try {
            for (Method method : listHandlerMethods(annotationClass, target)) {
                Annotation annotation = method.getAnnotation(annotationClass);
                if (annotation != null) {
                    logger.info("Setting up method for annotation: " + annotation);
                    String path = getPathForAnnotation(annotation);
                    if (path != null) {
                        // skip already installed handlers for the same path. the annotation contains both path and method, so it is enough.
                        String installedHandlerKey = annotation.toString();
                        if (installedHandlers.contains(installedHandlerKey))
                            continue;
                        installedHandlers.add(installedHandlerKey);

                        if (annotationClass == GET.class)
                            router.get(path).handler(createHandler(annotation, target, easyRoutingContext));
                        else if (annotationClass == POST.class)
                            router.post(path).handler(createHandler(annotation, target, easyRoutingContext));
                        else if (annotationClass == DELETE.class)
                            router.delete(path).handler(createHandler(annotation, target, easyRoutingContext));
                        else if (annotationClass == PUT.class)
                            router.put(path).handler(createHandler(annotation, target, easyRoutingContext));
                        else if (annotationClass == PATCH.class)
                            router.patch(path).handler(createHandler(annotation, target, easyRoutingContext));
                        else if (annotationClass == ANY.class)
                            router.route(path).handler(createHandler(annotation, target, easyRoutingContext));
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

    private static Handler<RoutingContext> createHandler(Annotation annotation, Object target, EasyRoutingContext easyRoutingContext) {
        return new RoutingContextHandler(annotation, target, easyRoutingContext);
    }

    /**
     * Interface for classes that hold and manage {@link AnnotatedConverters}. Implementing classes can provide access
     * to their converter collection, allowing for centralized converter management and reuse.
     */
    public interface AnnotatedConvertersHolder {
        /**
         * Gets the AnnotatedConverters instance associated with this holder.
         *
         * @return the AnnotatedConverters instance
         */
        AnnotatedConverters getAnnotatedConverters();
    }

    protected static class RoutingContextHandler implements Handler<RoutingContext> {
        private static final String KEY_EXCEPTION_TO_HANDLE = "exceptionToHandle";
        private static boolean warnedAboutMissingParameterNames = false;
        private final Annotation annotation;
        private final Object target;
        private final AnnotatedConverters annotatedConverters;
        private final EasyRoutingContext easyRoutingContext;

        public RoutingContextHandler(Annotation annotation, Object target, EasyRoutingContext easyRoutingContext) {
            this.annotation = annotation;
            this.target = target;
            this.annotatedConverters = setupAnnotatedConverters(target);
            this.easyRoutingContext = easyRoutingContext;
        }

        private static void errorHandlerInvocation(Annotation annotation, Set<String> parameterNames, Throwable exception) {
            logger.error(MessageFormat.format(ERROR_HANDLING_ANNOTATED_METHOD,
                    annotation,
                    String.join(", ", parameterNames),
                    exception));
        }

        private static void decomposeJsonBody(RoutingContext ctx, Method method, MultiMap requestParameters) {
            if (CT_APPLICATION_JSON.equalsIgnoreCase(ctx.request().headers().get(CONTENT_TYPE))) {
                if (method.getAnnotation(DecomposeBody.class) != null) {
                    JsonObject jsonBody = ctx.body().asJsonObject();
                    for (Map.Entry<String, Object> entry : jsonBody) {
                        requestParameters.add(entry.getKey(), entry.getValue().toString());
                    }
                }
            }
        }

        static Object convertValue(Object value, Type to) {
            Class<?> classTo;
            Type elementType = null;

            if (to instanceof ParameterizedType parameterizedType) {
                classTo = (Class<?>) parameterizedType.getRawType();
                if (parameterizedType.getActualTypeArguments().length > 0)
                    elementType = parameterizedType.getActualTypeArguments()[0];
            } else {
                classTo = (Class<?>) to;
            }

            if (!classTo.isArray() &&
                    !classTo.isAssignableFrom(List.class) &&
                    !classTo.isAssignableFrom(Map.class) &&
                    classTo.isAssignableFrom(value.getClass()))
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
            } else if (!classTo.isPrimitive()) {
                try {
                    JsonMapper jsonMapper = new JsonMapper();
                    if (value instanceof Map || value instanceof List || value instanceof JsonObject || value instanceof JsonArray) {
                        if (elementType != null) {
                            if (value instanceof List) {
                                final Class<?> elementClass = (Class<?>) elementType;
                                return ((List<?>) value).stream()
                                        .map(v -> jsonMapper.convertValue(v, elementClass))
                                        .collect(Collectors.toList());

                            }
                        } else {
                            return jsonMapper.convertValue(value, classTo);
                        }
                    } else if (value instanceof String || value instanceof Buffer) {
                        return jsonMapper.readValue(value.toString(), classTo);
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to convert value to " + classTo.getName(), e);
                }
                return value;
            }

            throw new IllegalArgumentException("Unsupported value type: " + to);
        }

        private static HandlesException getAnnotation(Class<?> exceptionClass) {
            return new HandlesException() {

                @Override
                public Class<? extends Annotation> annotationType() {
                    return HandlesException.class;
                }

                @Override
                public Class<?> value() {
                    return exceptionClass;
                }
            };
        }

        private EasyRoutingContext getEasyRoutingContext() {
            return target instanceof EasyRoutingContext context ? context : this.easyRoutingContext;
        }

        private TemplateEngine getTemplateEngine() {
            EasyRoutingContext context = getEasyRoutingContext();
            return context != null ? context.getTemplateEngine() : null;
        }

        private ServiceDiscovery getServiceDiscovery() {
            EasyRoutingContext context = getEasyRoutingContext();
            return context != null ? context.getServiceDiscovery() : null;
        }

        private Record getPublishedRecord() {
            EasyRoutingContext context = getEasyRoutingContext();
            return context != null ? context.getPublishedRecord() : null;
        }

        private void obtainRpcContext(RoutingContext ctx, Object target) throws RpcContext.RpcException {
            if (annotation != null)
                return;

            RpcContext result = RpcContext.getRpcContext(ctx);

            if (result == null) {
                Rpc annotation = target.getClass().getAnnotation(Rpc.class);
                if (annotation != null) {
                    result = RpcContext.createRpcContext(ctx, annotation.rpcType());
                    RpcContext.setRpcContext(ctx, result);
                }
            }
        }

        private AnnotatedConverters setupAnnotatedConverters(Object target) {
            final AnnotatedConverters annotatedConverters;
            if (target instanceof AnnotatedConvertersHolder annotatedConvertersHolder) {
                annotatedConverters = annotatedConvertersHolder.getAnnotatedConverters();
            } else {
                annotatedConverters = new AnnotatedConverters();
                annotatedConverters.collectConverters(target);
            }
            return annotatedConverters;
        }

        public void handle(RoutingContext ctx) {
            handle(ctx, annotation, false);
        }

        public boolean handle(RoutingContext ctx, Annotation anAnnotation, boolean ignoreMissingMethod) {
            boolean result = false;
            try {
                obtainRpcContext(ctx, target);

                MethodResult methodResult = getMethod(ctx, anAnnotation);
                if (methodResult == null) {
                    if (! ignoreMissingMethod) {
                        logger.error("No handler method for: \"" + anAnnotation + "\" and parameters: " + ctx.request().params().names());
                        RpcContext rpcContext = RpcContext.getRpcContext(ctx);
                        if (rpcContext != null) {
                            rpcContext.getNoMethodRpcResponse().handle(ctx);
                        } else {
                            ctx.response().setStatusCode(404).end();
                        }
                    }
                } else if (checkRequiredRoles(ctx, methodResult.method)) {
                    result = true;
                    if (methodResult.hasBodyParam) {
                        Buffer bodyBuffer = ctx.body().buffer();
                        try {
                            Object[] args = methodParameterValues(ctx, methodResult.method(), methodResult.parameterNames, methodResult.method().getGenericParameterTypes(), ctx.request().params(), bodyBuffer);
                            invokeHandlerMethod(ctx, methodResult, args);
                        } catch (Exception e) {
                            ctx.response()
                                    .setStatusCode(500)
                                    .end("Internal Server Error");
                            LoggerFactory.getLogger(target.getClass()).
                                    error("Error processing request body", e);
                        }
                    } else {
                        Object[] args = methodParameterValues(ctx, methodResult.method(), methodResult.parameterNames, methodResult.method().getGenericParameterTypes(), ctx.request().params(), null);
                        invokeHandlerMethod(ctx, methodResult, args);
                    }
                } else {
                    throw new HttpException(403, "Access denied"); // exception to let failure handler handle it
                }
            } catch (Exception ex) {
                handleExceptions(ctx, ex);
            }

            return result;
        }

        private boolean isParametersAnnotationPresent(Method method, Class<? extends Annotation> annotation) {
            for (Parameter parameter : method.getParameters()) {
                if (parameter.getAnnotation(annotation) != null)
                    return true;
            }
            return false;
        }

        private void handleExceptions(RoutingContext ctx, Throwable ex) {
            if (ex instanceof RpcContext.RpcException rpcException) {
                rpcException.getRpcResponse().handle(ctx);
                logger.error(ex.getMessage(), ex);
            } else if (ex instanceof HttpException httpException) {
                errorHandlerInvocation(annotation, ctx.request().params().names(), ex);
                throw httpException;
            } else {
                if (! handleException(ctx, ex)) {
                    RpcContext rpcContext = RpcContext.getRpcContext(ctx);
                    if (rpcContext != null) {
                        rpcContext.getErrorMethodInvocationRpcResponse(ex).handle(ctx);
                    } else {
                        errorHandlerInvocation(annotation, ctx.request().params().names(), ex);
                        ctx.fail(500, ex);
                    }
                }
            }
        }

        private boolean handleException(RoutingContext ctx, Throwable ex) {
            if (getExceptionToHandle(ctx) != null)
                return false;
            Throwable exceptionToHandle = ex instanceof InvocationTargetException itEx ? itEx.getTargetException() : ex;
            setExceptionToHandle(ctx, exceptionToHandle);
            Class<?> exceptionClass = exceptionToHandle.getClass();
            while (exceptionClass != null) {
                if (handle(ctx, getAnnotation(exceptionClass), true))
                    return true;
                exceptionClass = exceptionClass.getSuperclass();
            }
            return false;
        }

        private void setExceptionToHandle(RoutingContext ctx, Throwable ex) {
            ctx.put(KEY_EXCEPTION_TO_HANDLE, ex);
        }

        private Throwable getExceptionToHandle(RoutingContext ctx) {
            return ctx.get(KEY_EXCEPTION_TO_HANDLE);
        }

        private void invokeHandlerMethod(RoutingContext ctx, MethodResult handlerMethod, Object[] args) {
            try {
                boolean needFetchArguments = getServiceDiscovery() != null &&
                        isParametersAnnotationPresent(handlerMethod.method(), NodeURI.class);
                if (isBlockingAnnotationPresent(handlerMethod) && !needFetchArguments) {
                    invokeHandlerMethodBlocking(ctx, handlerMethod, args);
                } else if (needFetchArguments) {
                    invokeHandlerMethodFetchArguments(ctx, handlerMethod, args);
                } else {
                    invokeHandlerMethodNonBlocking(ctx, handlerMethod, args);
                }
            } catch (Exception e) {
                RpcContext rpcContext = RpcContext.getRpcContext(ctx);
                if (rpcContext != null) {
                    rpcContext.getErrorMethodInvocationRpcResponse(e).handle(ctx);
                } else {
                    throw new RuntimeException(e);
                }
            }
        }

        private void invokeHandlerMethodFetchArguments(RoutingContext ctx, MethodResult handlerMethod, Object[] args) {
            List<Future<Record>> futures = new ArrayList<>();
            int i = 0;
            for (Parameter parameter : handlerMethod.method().getParameters()) {
                NodeURI annotation = parameter.getAnnotation(NodeURI.class);
                ServiceDiscovery serviceDiscovery = getServiceDiscovery();
                if (annotation != null && serviceDiscovery != null) {
                    int finalI = i;
                    futures.add(serviceDiscovery.getRecord(new JsonObject().put("name", annotation.value())).onComplete((record, ex) -> {
                        if (ex == null && record != null) {
                            // disallow getting 'this' record to avoid circular processing
                            Record publishedRecord = getPublishedRecord();
                            if (!record.getName().equals(publishedRecord != null ? publishedRecord.getName() : null)) {
                                JsonObject location = record.getLocation();
                                args[finalI] = URI.create(location.getString("endpoint"));
                            }
                        } else {
                            logger.error("Failed to get cluster node endpoint for: " + annotation.value(), ex);
                        }
                    }));
                }
                i++;
            }

            Future.all(futures).onComplete((aCompositeFuture, aThrowable) -> {
                try {
                    if (isBlockingAnnotationPresent(handlerMethod)) {
                        invokeHandlerMethodBlocking(ctx, handlerMethod, args);
                    } else {
                        invokeHandlerMethodNonBlocking(ctx, handlerMethod, args);
                    }
                } catch (Exception e) {
                    handleExceptions(ctx, e);
                }
            });
        }

        private static boolean isBlockingAnnotationPresent(MethodResult handlerMethod) {
            Method method = handlerMethod.method();
            if (method.isAnnotationPresent(Blocking.class))
                return true;

            for (Annotation annotation : method.getAnnotations())
                if (annotation.annotationType().isAnnotationPresent(Blocking.class))
                    return true;

            return false;
        }

        private void invokeHandlerMethodNonBlocking(RoutingContext ctx, MethodResult handlerMethod, Object[] args) throws IllegalAccessException, InvocationTargetException {
            Object result = handlerMethod.method().invoke(target, args);
            if (!ctx.response().ended())
                processHandlerResult(handlerMethod.method(), ctx, result);
        }

        private void invokeHandlerMethodBlocking(RoutingContext ctx, MethodResult handlerMethod, Object[] args) {
            Future<Object> future = ctx.vertx().executeBlocking(() -> {
                Retry retry = handlerMethod.method().getAnnotation(Retry.class);

                Set<Class<?>> excludeExceptions = retry != null ?
                        Set.of(retry.excludeExceptions()) : Collections.emptySet();
                int tryCount = retry != null ? retry.maxRetries() : 1;
                long delay = retry != null ? retry.delay() : 0;

                for (int i = 0; i < tryCount; i++) {
                    try {
                        return handlerMethod.method().invoke(target, args);
                    } catch (Exception ex) {
                        Class<?> exceptionClass = ex instanceof InvocationTargetException ite ?
                                ite.getTargetException().getClass() : ex.getClass();
                        if (excludeExceptions.contains(exceptionClass) ||  i >= tryCount - 1) {
                            handleExceptions(ctx, ex);
                            return null;
                        } else {
                            logger.error("Caught an exception, retrying...", ex);
                        }
                    }
                    if (i < tryCount - 1 && delay > 0) {
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                return null;
            });
            future.onComplete((result, e) -> {
                if (e == null) {
                    processHandlerResult(handlerMethod.method(), ctx, result);
                } else {
                    handleExceptions(ctx, e);
                }
            });
        }

        private MethodResult getMethod(RoutingContext ctx, Annotation annotation) {
            Method[] declaredMethods = target.getClass().getDeclaredMethods();

            MultiMap lowercaseParams = MultiMap.caseInsensitiveMultiMap();
            lowercaseParams.addAll(ctx.request().params());

            MultiMap formAttributes = ctx.request().method() == HttpMethod.POST ? ctx.request().formAttributes() : null;
            MultiMap lowercaseFormAttributes = formAttributes != null ? MultiMap.caseInsensitiveMultiMap() : null;
            if (lowercaseFormAttributes != null)
                lowercaseFormAttributes.addAll(formAttributes);

            String methodName = null;
            RpcContext rpcContext = RpcContext.getRpcContext(ctx);
            if (rpcContext != null) {
                methodName = rpcContext.rpcRequest.getMethodName();
                for (Map.Entry<String, Object> entry : rpcContext.rpcRequest.getArguments().entrySet())
                    lowercaseParams.add(entry.getKey(), ""); // put empty value just to populate parameter name
            }

            for (Method method : declaredMethods) {
                if (methodName != null && (!RpcContext.canExportMethod(method) || !method.getName().equals(methodName)))
                    continue;

                Annotation methodAnnotation = annotation != null ? method.getAnnotation(annotation.annotationType()) : null;
                if (annotation == null || (methodAnnotation != null && methodAnnotation.equals(annotation))) {
                    MethodResult methodResult = getMethod(ctx, method, lowercaseParams, lowercaseFormAttributes);
                    if (methodResult != null)
                        return methodResult;
                }
            }

            return null;
        }

        private MethodResult getMethod(RoutingContext ctx, Method method, MultiMap params, MultiMap formAttributes) {
            List<String> paramNames = new ArrayList<>();
            int matchedParamCount = 0;
            int optionalParamCount = 0;
            int otherParamCount = 0;
            boolean hasBodyParam = false;
            boolean isFormHandler = method.getAnnotation(Form.class) != null;

            decomposeJsonBody(ctx, method, params);

            for (Parameter parameter : method.getParameters()) {

                if (parameter.getAnnotation(Param.class) != null) {
                    Param param = parameter.getAnnotation(Param.class);
                    paramNames.add(param.value());
                    if (param.defaultValue().equals(Param.UNSPECIFIED)) {
                        String lowerCaseParam = param.value().toLowerCase();
                        if (params.get(lowerCaseParam) != null)
                            matchedParamCount++;
                        else if (isFormHandler && formAttributes != null && formAttributes.get(lowerCaseParam) != null)
                            otherParamCount++;
                    } else {
                        matchedParamCount++;
                        String lowerCaseParam = param.value().toLowerCase();
                        if (params.get(lowerCaseParam) == null)
                            optionalParamCount++;
                        else if (isFormHandler && formAttributes != null && formAttributes.get(lowerCaseParam) != null)
                            otherParamCount++;
                    }
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
                } else if (parameter.getAnnotation(ContextParam.class) != null) {
                    ContextParam param = parameter.getAnnotation(ContextParam.class);
                    otherParamCount++;
                    paramNames.add(param.value());
                } else if (parameter.getAnnotation(CookieParam.class) != null) {
                    otherParamCount++;
                    paramNames.add(parameter.getName());
                } else if (parameter.getAnnotation(HeaderParam.class) != null) {
                    otherParamCount++;
                    paramNames.add(parameter.getName());
                } else if (parameter.getAnnotation(TemplateModelParam.class) != null || parameter.getType().equals(TemplateModel.class)) {
                    otherParamCount++;
                    paramNames.add(parameter.getName());
                } else if (parameter.getAnnotation(NodeURI.class) != null) {
                    otherParamCount++;
                    paramNames.add(parameter.getName());
                } else {
                    if (parameter.getName().matches("arg\\d+") && !warnedAboutMissingParameterNames) {
                        warnedAboutMissingParameterNames = true;
                        logger.warn("Parameter names are missing. Either use @Param annotations or compile project with -parameters option");
                    }
                    paramNames.add(parameter.getName());
                    String lowerCaseParam = parameter.getName().toLowerCase();
                    if (params.get(lowerCaseParam) != null)
                        matchedParamCount++;
                    else if (isFormHandler && formAttributes != null && formAttributes.get(lowerCaseParam) != null)
                        otherParamCount++;
                    else if (parameter.getType().equals(RoutingContext.class))
                        otherParamCount++;
                    else if (Throwable.class.isAssignableFrom(parameter.getType()))
                        otherParamCount++;
                }
            }

            int totalParamCount = matchedParamCount + otherParamCount;
            if (method.getParameterCount() == totalParamCount && matchedParamCount == params.size() + optionalParamCount) {
                return new MethodResult(method, paramNames.toArray(new String[0]), hasBodyParam);
            }
            return null;
        }

        private Object[] methodParameterValues(RoutingContext ctx,
                                               Method method,
                                               String[] parameterNames,
                                               Type[] parameterTypes,
                                               MultiMap requestParameters,
                                               Object body) {
            if (parameterTypes.length == 1 && parameterTypes[0].equals(RoutingContext.class)) {
                // If the method has only one parameter of type RoutingContext, return it directly
                return new Object[]{ctx};
            }

            List<Object> result = new ArrayList<>();

            decomposeJsonBody(ctx, method, requestParameters);

            RpcContext rpcContext = RpcContext.getRpcContext(ctx);

            Parameter[] parameters = method.getParameters();

            for (int i = 0; i < parameterNames.length; i++) {
                Parameter parameter = parameters[i];

                if (parameter.getAnnotation(BodyParam.class) != null) {
                    result.add(convertBody(ctx.request().getHeader(CONTENT_TYPE), parameterTypes[i], body));
                } else if (parameter.getAnnotation(UploadsParam.class) != null) {
                    result.add(ctx.fileUploads());
                } else if (parameter.getAnnotation(PathParam.class) != null) {
                    result.add(ctx.normalizedPath());
                } else if (parameter.getAnnotation(CookieParam.class) != null) {
                    Cookie cookie = ctx.request().getCookie(parameter.getAnnotation(CookieParam.class).value());
                    result.add(cookie != null ? cookie.getValue() : null);
                } else if (parameter.getAnnotation(HeaderParam.class) != null) {
                    result.add(ctx.request().getHeader(parameter.getAnnotation(HeaderParam.class).value()));
                } else if (parameter.getAnnotation(TemplateModelParam.class) != null || parameter.getType().equals(TemplateModel.class)) {
                    result.add(new TemplateModel(ctx));
                } else if (parameter.getAnnotation(NodeURI.class) != null) {
                    result.add(null); // add null as placeholder; we will get actual values later
                } else if (parameter.getType().equals(RoutingContext.class) || parameter.getAnnotation(ContextParam.class) != null) {
                    result.add(ctx);
                } else if (Throwable.class.isAssignableFrom(parameter.getType())) {
                    result.add(getExceptionToHandle(ctx));
                    setExceptionToHandle(ctx, null);
                } else {
                    Param param = parameter.getAnnotation(Param.class);
                    result.add(convertValue(rpcContext,
                            parameterNames[i],
                            parameterTypes[i],
                            requestParameters,
                            param != null && !param.defaultValue().equals(Param.UNSPECIFIED) ? param.defaultValue() : null));
                }
            }

            return result.toArray(new Object[0]);
        }

        private Object convertBody(String contentType, Type parameterType, Object body) {
            if (body == null) {
                return null;
            }
            Object convertedBody = convertFrom(contentType, parameterType, body);
            return convertValue(convertedBody, parameterType);
        }

        private Object convertFrom(String contentType, Type type, Object value) {
            Object result = value;
            if (value != null && contentType != null)
                result = annotatedConverters.convert(result, contentType, type);
            return result;
        }

        private Object convertValue(RpcContext rpcContext, String parameterName, Type parameterType, MultiMap parameters, String defaultValue) {
            Object value = rpcContext != null ? rpcContext.getRpcRequest().getArguments().get(parameterName) : null;
            if (value == null)
                value = parameters.get(parameterName);
            return convertValue(value != null ? value : defaultValue, parameterType);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void processHandlerResult(Method method, RoutingContext ctx, Object result) {
            try {
                if (result instanceof Future future) {
                    future.onComplete((futureResult, ex) -> {
                        if (ex == null) {
                            processHandlerResultNotFuture(method, ctx, futureResult);
                        } else {
                            handleExceptions(ctx, ex);
                        }
                    });
                } else {
                    processHandlerResultNotFuture(method, ctx, result);
                }
            } catch (Exception ex) {
                handleExceptions(ctx, ex);
            }
        }

        @SuppressWarnings("unchecked")
        private void processHandlerResultNotFuture(Method method, RoutingContext ctx, Object result) {
            if (ctx.response().ended())
                return;

            Result<Object> handlerResult = result instanceof Result<?> ? (Result<Object>) result : new Result<>(result);
            handlerResult.setup(target instanceof EasyRoutingContext ? (EasyRoutingContext) target : null, method);

            Object convertedResult = convertTo(target, handlerResult.getResult(), method.getGenericReturnType(), (String) handlerResult.getHeaders().get(CONTENT_TYPE));
            if (handlerResult.getResult() != convertedResult) {
                handlerResult.setResult(convertedResult);
                handlerResult.setResultClass(convertedResult.getClass());
            }

            handlerResult.handle(ctx);
        }

        private Object convertTo(Object target, Object result, Type type, String contentType) {
            Object convertedResult = result;

            if (result != null && contentType != null)
                convertedResult = annotatedConverters.convert(result, type, contentType);

            return convertedResult;
        }

        record MethodResult(Method method, String[] parameterNames, boolean hasBodyParam) {
        }
    }

    /**
     * Manages annotated converter methods for content type conversions. Provides caching and execution of converter
     * methods marked with {@link ConvertsTo} and {@link ConvertsFrom} annotations. Thread-safe implementation using
     * synchronized collections.
     * <p>
     * This class maintains a thread-safe cache of converter methods and provides functionality to:
     * <ul>
     *   <li>Collect and register converter methods from target objects</li>
     *   <li>Convert values between different content types using registered converters</li>
     *   <li>Manage converter method lookup and execution</li>
     * </ul>
     */
    public static class AnnotatedConverters {
        public static final String KEY_DELIMITER_TO = "->";
        public static final String KEY_DELIMITER_FROM = "<-";

        private final Map<String, Method> convertersCache = Collections.synchronizedMap(new HashMap<>());

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

        private static String keyToString(String key, boolean shortenClassNames) {
            if (!shortenClassNames) return key;

            String keyDelimiter = KEY_DELIMITER_TO;

            String[] parts = key.split(keyDelimiter, 2);
            if (parts.length != 2) {
                keyDelimiter = KEY_DELIMITER_FROM;
                parts = key.split(keyDelimiter, 2);
            }

            return MessageFormat.format("{0}{1}{2}", parts[0], keyDelimiter, parts[1].replaceAll("^.*[.$]", ""));
        }

        private static String methodToString(Method method, boolean shortenClassNames) {
            return MessageFormat.format("{0} {1}.{2}({3})",
                    shortenClassNames ? method.getReturnType().getSimpleName() : method.getReturnType().getName(),
                    shortenClassNames ? method.getDeclaringClass().getSimpleName() : method.getDeclaringClass().getName(),
                    method.getName(),
                    Arrays.stream(method.getParameterTypes()).
                            map(c -> shortenClassNames ? c.getSimpleName() : c.getName()).
                            collect(Collectors.joining(",")));
        }

        /**
         * Collects and caches converter methods from the target object. Scans for methods annotated with
         * {@link ConvertsTo} and {@link ConvertsFrom}, storing them in the internal cache for later use.
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

            convertersCache.putAll(result);
        }

        private String keyFor(ConvertsTo converter, Class<?> type) {
            return MessageFormat.format("\"{0}\"{1}{2}", converter.value(), KEY_DELIMITER_TO, type.getName());
        }

        private String keyFor(ConvertsFrom converter, Class<?> type) {
            return MessageFormat.format("\"{0}\"{1}{2}", converter.value(), KEY_DELIMITER_FROM, type.getName());
        }

        /**
         * Returns a string representation of all registered converters. Each line contains a converter entry in the
         * format "sourceType->targetType=methodSignature". Method signatures include return type, method name, and
         * parameter types.
         *
         * @return a string containing all registered converters, sorted by converter key, one per line
         */
        @Override
        public String toString() {
            return toString(false);
        }

        /**
         * Returns a string representation of all registered converters. Each line contains a converter entry in the
         * format "sourceType->targetType=methodSignature". Method signatures include return type, method name, and
         * parameter types.
         *
         * @param shortenClassNames if true, uses simple class names instead of fully qualified names
         * @return a string containing all registered converters, sorted by converter key, one per line
         */
        public String toString(boolean shortenClassNames) {
            return "{\n" +
                    convertersCache.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(e -> "  " +
                                    keyToString(e.getKey(), shortenClassNames) +
                                    " = " +
                                    methodToString(e.getValue(), shortenClassNames))
                            .collect(Collectors.joining("\n")) +
                    "\n}";
        }

        /**
         * Gets a converter method that converts from the specified class to the given content type.
         *
         * @param from source class to convert from
         * @param to   target content type to convert to
         * @return converter method if found, null otherwise
         */
        public Method getConverter(Class<?> from, String to) {
            return convertersCache.get(keyFor(new ConvertsTo() {
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
            return convertersCache.get(keyFor(new ConvertsFrom() {
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
        public Object convert(Object value, String from, Type to) {
            Class<?> classTo;
            Class<?> elementType = null;

            if (to instanceof ParameterizedType parameterizedType) {
                classTo = (Class<?>) parameterizedType.getRawType();
                // use array converter
                if (classTo.isAssignableFrom(List.class) && parameterizedType.getActualTypeArguments().length > 0) {
                    elementType = (Class<?>) parameterizedType.getActualTypeArguments()[0];
                    classTo = Array.newInstance(elementType, 0).getClass();
                }
            } else {
                classTo = (Class<?>) to;
            }

            Method method = getConverter(from, classTo);
            if (method != null) {
                try {
                    Object result = method.invoke(null, RoutingContextHandler.convertValue(value, method.getParameterTypes()[0]));
                    return result.getClass().isArray() && elementType != null ?
                            Arrays.asList((Object[]) result) :
                            result;
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
        public Object convert(Object value, Type from, String to) {
            Class<?> classFrom;
            Class<?> elementType = null;
            Object localValue = value;

            if (from instanceof ParameterizedType parameterizedType) {
                classFrom = (Class<?>) parameterizedType.getRawType();
                // use array converter
                if (classFrom.isAssignableFrom(List.class) && parameterizedType.getActualTypeArguments().length > 0) {
                    elementType = (Class<?>) parameterizedType.getActualTypeArguments()[0];
                    classFrom = Array.newInstance(elementType, 0).getClass();
                    List<?> list = (List<?>) value;
                    localValue = Array.newInstance(elementType, list.size());
                    localValue = (Object[]) list.toArray((Object[]) localValue);
                }
            } else {
                classFrom = (Class<?>) from;
            }

            Method method = getConverter(classFrom, to);
            if (method != null) {
                try {
                    Object result = method.invoke(null, localValue);
                    return result.getClass().isArray() && elementType != null ?
                            Arrays.asList((Object[]) result) :
                            result;
                } catch (Exception e) {
                    logger.error("Error converting value using @ConvertsFrom method: " + method.getName(), e);
                    throw new RuntimeException(e);
                }
            } else {
                return localValue;
            }
        }
    }
}
