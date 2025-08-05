# EasyRouting for Vert.x

EasyRouting is an experimental lightweight annotation-based HTTP routing library
for Vert.x web applications. It simplifies route configuration by allowing
developers to define routes using annotations and automatically handles
parameter binding and response processing. While inspired by SpringBoot's
annotation-based routing patterns, it maintains a focused, lightweight
implementation without attempting to replicate SpringBoot's entire feature set.

EasyRouting enables you to build web applications effortlessly, even without
prior knowledge of Vert.x. To get started, you only need a basic understanding
of HTTP and general knowledge of Java.

With just a single page of code, EasyRouting serves web pages and APIs,
automatically converts incoming and outgoing data, handles errors, supports JWT
authentication, and enforces role-based access control.

This library is ideal for rapidly prototyping, testing, or learning the basics
of web application development. It offers a minimal, annotation-driven API that
simplifies the development process while still providing powerful features.

## Key Features

- No Vert.x knowledge required to build web applications
- Minimalistic and easy-to-use API
- Annotation-based handler definitions for all common HTTP methods (GET, POST,
  PUT, DELETE, PATCH)
- Automatic parameter binding from request parameter, body, and form arguments
- Support for optional parameters
- Form handling
- File upload/download handling
- JSON objects conversion
- Automatic incoming and outgoing data conversion
- Automatic response processing
- Redirect handling
- Builtin JWT authentication and role-based authorization
- Binding methods to HTTP error codes
- Ready-to use application class for prototyping and testing

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
        
        // Enable body handling for parsing request bodies
        router.route().handler(BodyHandler.create());
        
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

## Adding and Configuring Handler Methods

To create handler methods in your controller classes:

1. **Create a method with an HTTP method annotation** - Use one of the following
   annotations to define the HTTP method and path:
    - `@GET(path)` - For HTTP GET requests
    - `@POST(path)` - For HTTP POST requests
    - `@PUT(path)` - For HTTP PUT requests
    - `@DELETE(path)` - For HTTP DELETE requests
    - `@PATCH(path)` - For HTTP PATCH requests

2. **Define path parameters** - Use `:paramName` syntax in your path to define
   path parameters:
   ```java
   @HttpMethods.GET("/users/:id")
   public User getUser(@Param("id") int userId) {
       // ...
   }
   ```

3. **Bind request parameters** - Use the `@Param` annotation to bind URL/query
   parameters to method arguments:
   ```java
   @HttpMethods.GET("/users/search")
   public List<User> searchUsers(@Param("name") String name, @Param("age") Integer age) {
       // Access query parameters like /users/search?name=John&age=30
   }
   ```
4. **Bind optional request parameters** - Use the `@OptionalParam` annotation to
   bind optional URL/query parameters to method arguments:
   ```java
   @HttpMethods.GET("/users/search")
   public List<User> searchUsers(@Param("name") String name, @OptionalParam("age") Integer age) {
       // Access query parameters like /users/search?name=John&age=30
   }
   ```

5. **Access request body** - Use the `@BodyParam` annotation to bind JSON body
   content to method arguments:
   ```java
   @HttpMethods.POST("/users")
   public User createUser(@BodyParam("user") User user) {
       // Automatically converts JSON to User object
   }
   ```

6. **Handle file uploads** - Use the `@UploadsParam` annotation to bind a list
   of uploaded files to method arguments:

```java

@POST("/files/uploadFile")
public HandlerResult<String> uploadFiles(@Param("fileCount") int fileCount, @UploadsParam List<FileUpload> fileUploads) {
    return HandlerResult.saveFiles("files", fileUploads, "redirect:/");
}
```

7. **Handle form submissions** - Use the `@Form` annotation to bind form
   fields to method arguments:

```java

@Form
@GET("/loginForm")
String loginForm(@Param("user") String user, @Param("password") String password) {
    ...
}
```

8. **Return values** - Handler methods can return various types which are
   automatically processed:
    - Java objects (automatically converted to JSON)
    - Lists/arrays (automatically converted to JSON arrays)
    - Primitives or Strings (sent as response)
    - `void` (for operations without explicit returns)

9. **Register your controllers** - After defining your handler methods, register
   the controller instance with EasyRouting:
   ```java
   EasyRouting.setupHandlers(router, controllerInstance);
   ```

## Using EasyRouting Application for Testing and Prototyping

There is a special Application class that allows building minimal applications
for prototyping and testing purposes
without having to create a full Vert.x application.

``` java
class TestApplication extends Application {
    @GET("/*")
    String hello() {
        return "Hello from TestApplication!";
    }

    public static void main(String[] args) {
        TestApplication app = new TestApplication().start(8080);
    }
}
```

You may use that application to simplify making your tests:

``` java
class TestApplication extends Application {
    @GET("/*")
    String hello() {
        return "Hello from TestApplication!";
    }

    public static void main(String[] args) {
        TestApplication app = new TestApplication();
        app.start(8080, app -> {
            try {
                // add your testing code here
            } finally {
                app.stop();
            }               
        });
        app.handleCompletionHandlerFailure();
    }
}
```

## JWT Support

EasyRouting supports JWT authentication and role-based authorization. To apply
it to a router - use
`EasyRouting.applyJwtAuth()` method:

```java
EasyRouting.applyJWTAuth(vertx, router, "/api/",<SECRET KEY>);
```

You may authorize certain methods to be accessed only by certain roles by
specifying them as `requiredRoles` in the
`@GET`, `@POST` etc. annotations:

```java

@HttpMethods.GET("/users/:id", requiredRoles = {"admin"})
public User getUser(@Param("id") int userId) {
    // ...
}
```

## Error Handling

EasyRouting supports error handling by binding HTTP error codes to handler
methods using `@StatusCode` annotations:

```java

@StatusCode(401)
@GET("/loginForm")
public String loginForm(@OptionalParam("redirect") String redirect) {
    ...
}
```

## Adding to Your Build

To add to your build: copy `com.gl.vertx.easyrouting` classes to your Vert.x
project.

## License

The Vert.x-EasyRouting is licensed under the terms of
the [MIT License](https://opensource.org/license/mit).
