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

import com.gl.vertx.easyrouting.annotations.Rpc;
import com.gl.vertx.easyrouting.annotations.RpcExclude;
import com.gl.vertx.easyrouting.annotations.RpcInclude;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.gl.vertx.easyrouting.Result.CONTENT_TYPE;

public abstract class RpcContext {
    protected RpcRequest rpcRequest;
    protected final String contentType;
    protected final RpcType rpcType;

    public RpcContext(RpcRequest aRpcRequest, String aContentType, RpcType rpcType) {
        this.rpcRequest = Objects.requireNonNull(aRpcRequest);
        this.contentType = Objects.requireNonNull(aContentType);
        this.rpcType = rpcType;
    }

    public RpcContext(String aContentType, RpcType rpcType) {
        this.contentType = Objects.requireNonNull(aContentType);
        this.rpcType = rpcType;
    }

    public String getContentType() {
        return contentType;
    }

    private static final String KEY_RPC_CONTEXT = "rpcContext";

    public static RpcContext getRpcContext(RoutingContext ctx) {
        return ctx.get(KEY_RPC_CONTEXT);
    }

    public static String getRpcMethodName(RoutingContext ctx) {
        RpcContext rpcContext = getRpcContext(ctx);
        if (rpcContext != null && rpcContext.getRpcRequest() != null)
            return rpcContext.getRpcRequest().getMethodName();
        return null;
    }

    public static RpcContext createRpcContext(RoutingContext ctx, RpcType rpcType) throws RpcException {
        if (Objects.requireNonNull(rpcType) == RpcType.JsonRpc && ctx.body() != null && !ctx.body().asString().isEmpty())
            return new JsonRpcContext(ctx.body());

        throw new IllegalArgumentException("Unsupported RPC type: " + rpcType);
    }
    public static void setRpcContext(RoutingContext ctx, RpcContext rpcContext) {
        ctx.response().putHeader(CONTENT_TYPE, rpcContext.getContentType());
        ctx.put(KEY_RPC_CONTEXT, rpcContext);
    }

    public RpcRequest getRpcRequest() {
        return rpcRequest;
    }

    public abstract RpcResponse getRpcResponse(Object result);
    public abstract RpcResponse getNoMethodRpcResponse();
    public abstract RpcResponse getErrorMethodInvocationRpcResponse(Throwable e);

    public static class RpcRequest {
        private final String methodName;
        private final Map<String, Object> arguments;
        private final String id;

        public RpcRequest(String aRequestID, String aMethodName, Map<String, Object> aArguments) {
            id = aRequestID;
            methodName = aMethodName;
            arguments = aArguments;
        }

        public String getMethodName() {
            return methodName;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }

        public String getId() {
            return id;
        }

        @SuppressWarnings("unchecked")
        public void populate(MultiMap requestParameters) {
            for (String paramName : arguments.keySet()) {
                Object value = arguments.get(paramName);
                if (value instanceof List<?>)
                    value = new JsonArray((List<?>) value).encode();
                else if (value instanceof Map<?, ?>)
                    value = new JsonObject((Map<String, Object>) value).encode();
                else
                    value = value.toString();
                requestParameters.add(paramName, value.toString());
            }
        }
    }

    public interface RpcResponse {
        void handle(RoutingContext ctx);
    }

    /**
     * Checks if the given method can be exported as an RPC method.
     * A method is considered exportable if it is public and not static.
     *
     * @param method the method to check
     * @return true if the method can be exported, false otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean canExportMethod(Method method) {
        int mods = method.getModifiers();
        boolean result = Modifier.isPublic(mods) && ! Modifier.isStatic(mods);

        if (result) {
            Rpc rpc = method.getDeclaringClass().getAnnotation(Rpc.class);
            if (rpc != null)
                result = rpc.exportPolicy() == RpcExportPolicy.All;
            if (method.getAnnotation(RpcExclude.class) != null)
                result = false;
            else if (method.getAnnotation(RpcInclude.class) != null)
                result = true;
        }

        return result;
    }

    public static class RpcException extends Exception {
        /**
         * Exception class representing an error that occurred during RPC processing.
         * This exception encapsulates an {@code RpcResponse} that can be used to form
         * a response can be sent back to the client.
         */
        private final RpcResponse response;

        /**
         * Constructs a new {@code RpcException} with the specified {@code RpcResponse}.
         *
         * @param response the RpcResponse to be sent back to the client
         */
        public RpcException(RpcResponse response) {
            this.response = response;
        }

        /**
         * Retrieves the {@code RpcResponse} associated with this exception.
         *
         * @return the RpcResponse to be sent back to the client
         */
        public RpcResponse getRpcResponse() {
            return response;
        }

        @Override
        public String getMessage() {
            return response.toString();
        }
    }
}
