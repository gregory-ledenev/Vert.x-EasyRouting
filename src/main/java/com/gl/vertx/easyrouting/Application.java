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

import java.util.List;
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
public class Application {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private int port;
    private boolean readInput;
    private Consumer<Application> completionHandler;
    private final String jwtSecret;
    private final String[] jwtRoutes;
    private BiConsumer<Application, Throwable> failureHandler;

    private Throwable completionHandlerFailure;
    private ApplicationVerticle applicationVerticle;
    private Vertx vertx;

    class ApplicationVerticle extends AbstractVerticle {
        @Override
        public void start(Promise<Void> startPromise) {
            Router router = Router.router(vertx);

            router.route().handler(BodyHandler.create());

            if (jwtSecret != null && jwtRoutes != null)
                for (String jwtRoute : jwtRoutes)
                    EasyRouting.applyJWTAuth(vertx, router, jwtRoute, jwtSecret);

            EasyRouting.setupHandlers(router, Application.this);

            createHttpServer(startPromise, router, port);
        }
    }

    public Application(String jwtSecret, String... jwtRoutes) {
        this.jwtSecret = jwtSecret;
        this.jwtRoutes = jwtRoutes;
    }

    public Application() {
        this(null, (String[]) null);
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
     * @param completionHandler a callback function that will be called when the server starts successfully. You may put
     *                          your testing code to that handler to ensure it runs only when the server is ready.
     * @param failureHandler    a callback function that will be called when the server fails to start
     */
    public void start(int port, Consumer<Application> completionHandler, BiConsumer<Application, Throwable> failureHandler) {
        if (applicationVerticle == null) {
            this.completionHandler = completionHandler;
            this.failureHandler = failureHandler;
            this.port = port;
            applicationVerticle = new ApplicationVerticle();
            vertx = Vertx.vertx();
            vertx.deployVerticle(applicationVerticle);
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
            logger.info("Application stopping...");
            vertx.close().onComplete(result -> {
                stopWaiting();
                synchronized (shutdownLock) {
                    shutdownLock.notify();
                }
            });
            synchronized (shutdownLock) {
                try {
                    shutdownLock.wait();
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
            vertx = null;
            completionHandler = null;
            applicationVerticle = null;
            logger.info("Application stopped");
            System.out.println("Application stopped");
        } else {
            logger.warn("Application is not running, nothing to stop.");
        }
    }

    /**
     * Checks if the application is currently running.
     *
     * @return {@code true} if the application is running, {@code false} otherwise
     */
    public boolean isRunning() {
        return vertx != null;
    }

    /**
     * Generates a JWT token for a user with the specified user ID and roles.
     *
     * @param userId    the ID of the user
     * @param roles     the list of roles assigned to the user
     * @param jwtSecret the secret key used for signing JWT tokens. It can be a plain password or a string in PEM format
     * @return a signed JWT token as a string
     */
    public String generateJWTToken(String userId, List<String> roles, String jwtSecret) {
        return JWTUtil.generateToken(vertx, userId, roles, jwtSecret);
    }

    /**
     * Returns the Vert.x instance associated with this application.
     * This instance can be used to access Vert.x functionality directly.
     *
     * @return the {@link Vertx} instance used by this application, or {@code null} if the application hasn't been started
     */
    public Vertx getVertx() {
        return vertx;
    }

    /**
     * Returns any failure that occurred during the execution of the completion handler.
     * This method can be used to check if there were any errors when the application
     * completed its startup process.
     *
     * @return the {@code Throwable} that occurred during completion handler execution, or {@code null} if no error occurred
     */
    public Throwable getCompletionHandlerFailure() {
        return completionHandlerFailure;
    }


    /**
     * Handles any failures that occurred during the execution of the completion handler.
     * If a completion handler failure exists, this method will throw the appropriate exception:
     * - If the failure's cause is an {@code AssertionError}, it throws the cause
     * - Otherwise, it throws the completion handler failure itself
     * <p>
     * This method is particularly useful for testing scenarios where you need to propagate
     * assertion failures from the completion handler to the test method.
     *
     * @throws Throwable if a completion handler failure occurred, either the original failure
     *                   or its cause in case of AssertionError
     */
    public void handleCompletionHandlerFailure() throws Throwable {
        if (getCompletionHandlerFailure() != null) {
            if (getCompletionHandlerFailure().getCause() instanceof AssertionError)
                throw getCompletionHandlerFailure().getCause();
            else
                throw getCompletionHandlerFailure();
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

    private static final Object shutdownLock= new Object();

    /**
     * Registers a shutdown hook that ensures the application is properly stopped when the JVM shuts down.
     * If the application is running when the shutdown hook is triggered, it will initiate a graceful
     * shutdown of the application by calling the stop() method.
     */
    public void handleShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isRunning()) {
                System.out.println("Shutting down...");
                stop();
            }
        }));
    }

    private void createHttpServer(Promise<Void> startPromise, Router router, int port) {
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onComplete(result -> {
                    if (result.succeeded()) {
                        logger.info("Server started on port: " + port);
                        startPromise.complete();
                        logger.info("Application started");
                        if (completionHandler != null) {
                            CompletableFuture.supplyAsync(() -> {
                                try {
                                    completionHandler.accept(this);
                                } catch (Throwable e) {
                                    completionHandlerFailure = e;
                                }
                                if (completionHandlerFailure != null)
                                    throw new RuntimeException(completionHandlerFailure);
                                return null;
                            });
                        }
                    } else {
                        logger.error("Server failed to start on port: " + port + " - " +
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
