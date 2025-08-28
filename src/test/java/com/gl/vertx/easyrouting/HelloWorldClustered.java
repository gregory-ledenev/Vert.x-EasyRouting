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
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static com.gl.vertx.easyrouting.annotations.HttpMethods.*;

public class HelloWorldClustered extends Application {
    @Blocking
    @GET("/blocking")
    public String helloBlocking(@ClusterNodeURI("node1") URI node1,
                                @ClusterNodeURI("node2") URI node2,
                                @ClusterNodeURI("mainNode") URI mainNode) {
        if (! nodeName.equals("mainNode"))
            return "Hello from node: " + nodeName;

        String message = "Hello, World! From nodes: ";

        HttpClient client = HttpClient.newHttpClient();

        try {
            if (node1 != null) {
                HttpRequest request = HttpRequest.newBuilder().uri(node1).GET().build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                message += response.body() + " ";
            }
            if (node2 != null) {
                HttpRequest request = HttpRequest.newBuilder().uri(node2).GET().build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                message += response.body() + " ";
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        return message;
    }

    @GET("/")
    public Future<String> hello(@ClusterNodeURI("node1") URI node1,
                                @ClusterNodeURI("node2") URI node2) {
        if (!nodeName.equals("mainNode")) {
            return Future.succeededFuture("Hello from node: " + nodeName);
        }

        WebClient client = WebClient.create(getVertx());
        List<Future<String>> nodeResults = new ArrayList<>();

        BiFunction<URI, String, Future<String>> callNodeWithCircuitBreaker = (nodeUri, nodeId) -> {
            CircuitBreaker circuitBreaker = getCircuitBreaker(nodeId);
            return circuitBreaker.execute(promise -> {
                        client.getAbs(nodeUri.toString())
                                .send()
                                .onSuccess(response -> promise.complete(response.bodyAsString()))
                                .onFailure(promise::fail);
                    }).map(result -> (String) result)
                    .otherwise(err -> "Node not available (" + nodeUri + "): " + err.getMessage());
        };

        if (node1 != null) {
            nodeResults.add(callNodeWithCircuitBreaker.apply(node1, "node1"));
        }
        if (node2 != null) {
            nodeResults.add(callNodeWithCircuitBreaker.apply(node2, "node2"));
        }

        if (nodeResults.isEmpty()) {
            return Future.succeededFuture("Hello, World! From nodes: No nodes provided");
        }

        return Future.all(nodeResults).compose(composite -> {
            StringBuilder message = new StringBuilder("Hello, World! From nodes: ");
            for (int i = 0; i < composite.size(); i++) {
                if (composite.succeeded(i)) {
                    message.append(composite.resultAt(i).toString()).append(" ");
                }
            }
            return Future.succeededFuture(message.toString());
        }).recover(error -> {
            return Future.succeededFuture("Error processing request: " + error.getMessage());
        });
    }

    public static void main(String[] args) {
        if (args.length > 0)
            port = Integer.parseInt(args[0]);
        if (args.length > 1)
            helloMessage = args[1];
        if (args.length > 2)
            nodeName = args[2];
        new HelloWorldClustered()
                .clustered(nodeName)
                .start(port);
    }
    private static int port = 8080;
    private static String helloMessage;
    private static String nodeName;
}
