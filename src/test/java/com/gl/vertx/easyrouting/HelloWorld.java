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

import com.gl.vertx.easyrouting.annotations.Blocking;
import com.gl.vertx.easyrouting.annotations.ClusterNodeURI;
import com.gl.vertx.easyrouting.annotations.Template;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.servicediscovery.types.HttpEndpoint;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static com.gl.vertx.easyrouting.TestApplication.JWT_TOKEN_USER_ADMIN;
import static com.gl.vertx.easyrouting.TestApplication.TestApplicationImpl.JWT_PASSWORD;
import static com.gl.vertx.easyrouting.UserAdminApplication.JWT_SECRET;
import static com.gl.vertx.easyrouting.annotations.HttpMethods.*;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class HelloWorld extends Application {
    @GET("/hello")
    public String hello(@ClusterNodeURI("node1") URI uri1, @ClusterNodeURI("node2") URI uri2) {
        // add here your code that calls microservices
        return "Hello Clustering and Microservices";
    }

    public static void main(String[] args) {
        new HelloWorld()
                .clustered("mainNode")
                .start();
    }
}

class HelloWorldAppTest {
    @Test
    void testHelloWorld() throws Throwable {
        Application app = new HelloWorld()
                .jwtAuth(JWT_PASSWORD, "/*")
                .templateEngine(TemplateEngineFactory.Type.Thymeleaf, null)
                .onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN)
                            .uri(URI.create("http://localhost:8080/"))
                            .GET()
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("Hello, World!", response.body());
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
}
