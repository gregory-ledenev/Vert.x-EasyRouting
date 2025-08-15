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

import io.vertx.ext.web.RoutingContext;

import static com.gl.vertx.easyrouting.EasyRouting.*;
/**
 * Base class for JSON-RPC application modules that handle JSON-RPC requests. This class extends the ApplicationModule
 * and provides a method to handle JSON-RPC requests by delegating to the methods defined in the module. You should:
 * <ul>
 *  <li>Subclass the {@code JsonRpcApplicationModule}</li>
 *  <li>Annotate the class witjh {@code @JsonRpc} to indicate that it is a JSON-RPC module.</li>
 *  <li>Override the {@code handleJsonRpcRequest()} method to provide a {@code @POST} annotation with the endpoint path</li>
 *  <li>Implement all required methods that can be accessible via JSON-RPC.</li>
 * </ul>
 * <pre>
 *     {@code
 *     @JsonRpc
 *     public class JsonRpcTestApplicationModule
 *         extends JsonRpcApplicationModule<TestApplicationImpl> {
 *
 *         @POST("/api/jsonrpc/test/*")
 *         public void handleJsonRpcRequest(RoutingContext ctx) {
 *             super.handleJsonRpcRequest(ctx);
 *         }
 *
 *         public int multiply(@Param("a") int a , @Param("b") int b) {
 *             return a * b;
 *         }
 *     }
 *    }</pre>
 * @param <T> The type of Application this module works with
 */
@JsonRpc
public abstract class JsonRpcApplicationModule<T extends Application> extends ApplicationModule<T> {
    private RoutingContextHandler routingContextHandler;

    /**
     * Constructor for JsonRpcApplicationModule. Initializes the module with the specified protected routes.
     *
     * @param protectedRoutes Array of route patterns that require authentication
     */
    public JsonRpcApplicationModule(String... protectedRoutes) {
        super(protectedRoutes);
    }

    @Override
    void setApplication(Application application) {
        super.setApplication(application);
        if (application != null)
            routingContextHandler = new RoutingContextHandler(null, this);
        else
            routingContextHandler = null;
    }

    /**
     * Handles JSON-RPC requests by delegating to the methods defined in the module. Subclasses must ovverride this
     * method to provide @POST annotation with the endpoint path
     *
     * @param ctx The RoutingContext containing the request and response
     */
    public void handleJsonRpcRequest(RoutingContext ctx) {
        routingContextHandler.handle(ctx, (name, parameterClasses) ->
                !(name.equals("handleJsonRpcRequest") && parameterClasses.length == 1 && parameterClasses[0] == RoutingContext.class));
    }
}
