package com.gl.vertx.easyrouting;

import com.gl.vertx.easyrouting.annotations.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gl.vertx.easyrouting.annotations.HttpMethods.*;


/**
 * A sample user administration application demonstrating EasyRouting capabilities.
 * Provides REST endpoints for user management and authentication.
 * <p>
 * Authentication endpoints:
 * - POST /login - Authenticates user and returns JWT token
 * <p>
 * Static content:
 * - GET /* - Serves static files from resources
 * <p>
 * User management endpoints (requires authentication):
 * - GET /api/users - Returns a list of all users
 * - POST /api/users - Creates new user (admin role required)
 * - PUT /api/users/:id - Updates existing user (admin role required)
 * - DELETE /api/users/:id - Deletes user (admin role required)
 * <p>
 * Error handling:
 * - GET /unauthenticated - Returns 401 error for unauthorized access
 */
@SuppressWarnings("unused")

public class UserAdminApplication extends Application {
    static class UserApplicationModule extends ApplicationModule<UserAdminApplication> {
        @GET("/api/users")
        List<User> getUsers() {
            return application.userService.getUsers();
        }

        @GET("/api/users/:id") @NotNullResult(value = "No user found", statusCode = 404)
        User getUser(@Param("id") String id) {
            return application.userService.getUser(id);
        }

        @POST(value = "/api/users", requiredRoles = {"admin"})
        User addUser(@BodyParam("user") User user) {
            return application.userService.addUser(user);
        }

        @PUT(value = "/api/users/:id", requiredRoles = {"admin"})
        User updateUser(@Param("id") String id, @BodyParam("user") User user) {
            return application.userService.updateUser(user, id);
        }

        @DELETE(value = "/api/users/:id", requiredRoles = {"admin"})
        boolean deleteUser(@Param("id") String id) {
            return application.userService.deleteUser(id);
        }

        public UserApplicationModule(String... protectedRoutes) {
            super(protectedRoutes);
        }
    }

    private final LoginService loginService;
    private final UserService userService;
    static final String JWT_SECRET = "very looooooooooooooooooooooooooooooooooooong secret";

    public static void main(String[] args) {
        UserAdminApplication userAdminApplication = new UserAdminApplication(new LoginService(), new UserService()).
                module(new UserApplicationModule("/api/*")).
                templateEngine(TemplateEngineFactory.Type.Thymeleaf).
                jwtAuth(JWT_SECRET).
                sslWithJks("keystore", "1234567890").
                handleShutdown().
                start();
    }

    UserAdminApplication(LoginService loginService, UserService userService) {
        this.userService = userService;
        this.loginService = loginService;
    }

    @POST("/login") @NotNullResult(value = "Invalid credentials", statusCode = 401)
    String login(@BodyParam("path") LoginData loginData) {
        return loginService.login(loginData.username(), loginData.password());
    }

    @GET("/*")
    @Template @FileFromFolder("documents")
    String get(@PathParam("path") String path, TemplateModel templateModel) {
        templateModel.put("title", "SOME CUSTOM TITLE");
        return path;
    }

    @HandlesStatusCode(401)
    @GET("/unauthenticated")
    Result<?> unauthenticated(@Param(value = "redirect", defaultValue = "") String redirect) {
        return new Result<>("You are not unauthenticated to access this: " + redirect, 401);
    }
}

record LoginData(String username, String password) {}

record User(String id, String name, String email) {
    private static int userIDCounter = 1;
    public User(String name, String email) {
        this(String.valueOf(userIDCounter++), name, email);
    }

    public static void clearUserIDCounter() {
        userIDCounter = 1;
    }
}

@SuppressWarnings("SameReturnValue")
class UserService {
    private final LinkedHashMap<String, User> users = new LinkedHashMap<>();

    public UserService() {
        User john = new User("John Doe", "john.doe@example.com");
        User sarah = new User("Sarah Wilson", "sarah.wilson@example.com");
        User mike = new User("Michael Smith", "mike.smith@example.com");
        User emily = new User("Emily Johnson", "emily.j@example.com");
        User david = new User("David Brown", "d.brown@example.com");
        users.put(john.id(), john);
        users.put(sarah.id(), sarah);
        users.put(mike.id(), mike);
        users.put(emily.id(), emily);
        users.put(david.id(), david);
    }

    public List<User> getUsers() {
        return new ArrayList<>(users.values());
    }

    public User addUser(User user) {
        User newUser = new User(user.name(), user.email());
        users.put(newUser.id(), newUser);
        return newUser;
    }

    public User updateUser(User user, String id) {
        User newUser = new User(id, user.name(), user.email());
        users.put(newUser.id(), newUser);
        return newUser;
    }

    public boolean deleteUser(String id) {
        users.remove(id);
        return true;
    }

    public User getUser(String id) {
        return users.get(id);
    }
}

class LoginService {
    public String login(String user, String password) {
        if (user.equals("admin") && password.equals("admin_pwd"))
            return JWTUtil.generateToken(null, user, List.of("user", "admin"), null, UserAdminApplication.JWT_SECRET);
        if (user.equals("user") && password.equals("user_pwd"))
            return JWTUtil.generateToken(null, user, List.of("user"), null, UserAdminApplication.JWT_SECRET);
        else
            return null;
    }
}

