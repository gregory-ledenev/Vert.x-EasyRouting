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

import com.gl.vertx.easyrouting.annotations.RequiredRoles;
import com.gl.vertx.easyrouting.annotations.Rpc;
import com.gl.vertx.easyrouting.annotations.RpcExclude;
import com.gl.vertx.easyrouting.annotations.RpcInclude;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.gl.vertx.easyrouting.TestApplication.JWT_TOKEN_USER;
import static com.gl.vertx.easyrouting.TestApplication.JWT_TOKEN_USER_ADMIN;
import static com.gl.vertx.easyrouting.TestApplication.TestApplicationImpl.JWT_PASSWORD;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HelloWorldJsonRpcTest {
    @SuppressWarnings("SameReturnValue")
    @Rpc(path = "/*", provideScheme = true)
    public static class HelloWorldJsonRpcApplication extends Application {
        public static void main(String[] args) {
            new HelloWorldJsonRpcApplication().start();
        }

        public String hello() {
            return "Hello, World!";
        }

        @RpcExclude
        public String bye() {
            return "Bye";
        }
    }

    @Rpc(path = "/*")
    public static class HelloWorldAuthJsonRpcApplication extends Application {
        public static void main(String[] args) {
            new HelloWorldAuthJsonRpcApplication().
                    jwtAuth(JWT_PASSWORD, "/*").
                    start();
        }

        @RequiredRoles({"admin"})
        public String hello() {
            return "Hello, World!";
        }

        @RequiredRoles({"user", "admin"})
        public String bye() {
            return "Bye";
        }
    }

    @SuppressWarnings("SameReturnValue")
    @Rpc(path = "/*", provideScheme = true, exportPolicy = RpcExportPolicy.None)
    static class HelloWorldJsonRpcApplicationNoneExportPolicy extends Application {
        public static void main(String[] args) {
            new HelloWorldJsonRpcApplicationNoneExportPolicy().start();
        }

        @RpcInclude
        public String hello() {
            return "Hello, World!";
        }

        public String bye() {
            return "Bye";
        }
    }

    @Test
    void testJsonRpc() throws Throwable {
        Application app = new HelloWorldJsonRpcApplication().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                                                      {"jsonrpc": "2.0", "method": "hello", "params": {}, "id": 2}"""))
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("""
                                                {"jsonrpc":"2.0","id":"2","result":"Hello, World!"}""", response.body());
                        System.out.println(response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testJsonRpcByeExcluded() throws Throwable {
        Application app = new HelloWorldJsonRpcApplication().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                                                      {"jsonrpc": "2.0", "method": "bye", "params": {}, "id": 2}"""))
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("""
                                                {"jsonrpc":"2.0","id":"2","error":{"code":-32601,"message":"Method not found: bye"}}""", response.body());
                        System.out.println(response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testJsonRpcNoneExportPolicyHelloIncluded() throws Throwable {
        Application app = new HelloWorldJsonRpcApplicationNoneExportPolicy().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                                                      {"jsonrpc": "2.0", "method": "hello", "params": {}, "id": 2}"""))
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("""
                                                {"jsonrpc":"2.0","id":"2","result":"Hello, World!"}""", response.body());
                        System.out.println(response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testJsonRpcNoneExportPolicyByeExcluded() throws Throwable {
        Application app = new HelloWorldJsonRpcApplicationNoneExportPolicy().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                                                      {"jsonrpc": "2.0", "method": "bye", "params": {}, "id": 2}"""))
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("""
                                               {"jsonrpc":"2.0","id":"2","error":{"code":-32601,"message":"Method not found: bye"}}""", response.body());
                        System.out.println(response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testUnauthenticated() throws Throwable {
        Application app = new HelloWorldAuthJsonRpcApplication().
                jwtAuth(JWT_PASSWORD, "/*").
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/api/"))
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                                                      {"jsonrpc": "2.0", "method": "hello", "params": {}, "id": 2}"""))
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(401, response.statusCode()); // redirect /loginForm
                        System.out.println(response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                })
                .start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testAuthorized() throws Throwable {
        Application app = new HelloWorldAuthJsonRpcApplication().
                jwtAuth(JWT_PASSWORD, "/*").
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN)
                            .uri(URI.create("http://localhost:8080/"))
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                                                      {"jsonrpc": "2.0", "method": "hello", "params": {}, "id": 2}"""))
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("""
                                     {"jsonrpc":"2.0","id":"2","result":"Hello, World!"}""", response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                })
                .start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testUnauthorizedForAdmin() throws Throwable {
        Application app = new HelloWorldAuthJsonRpcApplication().
                jwtAuth(JWT_PASSWORD, "/*").
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .header("Authorization", "Bearer " + JWT_TOKEN_USER)
                            .uri(URI.create("http://localhost:8080/"))
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                                                      {"jsonrpc": "2.0", "method": "hello", "params": {}, "id": 2}"""))
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(403, response.statusCode());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                })
                .start(8080);

        app.handleCompletionHandlerFailure();
    }

}
