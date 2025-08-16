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

package com.gl.vertx.easyrouting.annotations;

import com.gl.vertx.easyrouting.RpcExportPolicy;
import com.gl.vertx.easyrouting.RpcType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark classes that implement RPC services. The annotated class will be registered as an RPC service
 * with the specified type and path.
 * <p>
 * The default RPC type is {@link RpcType#JsonRpc} and the default path is "/", which means the service will be
 * registered at the root path.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Rpc {
    /**
     * Specifies the type of RPC service. Currently, only JSON-RPC is supported.
     *
     * @return the RPC type
     */
    RpcType rpcType() default RpcType.JsonRpc;

    /**
     * Specifies the endpoint path for the RPC service. The default path is "/".
     *
     * @return the base path for the RPC service
     */
    String path() default "/";

    /**
     * Indicates whether to allow getting the scheme for the RPC service. The default value is false, meaning the scheme
     * will not be included unless explicitly specified. If allowed, the scheme can be obtained via {@code GET} for
     * the service endpoint.
     *
     * @return true if the scheme should be included in the OpenAPI documentation, false otherwise
     */
    boolean provideScheme() default false;

    /**
     * Specifies the export policy for the RPC service. The default is {@link RpcExportPolicy#All}, meaning all
     * methods will be exported.
     *
     * @return the export policy for the RPC service
     */
    RpcExportPolicy exportPolicy() default RpcExportPolicy.All;
}
