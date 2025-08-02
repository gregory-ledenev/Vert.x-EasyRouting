# EasyRouting for Vert.x

EasyRouting is an experimental lightweight annotation-based HTTP routing library for Vert.x web applications. It
simplifies route configuration by allowing developers to define routes using annotations and automatically handles
parameter binding and response processing. While inspired by SpringBoot's annotation-based routing patterns, it maintains a focused, lightweight implementation without attempting to replicate SpringBoot's entire feature set.

## Key Features

- Annotation-based route definitions for all common HTTP methods (GET, POST, PUT, DELETE, PATCH)
- Automatic parameter binding from request parameters and body
- Support for optional parameters
- File upload/download handling
- JSON object/array conversion
- Automatic response processing
- Redirect handling

## Basic Usage

### 1. Create a Controller Class

``` java
public class UserController {
    
    @HttpMethods.GET("/users")
    public List<User> getAllUsers() {
        // Return all users
        return userService.findAll();
    }
    
    @HttpMethods.GET("/users/:id")
    public User getUser(@Param("id") int userId) {
        // Return user by ID
        return userService.findById(userId);
    }
    
    @HttpMethods.POST("/users")
    public User createUser(@BodyParam("user") User user) {
        // Create a new user
        return userService.create(user);
    }
    
    @HttpMethods.PUT("/users/:id")
    public User updateUser(@Param("id") int userId, @BodyParam("user") User user) {
        // Update existing user
        return userService.update(userId, user);
    }
    
    @HttpMethods.DELETE("/users/:id")
    public void deleteUser(@Param("id") int userId) {
        // Delete user
        userService.delete(userId);
    }
}
```

### 2. Register the Controller with Vert.x Router

``` java
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import com.gl.vertx.easyrouting.EasyRouting;

public class Application {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        
        // Create controller instance
        UserController userController = new UserController();
        
        // Setup routes using EasyRouting
        EasyRouting.setupHandlers(router, userController);
        
        // Start the server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8080);
    }
}
```

## Adding to Your Build

To add to your build: copy `com.gl.vertx.easyrouting` classes to your project and add
it to your classpath.

## License

The Vert.x-EasyRouting is licensed under the terms of the [MIT License](https://opensource.org/license/mit).
