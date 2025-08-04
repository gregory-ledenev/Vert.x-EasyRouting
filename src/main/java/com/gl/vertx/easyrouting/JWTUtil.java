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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for handling JWT authentication in a Vert.x application.
 * Provides methods to apply JWT authentication to routes and to create handlers that check user roles.
 */
public class JWTUtil {

    static final String HS_256 = "HS256";
    static final String SUB = "sub";
    static final String ROLES = "roles";

    /**
     * Generates a JWT token for a user with the specified user ID and roles.
     *
     * @param userId    the ID of the user
     * @param roles     the list of roles assigned to the user
     * @param jwtSecret the secret key used for signing JWT tokens. It can be a plain password or a string in PEM format
     * @return a signed JWT token as a string
     */
    public static String generateToken(Vertx vertx, String userId, List<String> roles, String jwtSecret) {
        JsonObject claims = new JsonObject()
                .put(SUB, userId)
                .put(ROLES, new JsonArray(roles));

        return JWTAuth.create(vertx, getJWTAuthOptions(jwtSecret)).generateToken(claims);
    }

    /**
     * Represents a parsed JWT token containing user information and roles.
     */
    public record Token(String user, List<String> roles) {}

    /**
     * Asynchronously parses a JWT token and extracts user information and roles.
     *
     * @param token     the JWT token to parse
     * @param jwtSecret the secret key used for signing JWT tokens. It can be a plain password or a string in PEM format
     * @return a Future containing the parsed Token object with user ID and roles
     */
    public static Future<Token> parseTokenAsync(Vertx vertx, String token, String jwtSecret) {
        JWTAuth jwtAuth = JWTAuth.create(vertx, getJWTAuthOptions(jwtSecret));
        return jwtAuth.authenticate(new TokenCredentials(token))
                .onSuccess(User::principal)
                .map(user -> new Token(user.principal().getString(SUB),
                        user.principal().getJsonArray(ROLES, new JsonArray()).stream()
                                .map(Object::toString)
                                .toList()));
    }

    /**
     * Synchronously parses a JWT token and extracts user information and roles.
     * This method blocks the current thread until the token is parsed.
     * <b>NOT call this method on Vert.x event loop threads, <b/> use async version instead.
     *
     * @param token     the JWT token to parse
     * @param jwtSecret the secret key used for signing JWT tokens. It can be a plain password or a string in PEM format
     * @return a Token object containing user ID and roles
     * @throws IllegalArgumentException if the token parsing fails
     */
    public static Token parseToken(Vertx vertx, String token, String jwtSecret) {
        try {
            return parseTokenAsync(vertx, token, jwtSecret).toCompletionStage().toCompletableFuture().get();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse token", e);
        }
    }

    /**
     * Applies JWT authentication with common "HS256" algorithm to a specified route in the given router.
     * Sample use would be as simple as:<br><br>
     * {@code JWTUtil.applyAuth(vertx, router, "/api/*", "very long password");}
     * <br><br>
     * that applies JWT authentication to all routes
     * starting with "/api/".
     *
     * @param vertx      the Vert.x instance
     * @param router     the router to which the route will be added
     * @param path       the path for which JWT authentication should be applied
     * @param jwtSecret  the secret key used for signing JWT tokens. It can be a plain password or a string in PEM format
     * @return the created route with JWT authentication applied
     */
    public static Route applyAuth(Vertx vertx, Router router,
                                  String path,
                                  String jwtSecret) {
        return router.route(path).handler(
                JWTAuthHandler.create(JWTAuth.create(vertx, getJWTAuthOptions(jwtSecret))));
    }

    private static JWTAuthOptions getJWTAuthOptions(String jwtSecret) {
        return new JWTAuthOptions().addPubSecKey(new PubSecKeyOptions()
                .setAlgorithm(HS_256)
                .setBuffer(jwtSecret));
    }

    /**
     * Creates a handler that checks if the user has a specific role before allowing access to the route.
     * If a role is matched, it sets the user ID and roles in the "X-User-ID" and "X-User-Roles" request headers
     * before executing the {@code handler}. Sample code would be as follows:
     *
     * <pre><code>
     * router.get("api/products/admin/:id").handler(guardedHandler("admin", ctx -> {
     *     ctx.response().end("Admin access to Products granted");
     * }));<code/></pre>
     *
     * @param requiredRole   the role that the user must have
     * @param handler        the handler to execute if the user has the required role
     * @return a handler that checks the user's roles and executes the provided handler if authorized
     */
    public static Handler<RoutingContext> guardedHandler(String requiredRole, Handler<RoutingContext> handler) {
        return guardedHandler(requiredRole, handler, null);
    }

    /**
     * Creates a handler that checks if the user has a specific role before allowing access to the route.
     * If the user does not have the required role, it can execute a custom function to handle unauthorized access.
     * If a role is matched, it sets the user ID and roles in the "X-User-ID" and "X-User-Roles" request headers
     * before executing the {@code handler}. Sample code would be as follows:
     *
     * <pre><code>
     * router.get("api/products/admin/:id").handler(guardedHandler("admin", ctx -> {
     *     ctx.response().end("Admin access to Products granted");
     * }));<code/></pre>
     *
     * @param requiredRole   the role that the user must have
     * @param handler        the handler to execute if the user has the required role
     * @param notAuthorised  a function to execute if the user is not authorized; can return true if handled
     * @return a handler that checks the user's roles and executes the provided handler if authorized
     */
    public static Handler<RoutingContext> guardedHandler(String requiredRole, Handler<RoutingContext> handler,
                                                         Function<RoutingContext, Boolean> notAuthorised) {
        return routingContext -> {
            JsonObject principal = routingContext.user().principal();
            String userId = principal.getString(SUB);
            JsonArray rolesArray = principal.getJsonArray(ROLES, new JsonArray());

            if (rolesArray == null || ! rolesArray.contains(requiredRole)) {
                boolean handled = false;
                if (notAuthorised != null)
                    handled = notAuthorised.apply(routingContext);
                if (! handled) {
                    routingContext.response().setStatusCode(403).end("Forbidden: Missing role '" + requiredRole + "'");
                    return;
                }
            }

            routingContext.request().headers().set("X-User-ID", userId);
            if (rolesArray != null) {
                routingContext.request().headers().set("X-User-Roles", rolesArray.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(",")));
            }

            handler.handle(routingContext);
        };
    }
}