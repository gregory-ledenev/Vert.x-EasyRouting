package com.gl.vertx.easyrouting;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class UserAdmin extends Application {
    public static void main(String[] args) {
        UserAdmin app = new UserAdmin();
        app.start();
    }

    record User(String id, String name, String email) {
        public User(String name, String email) {
            this(UUID.randomUUID().toString(), name, email);
        }
    }

    private final LinkedHashMap<String, User> users = new LinkedHashMap<>();

    public UserAdmin() {
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

    @HttpMethods.GET("/*")
    HandlerResult<String> get(@PathParam("path") String path) {
        if (path.contains(".."))
            return new HandlerResult<>("Forbidden", 403);

        String file;
        if (path.equals("/"))
            file = "index.html";
        else
            file = path.substring(path.lastIndexOf('/') + 1);

        try(InputStream in = getClass().getResourceAsStream(file)) {
            if (in != null)
                return HandlerResult.file(new String(in.readAllBytes()), file);
            else
                return new HandlerResult<>("File not found: " + file, 404);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @HttpMethods.GET("/api/users")
    List<User> getUsers() {
        return new ArrayList<>(users.values());
    }

    @HttpMethods.POST("/api/users")
    User addUser(@BodyParam("user") User user) {
        User newUser = new User(user.name(), user.email());
        users.put(newUser.id(), newUser);
        return newUser;
    }

    @HttpMethods.PUT("/api/users/:id")
    User updateUser(@BodyParam("user") User user, @Param("id") String id) {
        User newUser = new User(id, user.name(), user.email());
        users.put(newUser.id(), newUser);
        return newUser;
    }

    @HttpMethods.DELETE("/api/users/:id")
    String deleteUser(@Param("id") String id) {
        users.remove(id);
        return "User deleted: " + id;
    }
}
