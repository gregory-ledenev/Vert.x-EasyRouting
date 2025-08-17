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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;

import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.gl.vertx.easyrouting.Result.*;

/**
 * Represents a JSON-RPC context that encapsulates the JSON-RPC request and provides methods to handle JSON-RPC responses.
 * It extends the RpcContext class and provides specific handling for JSON-RPC version 2.0.
 * <p>
 * The context includes methods to convert various types of results into JSON format, handle errors, and create responses
 * according to the JSON-RPC specification.
 */
public class JsonRpcContext extends RpcContext {

    public static final String KEY_VERSION = "jsonrpc";
    public static final String KEY_ID = "id";
    public static final String KEY_METHOD = "method";
    public static final String KEY_PARAMS = "params";
    public static final String VERSION = "2.0";
    public static final String KEY_RESULT = "result";
    public static final String KEY_CODE = "code";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_ERROR = "error";

    /**
     * Create a new JsonRpcContext with the given JSON request object.
     * This constructor extracts the JSON-RPC request information from the provided JsonObject.
     *
     * @param body the body
     * @throws IllegalArgumentException if the JSON-RPC version is not supported or if the request is invalid
     */
    public JsonRpcContext(RequestBody body) throws RpcException {
        super(CT_APPLICATION_JSON, RpcType.JsonRpc);
        rpcRequest = getRpcRequest(body);
    }

    private RpcRequest getRpcRequest(RequestBody body) throws RpcException {
        JsonObject rpcRequestObject;
        try {
            rpcRequestObject = body.asJsonObject();
        } catch (Exception e) {
            throw new RpcException(getInvalidRequestRpcResponse(e));
        }

        String version = rpcRequestObject.getString(KEY_VERSION);
        if (version != null) {
            String id = rpcRequestObject.getString(KEY_ID);
            if (VERSION.equals(version)) {
                if (! (rpcRequestObject.getValue(KEY_PARAMS) instanceof JsonArray)) {
                    JsonObject params = rpcRequestObject.getJsonObject(KEY_PARAMS);
                    return new RpcRequest(
                            id,
                            rpcRequestObject.getString(KEY_METHOD),
                            params != null ? params.getMap() : Collections.emptyMap());
                } else {
                    throw new RpcException(getSequentialParametersNotSupportedRpcResponse(id));
                }
            } else {
                throw new RpcException(getInvalidPayloadRpcResponse(version, id));
            }
        } else {
            return null;
        }
    }

    static class JsonRpcResponse implements RpcResponse {
        private JsonObject result;

        public JsonRpcResponse(JsonObject result) {
            this.result = result;
        }

        public JsonRpcResponse() {
        }

        @Override
        public void handle(RoutingContext ctx) {
            if (result != null) {
                ctx.response().putHeader(CONTENT_TYPE, CT_APPLICATION_JSON);
                ctx.response().setStatusCode(200);
                ctx.response().end(result.encode());
            } else {
                ctx.response().setStatusCode(200).end(); // Notification, no need to return anything
            }
        }

        @Override
        public String toString() {
            return result.toString();
        }
    }

    @Override
    public RpcResponse getRpcResponse(Object result) {
        if (rpcRequest.getId() != null) {
            JsonObject response = new JsonObject();
            response.put(KEY_VERSION, VERSION);
            response.put(KEY_ID, rpcRequest.getId());
            response.put(KEY_RESULT, convert(result));
            return new JsonRpcResponse(response);
        }

        return new JsonRpcResponse(); // Notification (no ID)
    }

    /**
     * Creates a JSON-RPC response indicating that the method was not found.
     * This response includes an error object with code -32601 and a message indicating the method name.
     *
     * @return RpcResponse representing the "method not found" error
     */
    public RpcResponse getNoMethodRpcResponse() {
        return getErrorRpcResponse(-32601,
                "Method not found: " + rpcRequest.getMethodName(),
                rpcRequest.getId());
    }

    /**
     * Creates a JSON-RPC response indicating that sequential parameters are not supported.
     * This response includes an error object with code -32602 and a message advising to use named parameters instead.
     *
     * @return RpcResponse representing the "sequential parameters not supported" error
     */
    public RpcResponse getSequentialParametersNotSupportedRpcResponse(String id) {
        return getErrorRpcResponse(-32602,
                "Sequential parameters are not supported. Use named parameters instead.",
                id);
    }

    public RpcResponse getInvalidRequestRpcResponse(Exception e) {
        return getErrorRpcResponse(-32600,
                "Invalid request. " + e.getMessage(),
                "");
    }

    /**
     * Creates a JSON-RPC response indicating that the payload is invalid or unsupported.
     * This response includes an error object with code -32600 and a message indicating the unsupported version.
     *
     * @param version The version of the JSON-RPC payload that is invalid or unsupported
     * @return RpcResponse representing the "invalid or unsupported payload" error
     */
    public RpcResponse getInvalidPayloadRpcResponse(String version, String id) {
        return getErrorRpcResponse(-32600,
                MessageFormat.format("Invalid or unsupported JSON-RPC payload: {0}. Only 2.0 is supported.", version),
                id);
    }

    /**
     * Creates a JSON-RPC response for an error that occurred during method invocation.
     * This response includes an error object with code -32000 and a message indicating the method name and the exception message.
     *
     * @param e The exception that occurred during method invocation
     * @return RpcResponse representing the error response
     */
    public RpcResponse getErrorMethodInvocationRpcResponse(Exception e) {
        String message = e.getMessage();
        if (e instanceof InvocationTargetException ie)
            message = ie.getTargetException().getMessage();

        return getErrorRpcResponse(-32000, message, rpcRequest.getId());
    }

    public RpcResponse getErrorRpcResponse(int code, String message, String id) {
        JsonObject response = new JsonObject();
        response.put(KEY_VERSION, VERSION);
        response.put(KEY_ID, id);

        JsonObject error = new JsonObject();
        error.put(KEY_CODE, code);
        error.put(KEY_MESSAGE, message);
        response.put(KEY_ERROR, error);

        return new JsonRpcResponse(response);
    }

    private Object convert(Object result) {
        if (result == null)
            return result;

        if (result instanceof Buffer buffer) {
            return Base64.getEncoder().encodeToString(buffer.getBytes());
        } else if (result instanceof JsonObject jsonObject) {
            return jsonObject;
        } else if (result instanceof JsonArray jsonArray) {
            return jsonArray;
        } else if (result instanceof Map<?, ?> map) {
            JsonObject jsonObject = new JsonObject();
            map.forEach((key, value) -> jsonObject.put(key.toString(), value));
            return jsonObject;
        } else if (result instanceof Collection<?> collection) {
            JsonArray jsonArray = new JsonArray();
            collection.forEach(jsonArray::add);
            return jsonArray;
        } else if (result != null && result.getClass().isArray()) {
            JsonArray jsonArray = new JsonArray();
            int length = java.lang.reflect.Array.getLength(result);
            for (int i = 0; i < length; i++) {
                jsonArray.add(java.lang.reflect.Array.get(result, i));
            }
            return jsonArray;
        } else if (result instanceof String ||
                result instanceof Number ||
                result instanceof Boolean ||
                result instanceof Character ||
                result.getClass().isPrimitive()) {
            return result;
        } else {
            return JsonObject.mapFrom(result);
        }
    }
}
