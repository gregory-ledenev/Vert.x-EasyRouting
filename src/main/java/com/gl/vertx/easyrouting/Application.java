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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * An application class that allows building minimal applications for prototyping and testing purposes. The application
 * can be made in a few lines:
 * <pre>
 * {@code
 *     class HelloWorld extends Application {
 *         @GET("/*")
 *         String hello() {
 *             return "Hello World!";
 *         }
 *
 *         public static void main(String[] args) {
 *             new HelloWorld().start();
 *         }
 *     }
 * }</pre>
 */
public class Application extends AbstractVerticle {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private int port;
    private boolean readInput;
    private Consumer<Application> completionHandler;
    private final String jwtSecret;
    private final String[] jwtRoutes;
    private BiConsumer<Application, Throwable> failureHandler;

    public Application(String jwtSecret, String... jwtRoutes) {
        this.jwtSecret = jwtSecret;
        this.jwtRoutes = jwtRoutes;
    }

    public Application() {
        this(null, (String[]) null);
    }

    /**
     * Initializes and starts the Vert.x HTTP server with configured routes.
     * This method is called when the verticle is deployed.
     *
     * @param startPromise Promise to be completed when the server has started
     */
    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());

        if (jwtSecret != null && jwtRoutes != null)
            for (String jwtRoute : jwtRoutes)
                EasyRouting.applyJWTAuth(vertx, router, jwtRoute, jwtSecret);

        EasyRouting.setupHandlers(router, this);

        createHttpServer(startPromise, router, port);
    }

    /**
     * Starts the application on the 8080.
     */
    public void start() {
        start(8080, null);
    }

    /**
     * Starts the application on the specified port.
     *
     * @param port the port number on which to start the server
     */
    public void start(int port) {
        start(port, null);
    }

    /**
     * Starts the application on the specified port.
     *
     * @param port              the port number on which to start the server
     * @param completionHandler a callback function that will be called when the server starts successfully
     */
    public void start(int port, Consumer<Application> completionHandler) {
        start(port, completionHandler, null);
    }

    /**
     * Starts the application on the specified port.
     *
     * @param port              the port number on which to start the server
     * @param completionHandler a callback function that will be called when the server starts successfully
     * @param failureHandler    a callback function that will be called when the server fails to start
     */
    public void start(int port, Consumer<Application> completionHandler, BiConsumer<Application, Throwable> failureHandler) {
        if (vertx == null) {
            this.completionHandler = completionHandler;
            this.failureHandler = failureHandler;
            this.port = port;
            vertx = Vertx.vertx();
            vertx.deployVerticle(this);
            startWaiting();
        } else {
            logger.warn("Application is already running on port: " + this.port);
        }
    }

    /**
     * Stops the application and closes the Vert.x instance.
     * If the application is not running, a warning message will be logged.
     */
    public void stop() {
        if (vertx != null) {
            vertx.close().onComplete(result -> {
                logger.info("Application stopped");
                stopWaiting();
            });
            vertx = null;
            completionHandler = null;
        } else {
            logger.warn("Application is not running, nothing to stop.");
        }
    }

    private final Object waitLock = new Object();

    private void stopWaiting() {
        synchronized (waitLock) {
            waitLock.notify();
        }
    }

    private void startWaiting() {
        try {
            synchronized (waitLock) {
                waitLock.wait();
            }
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    /**
     * Starts a console input loop that waits for user commands.
     * The application can be gracefully stopped by entering 'exit'.
     */
    public void waitForInput() {
        readInput = true;
        Thread inputThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (readInput) {
                String input = scanner.nextLine();
                if ("exit".equalsIgnoreCase(input.trim())) {
                    stop();
                    break;
                }
            }
            scanner.close();
        });
        inputThread.setDaemon(true);
        inputThread.start();
    }

    private void createHttpServer(Promise<Void> startPromise, Router router, int port) {
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onComplete(result -> {
                    if (result.succeeded()) {
                        logger.info("Application started on port: " + port);
                        startPromise.complete();
                        if (completionHandler != null) {
                            CompletableFuture.supplyAsync(() -> {
                                completionHandler.accept(this);
                                return null;
                            });
                        }
                    } else {
                        logger.error("Application failed to start on port: " + port + " - " +
                                result.cause().getMessage());
                        startPromise.fail(result.cause());
                        if (failureHandler != null) {
                            failureHandler.accept(this, result.cause());
                        }
                        stopWaiting();
                    }
                });
    }
}
