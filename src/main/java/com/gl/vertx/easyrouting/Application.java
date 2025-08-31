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

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.common.template.TemplateEngine;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.HttpEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
public class Application implements EasyRouting.AnnotatedConvertersHolder, EasyRoutingContext, ApplicationObject {
    private static final Object shutdownLock = new Object();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final List<ApplicationModule<?>> applicationModules = new ArrayList<>();
    private final EasyRouting.AnnotatedConverters annotatedConverters = new EasyRouting.AnnotatedConverters();
    private final Object waitLock = new Object();
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
    private TemplateEngineFactory.Type templateEngineType = TemplateEngineFactory.Type.UNKNOWN;
    private BiConsumer<TemplateEngine, TemplateEngineFactory.Type> templateEngineConfigurator;
    private TemplateEngine templateEngine;
    private boolean clustered;
    private String nodeName;
    private ServiceDiscovery serviceDiscovery;
    private Record publishedRecord;
    private static final Map<String, CircuitBreaker> circuitBreakerCache = new ConcurrentHashMap<>();
    private static final Map<String, HttpClient> httpClientCache = new ConcurrentHashMap<>();
    private CircuitBreakerOptions circuitBreakerOptions = new CircuitBreakerOptions()
            .setMaxFailures(3)
            .setTimeout(5000)
            .setFallbackOnFailure(true)
            .setResetTimeout(10000);;

    @Override
    public EasyRouting.AnnotatedConverters getAnnotatedConverters() {
        return annotatedConverters;
    }

    @Override
    public Record getPublishedRecord() {
        return publishedRecord;
    }

    @SuppressWarnings("unchecked")
    protected <T extends Application> T self() {
        return (T) this;
    }

    /**
     * Configures the application to use JWT-based authentication. Sets up JWT authentication for a passed JWT secret
     * and defines the routes where JWT authentication should be applied.
     *
     * @param jwtSecret       the secret key used to sign and verify JWT tokens
     * @param protectedRoutes routes or endpoints requiring JWT authentication
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public <T extends Application> T jwtAuth(String jwtSecret, String... protectedRoutes) {
        this.jwtSecret = jwtSecret;
        this.protectedRoutes = protectedRoutes;

        return self();
    }

    /**
     * Configures the application to use JKS (Java KeyStore) for SSL/TLS encryption. Sets the path to the JKS key store
     * and the password for accessing it. If PEM key and certificate options are already set, they will be cleared and
     * ignored.
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
     * Configures the application to use PEM (Privacy Enhanced Mail) key and certificate for SSL/TLS encryption. Sets
     * the paths to the PEM key and certificate files. If JKS options are already set, they will be cleared and
     * ignored.
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
     * Registers a completion handler to be executed when the application starts successfully. The handler receives the
     * current application instance as a parameter.
     *
     * @param completionHandler a {@code Consumer} that processes the application instance upon successful startup
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public <T extends Application> T onStartCompletion(Consumer<Application> completionHandler) {
        this.completionHandler = completionHandler;
        return self();
    }

    /**
     * Registers a failure handler executed when an application startup failure occurs. The failure handler receives the
     * current application instance and the related {@code Throwable}.
     *
     * @param failureHandler a {@code BiConsumer} accepting the application instance and the exception that caused the
     *                       startup failure
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public <T extends Application> T onStartFailure(BiConsumer<Application, Throwable> failureHandler) {
        this.failureHandler = failureHandler;
        return self();
    }

    /**
     * Starts the application on a default port: 8080 or 8443 for SSL/TLS.
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public <T extends Application> T start() {
        start(0);

        return self();
    }

    /**
     * Starts the application on the specified port.
     *
     * @param port the port number on which to start the server
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public <T extends Application> T start(int port) {
        start(port, "localhost");
        return self();
    }

    /**
     * Starts the application on the specified port.
     *
     * @param port the port number on which to start the server
     * @param host the hostname or IP address to which the server will bind (e.g., "localhost" or "0.0.0.0")
     * @return the current {@code Application} instance, allowing for method chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    public <T extends Application> T start(int port, String host) {
        Objects.requireNonNull(host);

        if (applicationVerticle == null) {
            this.host = host;
            this.port = port;
            applicationVerticle = new ApplicationVerticle();

            if (!clustered) {
                vertx = Vertx.vertx();
                vertx.deployVerticle(applicationVerticle);
            } else {
                startClustered();
            }
            startWaiting();
        } else {
            logger.warn("Application is already running on port: " + this.port);
        }

        return self();
    }

    private void startClustered() {
        Vertx.clusteredVertx(new VertxOptions()).onComplete(res -> {
            if (res.succeeded()) {
                vertx = res.result();
                serviceDiscovery = ServiceDiscovery.create(vertx);
                vertx.deployVerticle(applicationVerticle);
                publishService(serviceDiscovery, nodeName);
            } else {
                System.err.println("Failed to start Vert.x in clustered mode: " + res.cause().getMessage());
            }
        });
    }

    /**
     * Enables clustered mode for the application, allowing it to join a Vert.x cluster.
     * This registers the node with the specified name in the cluster.
     *
     * @param nodeName the name to register this node under in the cluster
     * @return the current {@code Application} instance for method chaining
     */
    public Application clustered(String nodeName) {
        this.nodeName = nodeName;
        this.clustered = true;

        return this;
    }

    private void publishService(ServiceDiscovery discovery, String name) {
        Record record = HttpEndpoint.createRecord(name, isSsl(), host, port, "/", null);
        discovery.publish(record).onComplete((aRecord, ex) -> {
            if (ex== null) {
                this.publishedRecord = aRecord;
                logger.info("Service published with ID: " + aRecord.getRegistration());
            } else {
                logger.error("Cannot publish " + name + ": " + ex.getMessage(), ex);
            }
        });
    }

    /**
     * Registers a shutdown hook that ensures the application is properly stopped when the JVM shuts down. If the
     * application is running when the shutdown hook is triggered, it will initiate a graceful shutdown of the
     * application by calling the stop() method.
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
     * Registers an application module with this application. Application modules provide a way to modularize and extend
     * the application's functionality. Modules must be registered before the application starts running.
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

    /**
     * Registers a controller object with this application. The controller's methods annotated with routing annotations
     * (e.g., @GET, @POST) will be automatically mapped to corresponding HTTP routes. Protected routes require
     * authentication before they can be accessed.
     *
     * @param controller the controller object containing route handler methods
     * @return the current {@code Application} instance, allowing for method chaining
     * @throws IllegalStateException if attempting to register a controller after the application has started
     */
    public Application controller(Object controller) {
        return controller(controller, new String[0]);
    }

    /**
     * Registers a controller object with this application. The controller's methods annotated with routing annotations
     * (e.g., @GET, @POST) will be automatically mapped to corresponding HTTP routes. Protected routes require
     * authentication before they can be accessed.
     *
     * @param controller      the controller object containing route handler methods
     * @param protectedRoutes array of URL patterns for routes that require authentication
     * @return the current {@code Application} instance, allowing for method chaining
     * @throws IllegalStateException if attempting to register a controller after the application has started
     */
    public Application controller(Object controller, String... protectedRoutes) {
        Objects.requireNonNull(controller);
        if (isRunning())
            throw new IllegalStateException("Cannot register controller after application has started");
        ApplicationModule<?> applicationModule = new ApplicationModule<>(controller, protectedRoutes) {};
        applicationModule.setApplication(this);
        applicationModules.add(applicationModule);
        return this;
    }

    public TemplateEngine getTemplateEngine() {
        return templateEngine;
    }

    /**
     * Create a  {@code TemplateEngine} and associates it with the application
     *
     * @param type type of TemplateEngine to create
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public Application templateEngine(TemplateEngineFactory.Type type) {
        return templateEngine(type, null);
    }

    /**
     * Create a  {@code TemplateEngine} and associates it with the application
     *
     * @param type         type of TemplateEngine to create
     * @param configurator optional configurator used to customize created TemplateEngine
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public Application templateEngine(TemplateEngineFactory.Type type, BiConsumer<TemplateEngine, TemplateEngineFactory.Type> configurator) {
        this.templateEngineType = type;
        this.templateEngineConfigurator = configurator;

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
     * Stops the application and closes the Vert.x instance. If the application is not running, a warning message will
     * be logged.
     */
    public void stop() {
        if (vertx != null) {
            logger.info("Application stopping...");
            vertx.close().onComplete(result -> {
                stoppedImpl();
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

    @Override
    public void started() {
    }

    @Override
    public void stopped() {
    }

    private void startedImpl() {
        started();
        for (ApplicationModule<?> applicationModule : applicationModules)
            applicationModule.started();
    }

    private void stoppedImpl() {
        stopped();
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
     * @param expiresIn token expiration period; {@code null} for no expiration
     * @param jwtSecret the secret key used for signing JWT tokens. It can be a plain password or a string in PEM
     *                  format
     * @return a signed JWT token as a string
     */
    public String generateJWTToken(String userId, List<String> roles, Duration expiresIn, String jwtSecret) {
        return JWTUtil.generateToken(vertx, userId, roles, null, jwtSecret);
    }

    /**
     * Generates a JWT token for a user with the specified user ID, roles and expiration period. JWT secret should be
     * specified for application. Otherwise, call {@code generateJWTToken()}, explicitly providing secret.
     *
     * @param userId    the ID of the user
     * @param roles     the list of roles assigned to the user
     * @param expiresIn token expiration period; {@code null} for no expiration
     * @return a signed JWT token as a string
     */
    public String generateJWTToken(String userId, List<String> roles, Duration expiresIn) {
        Objects.requireNonNull(jwtSecret, "No JWT Secret defined for applications. " +
                "Call generateJWTToken() explicitly specifying secret.");
        return JWTUtil.generateToken(vertx, userId, roles, expiresIn, jwtSecret);
    }

    /**
     * Returns the Vert.x instance associated with this application. This instance can be used to access Vert.x
     * functionality directly.
     *
     * @return the {@link Vertx} instance used by this application, or {@code null} if the application hasn't been
     * started
     */
    public Vertx getVertx() {
        return vertx;
    }

    /**
     * Returns any failure that occurred during the execution of the completion handler. This method can be used to
     * check if there were any errors when the application completed its startup process.
     *
     * @return the {@code Throwable} that occurred during completion handler execution, or {@code null} if no error
     * occurred
     */
    public Throwable getCompletionHandlerFailure() {
        return completionHandlerFailure;
    }

    /**
     * Handles any failures that occurred during the execution of the completion handler. If a completion handler
     * failure exists, this method will throw the appropriate exception: - If the failure's cause is an
     * {@code AssertionError}, it throws the cause - Otherwise, it throws the completion handler failure itself
     * <p>
     * This method is particularly useful for testing scenarios where you need to propagate assertion failures from the
     * completion handler to the test method.
     *
     * @throws Throwable if a completion handler failure occurred, either the original failure or its cause in case of
     *                   AssertionError
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
     * Starts a console input loop that waits for user commands. The application can be gracefully stopped by entering
     * 'exit'.
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

    /**
     * Checks if the {@code Application} runs in SSL mode
     * @return {@code true} if the {@code Application} runs in SSL mode; {@code false} otherwise
     */
    public boolean isSsl() {
        return jksOptions != null || pemKeyCertOptions != null;
    }

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

    class ApplicationVerticle extends AbstractVerticle {
        @Override
        public void start(Promise<Void> startPromise) {
            if (templateEngineType != TemplateEngineFactory.Type.UNKNOWN)
                templateEngine = TemplateEngineFactory.createTemplateEngine(vertx,
                        templateEngineType, templateEngineConfigurator);

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

            EasyRouting.setupController(router, Application.this, Application.this);
            new RpcController(Application.this, Application.this).setupController(router);

            createHttpServer(startPromise, router, port);

            startedImpl();
        }

        @Override
        public void stop() throws Exception {
            if (serviceDiscovery != null && publishedRecord != null) {
                serviceDiscovery.unpublish(publishedRecord.getRegistration()).onComplete((aUnused, aThrowable) -> {
                    serviceDiscovery.close();
                });
            }
            super.stop();
        }
    }

    public String getNodeName() {
        return nodeName;
    }

    public ServiceDiscovery getServiceDiscovery() {
        return serviceDiscovery;
    }

    /**
     * Specifies {@code CircuitBreakerOptions} for all managed {@code CircuitBreaker}'s and associates them with the application
     *
     * @param circuitBreakerOptions circuit breaker options
     * @return the current {@code Application} instance, allowing for method chaining
     */
    public Application circuitBreaker(CircuitBreakerOptions circuitBreakerOptions) {
        this.circuitBreakerOptions = circuitBreakerOptions;

        return this;
    }

    /**
     * Retrieves a {@link CircuitBreaker} instance by name, creating it if it does not exist.
     * The circuit breaker is configured with default options (if they are not explicitly specified via {@code circuitBreaker}):
     * max failures = 3, timeout = 5000ms, fallback on failure enabled, reset timeout = 10000ms.
     *
     * @param name the name of the circuit breaker
     * @return the {@link CircuitBreaker} instance associated with the given name
     */
    public CircuitBreaker getCircuitBreaker(String name) {
        return circuitBreakerCache.computeIfAbsent(name, key -> CircuitBreaker.create(key, vertx, circuitBreakerOptions));
    }
}
