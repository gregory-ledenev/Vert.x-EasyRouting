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

import io.vertx.ext.web.Router;

/**
 * Base class for application modules that can be registered with an {@link Application}.
 * Modules provide a way to organize and modularize application functionality by grouping
 * related endpoint handlers together.
 *
 * @param <T> the type of Application this module works with
 * @see Application
 */
public abstract class ApplicationModule<T extends Application> implements EasyRouting.AnnotatedConvertersHolder {
    protected T application;
    private final String[] protectedRoutes;

    @Override
    public EasyRouting.AnnotatedConverters getAnnotatedConverters() {
        return application.getAnnotatedConverters();
    }

    public T getApplication() {
        return application;
    }

    void setApplication(Application application) {
        //noinspection unchecked
        this.application = (T) application;
    }

    /**
     * Creates a new ApplicationModule with the specified application instance and protected routes.
     * Protected routes require authentication before they can be accessed.
     *
     * @param protectedRoutes array of URL patterns for routes that require authentication
     */
    public ApplicationModule(String... protectedRoutes) {
        this.protectedRoutes = protectedRoutes;
    }

    /**
     * Creates a new ApplicationModule with the specified application instance and protected routes.
     * Protected routes require authentication before they can be accessed.
     */
    public ApplicationModule() {
        this(new String[0]);
    }

    /**
     * Gets the array of protected route patterns configured for this module.
     *
     * @return array of URL patterns for routes that require authentication
     */
    public String[] getProtectedRoutes() {
        return protectedRoutes;
    }

    /**
     * Called when the module is started and registered with an application.
     * Sets up the module with a reference to the parent application instance.
     */
    @SuppressWarnings("EmptyMethod")
    public void started() {
    }

    /**
     * Called when the module is being stopped and unregistered from the application.
     * Cleans up the module's reference to the parent application.
     */
    @SuppressWarnings("EmptyMethod")
    public void stopped() {
    }

    /**
     * Sets up the controller with the specified router, registering all annotated methods
     * as endpoints based on their annotations.
     *
     * @param router The Vert.x Router to register the controller with
     */
    public void setupController(Router router) {
        EasyRouting.setupController(router, this);
        new RpcController(this).setupController(router);
    }
}
