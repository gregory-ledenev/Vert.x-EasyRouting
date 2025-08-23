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

import io.vertx.core.Vertx;
import io.vertx.ext.web.common.template.TemplateEngine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

/**
 * A utility class for creating and managing template engines in a Vert.x application.
 */
public class TemplateEngineFactory {

    /**
     * Enum representing the supported template engine types.
     * Each type is associated with the class name of the corresponding template engine.
     */
    public enum Type {
        UNKNOWN(""),
        Handlebar("io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine"),
        Pug("io.vertx.ext.web.templ.pug.PugTemplateEngine"),
        MVEL("io.vertx.ext.web.templ.mvel.MVELTemplateEngine"),
        Thymeleaf("io.vertx.ext.web.templ.thymeleaf.ThymeleafTemplateEngine"),
        FreeMarker("io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine"),
        Pebble("io.vertx.ext.web.templ.pebble.PebbleTemplateEngine"),
        Rocker("io.vertx.ext.web.templ.rocker.RockerTemplateEngine");

        /**
         * Retrieves the class name of the template engine associated with this type.
         *
         * @return the class name of the template engine
         */
        public String getClassName() {
            return className;
        }

        private final String className;

        /**
         * Constructor for the enum type.
         *
         * @param aClassName the class name of the template engine
         */
        Type(String aClassName) {
            className = aClassName;
        }
    }

    /**
     * Creates a template engine instance based on the specified type.
     *
     * @param vertx       the Vert.x instance
     * @param type        the type of the template engine to create
     * @param configurator a function for additional configuration of the template engine
     * @return the created template engine instance
     */
    public static TemplateEngine createTemplateEngine(Vertx vertx, Type type, BiConsumer<TemplateEngine, Type> configurator) {
        TemplateEngine result = null;
        try {
            Method method = Class.forName(type.getClassName()).getDeclaredMethod("create", new Class<?>[]{Vertx.class});
            result = (TemplateEngine) method.invoke(null, vertx);
            if (configurator != null)
                configurator.accept(result, type);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
