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

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.gl.vertx.easyrouting.Result.CONTENT_TYPE;

public abstract class RpcContext {
    protected RpcRequest rpcRequest;
    protected String contentType;
    protected RpcType rpcType;

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

    public static RpcContext createRpcContext(RoutingContext ctx, RpcType rpcType) {
        if (Objects.requireNonNull(rpcType) == RpcType.JsonRpc)
            return new JsonRpcContext(ctx.body().asJsonObject());

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
    public abstract RpcResponse getErrorMethodInvocationRpcResponse(Exception e);

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

    public static interface RpcResponse {
        void handle(RoutingContext ctx);
    }
}
