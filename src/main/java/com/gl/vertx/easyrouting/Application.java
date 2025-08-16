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
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
public class Application implements EasyRouting.AnnotatedConvertersHolder {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private int port;
    private boolean readInput;
    private Consumer<Application> completionHandler;
    private String jwtSecret;
    private String[] protectedRoutes;
    private BiConsumer<Application, Throwable> failureHandler;

    private Throwable completionHandlerFailure;
    private ApplicationVerticle applicationVerticle;
    private Vertx vertx;
    private Thread inputThread;
    private String host = "localhost";
    private JksOptions jksOptions;
    private PemKeyCertOptions pemKeyCertOptions;
    private Thread shutdownHook;
    private final List<ApplicationModule<?>> applicationModules = new ArrayList<>();
    private final EasyRouting.AnnotatedConverters annotatedConverters = new EasyRouting.AnnotatedConverters();

    @Override
    public EasyRouting.AnnotatedConverters getAnnotatedConverters() {
        return annotatedConverters;
    }

    class ApplicationVerticle extends AbstractVerticle {
        @Override
        public void start(Promise<Void> startPromise) {
            Router router = Router.router(vertx);

            router.route().handler(BodyHandler.create());

            if (jwtSecret != null) {
                for (ApplicationModule<?> applicationModule : applicationModules) {
                    for (String protectedRoute : applicationModule.getProtectedRoutes())
                        EasyRouting.applyJWTAuth(vertx, router, protectedRoute, jwtSecret);
                }

                if (protectedRoutes != null) {
                    for (String protectedRoute : protectedRoutes)
                        EasyRouting.applyJWTAuth(vertx, router, protectedRoute, jwtSecret);
                }
            }

            for (ApplicationModule<?> applicationModule : applicationModules)
                applicationModule.setupController(router);

            EasyRouting.setupController(router, Application.this);

            createHttpServer(startPromise, router, port);

            started();
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends Application> T self() {
        return (T) this;
    }

    /**
     * Configures the application to use JWT-based authentication.
     * Sets up JWT authentication for a passed JWT secret and defines the routes
     * where JWT authentication should be applied.
     *
     * @param jwtSecret the secret key used to sign and verify JWT tokens
     * @param protectedRoutes routes or endpoints requiring JWT authentication
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public <T extends Application> T jwtAuth(String jwtSecret, String... protectedRoutes) {
        this.jwtSecret = jwtSecret;
        this.protectedRoutes = protectedRoutes;

        return self();
    }

    /**
     * Configures the application to use JKS (Java KeyStore) for SSL/TLS encryption.
     * Sets the path to the JKS key store and the password for accessing it.
     * If PEM key and certificate options are already set, they will be cleared and ignored.
     *
     * @param jksKeyStorePath     the path to the JKS key store file
     * @param jksKeyStorePassword the password for the JKS key store
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public <T extends Application> T sslWithJks(String jksKeyStorePath, String jksKeyStorePassword) {
        Objects.requireNonNull(jksKeyStorePath);
        Objects.requireNonNull(jksKeyStorePassword);

        this.jksOptions = new JksOptions()
                .setPath(jksKeyStorePath)
                .setPassword(jksKeyStorePassword);

        if (pemKeyCertOptions != null) {
            logger.warn("PEM key and certificate options are ignored when when JKS key store options are used");
            pemKeyCertOptions = null;
        }

        return self();
    }

    /**
     * Configures the application to use PEM (Privacy Enhanced Mail) key and certificate for SSL/TLS encryption.
     * Sets the paths to the PEM key and certificate files.
     * If JKS options are already set, they will be cleared and ignored.
     *
     * @param keyPath  the path to the PEM key file
     * @param certPath the path to the PEM certificate file
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public <T extends Application> T sslWithPem(String keyPath, String certPath) {
        Objects.requireNonNull(keyPath);
        Objects.requireNonNull(certPath);

        if (jksOptions != null) {
            logger.warn("JKS options are ignored when when PEM key and certificate options are used");
            jksOptions = null;
        }

        this.pemKeyCertOptions = new PemKeyCertOptions()
                .setKeyPath(keyPath)
                .setCertPath(certPath);

        return self();
    }

    /**
     * Registers a completion handler to be executed when the application starts successfully.
     * The handler receives the current application instance as a parameter.
     *
     * @param completionHandler a {@code Consumer} that processes the application instance upon successful startup
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public <T extends Application> T onStartCompletion(Consumer<Application> completionHandler) {
        this.completionHandler = completionHandler;
        return self();
    }

    /**
     * Registers a failure handler executed when an application startup failure occurs.
     * The failure handler receives the current application instance and the related {@code Throwable}.
     *
     * @param failureHandler a {@code BiConsumer} accepting the application instance and the exception
     *                       that caused the startup failure
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public <T extends Application> T onStartFailure(BiConsumer<Application, Throwable> failureHandler) {
        this.failureHandler = failureHandler;
        return self();
    }

    /**
     * Starts the application on a default port: 8080 or 8443 for SSL/TLS.
     */
    public <T extends Application> T start() {
        start(0);

        return self();
    }

    /**
     * Starts the application on the specified port.
     *
     * @param port the port number on which to start the server
     */
    public <T extends Application> T start(int port) {
        start(port, "localhost");
        return self();
    }

    /**
     * Starts the application on the specified port.
     *
     * @param port              the port number on which to start the server
     * @param host              the hostname or IP address to which the server will bind (e.g., "localhost" or "0.0.0.0")
     */
    public <T extends Application> T start(int port, String host) {
        Objects.requireNonNull(host);

        if (applicationVerticle == null) {
            this.host = host;
            this.port = port;
            applicationVerticle = new ApplicationVerticle();
            vertx = Vertx.vertx();
            vertx.deployVerticle(applicationVerticle);
            startWaiting();
        } else {
            logger.warn("Application is already running on port: " + this.port);
        }

        return self();
    }

    /**
     * Registers a shutdown hook that ensures the application is properly stopped when the JVM shuts down.
     * If the application is running when the shutdown hook is triggered, it will initiate a graceful
     * shutdown of the application by calling the stop() method.
     */
    public Application handleShutdown() {
        if (shutdownHook != null) {
            logger.warn("Shutdown hook is already registered");
            return this;
        }

        shutdownHook = new Thread(() -> {
            if (isRunning()) {
                System.out.println("Shutting down...");
                stop();
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        return this;
    }

    /**
     * Registers an application module with this application.
     * Application modules provide a way to modularize and extend the application's functionality.
     * Modules must be registered before the application starts running.
     *
     * @param applicationModule the module to register with this application
     * @return the current {@code Application} instance, allowing for method chaining
     * @throws IllegalStateException if attempting to register a module after the application has started
     */
    public Application module(ApplicationModule<? extends Application> applicationModule) {
        Objects.requireNonNull(applicationModule);
        if (isRunning())
            throw new IllegalStateException("Cannot register application module after application has started");
        applicationModule.setApplication(this);
        applicationModules.add(applicationModule);
        return this;
    }

    private void removeShutdownHook() {
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                // ignore - JVM is already shutting down
            }
            shutdownHook = null;
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
                stopped();
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

    public void started() {
        for (ApplicationModule<?> applicationModule : applicationModules)
            applicationModule.started();
    }

    public void stopped() {
        for (ApplicationModule<?> applicationModule : applicationModules)
            applicationModule.stopped();
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
        // required to let the completion handler store completionHandlerFailure if any
        Thread.sleep(100);

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

        if (inputThread != null) {
            inputThread.interrupt();
            inputThread = null;
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
        inputThread = new Thread(() -> {
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

    private void createHttpServer(Promise<Void> startPromise, Router router, int port) {
        HttpServerOptions options = new HttpServerOptions();

        if (jksOptions != null) {
            options.setSsl(true).setKeyCertOptions(jksOptions);
        } else if (pemKeyCertOptions != null) {
            options.setSsl(true).setKeyCertOptions(pemKeyCertOptions);
        }

        final int portToUse = port == 0 ? (options.isSsl() ? 8443 : 8080) : port;

        vertx.createHttpServer(options)
                .requestHandler(router)
                .listen(portToUse, host)
                .onComplete(result -> {
                    if (result.succeeded()) {
                        logger.info("Server started on port: " + portToUse);
                        startPromise.complete();
                        logger.info("Application started");
                        if (completionHandler != null) {
                            CompletableFuture.supplyAsync(() -> {
                                try {
                                    completionHandler.accept(this);
                                } catch (Throwable e) {
                                    completionHandlerFailure = e;
                                }
                                return null;
                            });
                        }
                    } else {
                        removeShutdownHook();
                        String error = "Server failed to start on port: " + portToUse + " - " + result.cause().getMessage();
                        logger.error(error);
                        startPromise.fail(result.cause());
                        if (failureHandler != null) {
                            failureHandler.accept(this, result.cause());
                        }
                        completionHandlerFailure = new IllegalStateException(error);
                        vertx.close();
                        stopWaiting();
                    }
                });
    }
}
