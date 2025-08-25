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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
/**
 * Utility class for testing HTTP requests against an {@link Application} instance.
 */
public class TestUtils {

    /**
     * Executes a test by sending an HTTP GET request to the specified path and port, allowing customization of the
     * request and application under test.
     *
     * @param appSupplier a supplier that provides the {@link Application} instance
     * @param port        the port to send the request to
     * @param path        the path to send the request to
     * @param test        a consumer that performs actual test assertions on the {@link HttpResponse}
     * @throws Throwable if any error occurs during the test execution
     */
    public static void testGET(Supplier<Application> appSupplier, int port, String path,
                               Consumer<HttpResponse<String>> test) throws Throwable {
        testGET(appSupplier, port, path, null, test);
    }

    /**
     * Executes a test by sending an HTTP GET request to the specified path and port, allowing customization of the
     * request and application under test.
     *
     * @param appSupplier       a supplier that provides the {@link Application} instance
     * @param port              the port to send the request to
     * @param path              the path to send the request to
     * @param requestCustomizer a function to customize the {@link HttpRequest.Builder}, may be null
     * @param test              a consumer that performs actual test assertions on the {@link HttpResponse}
     * @throws Throwable if any error occurs during the test execution
     */
    public static void testGET(Supplier<Application> appSupplier, int port, String path,
                               Function<HttpRequest.Builder, HttpRequest.Builder> requestCustomizer,
                               Consumer<HttpResponse<String>> test) throws Throwable {
        Application app = appSupplier.get().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest.Builder builder = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/" + path))
                            .GET();
                    if (requestCustomizer != null)
                        builder = requestCustomizer.apply(builder);
                    HttpRequest request = builder.build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        test.accept(response);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).start(port);

        app.handleCompletionHandlerFailure();
    }

    /**
     * Executes a test by sending an HTTP GET request to the specified path and port, allowing customization of the
     * request and application under test.
     *
     * @param appSupplier       a supplier that provides the {@link Application} instance
     * @param port              the port to send the request to
     * @param path              the path to send the request to
     * @param requestCustomizer a function to customize the {@link HttpRequest.Builder}, may be null
     * @param body              a content to post
     * @param test              a consumer that performs actual test assertions on the {@link HttpResponse}
     * @throws Throwable if any error occurs during the test execution
     */
    public static void testPOST(Supplier<Application> appSupplier, int port, String path,
                                Function<HttpRequest.Builder, HttpRequest.Builder> requestCustomizer,
                                String body,
                                Consumer<HttpResponse<String>> test) throws Throwable {
        Application app = appSupplier.get().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest.Builder builder = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/" + path))
                            .POST(HttpRequest.BodyPublishers.ofString(body));
                    if (requestCustomizer != null)
                        builder = requestCustomizer.apply(builder);
                    HttpRequest request = builder.build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        test.accept(response);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).start(port);

        app.handleCompletionHandlerFailure();
    }
}