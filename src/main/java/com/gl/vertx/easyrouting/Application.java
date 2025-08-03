package com.gl.vertx.easyrouting;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * An application class that allows building minimal applications for prototyping and testing purposes.
 */
public class Application extends AbstractVerticle {
    private Vertx vertx;
    private final Logger logger = Logger.getLogger(this.getClass().getName());
    private int port;
    private boolean readInput;
    private Consumer<Application> completionHandler;

    /**
     * Initializes and starts the Vert.x HTTP server with configured routes.
     * This method is called when the verticle is deployed.
     *
     * @param startPromise Promise to be completed when the server has started
     * @throws Exception if server initialization fails
     */
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());
        EasyRouting.setupHandlers(router, this);

        createHttpServer(startPromise, router, port);
    }

    /**
     * Starts the application on the specified port.
     * If the application is already running, a warning message will be logged.
     *
     * @param port the port number on which to start the server
     */
    public void start(int port) {
        start(port, null);
    }

    /**
     * Starts the application on the specified port.
     * If the application is already running, a warning message will be logged.
     *
     * @param port              the port number on which to start the server
     * @param completionHandler a callback function that will be called when the server starts successfully
     */
    public void start(int port, Consumer<Application> completionHandler) {
        if (vertx == null) {
            this.completionHandler = completionHandler;
            this.port = port;
            vertx = Vertx.vertx();
            vertx.deployVerticle(this);
            startWaiting();
        } else {
            logger.warning("Application is already running on port: " + this.port);
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
            logger.warning("Application is not running, nothing to stop.");
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
     * The application can be stopped by entering 'exit'.
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
    
    /**
     * Creates and configures the HTTP server with the provided router.
     *
     * @param startPromise Promise to be completed when the server has started
     * @param router       Router instance containing configured routes
     * @param port         the port number on which to start the server
     */
    private void createHttpServer(Promise<Void> startPromise, Router router, int port) {
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onComplete(result -> {
                    if (result.succeeded()) {
                        logger.info("Application started on port: " + port);
                        startPromise.complete();
                        if (completionHandler != null)
                            CompletableFuture.supplyAsync(() -> {
                                completionHandler.accept(this);
                                return null;
                            });
                    } else {
                        logger.severe("Application failed to start on port: " + port + " - " +
                                result.cause().getMessage());
                        startPromise.fail(result.cause());
                    }
                });
    }
}
