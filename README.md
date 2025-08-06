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

To create handler methods in your controller classes, define usual Java methods that handle requests and produce response 
and annotate them with one of the HTTP method annotations:

- `@GET(path)` - For HTTP GET requests
- `@POST(path)` - For HTTP POST requests
- `@PUT(path)` - For HTTP PUT requests
- `@DELETE(path)` - For HTTP DELETE requests
- `@PATCH(path)` - For HTTP PATCH requests

### Annotate Methods

#### @Form

Use `@Form` annotation to handle form data:
```java
    @Form
    @GET("/loginForm")
    String loginForm(@Param("user") String user, @Param("password") String password) {
        ...
}
```

#### @ContentType
Use `@ContentType` annotation to set the response content type:
```java
    @GET("/text") @ContentType("text/plain")
    String getText() {
        return "Hello <b>World!</b>";
    }
```

#### @StatusCode
Use `@StatusCode` annotation to specify that a method  handles specific errors with codes:
```java
    @StatusCode(401)
    @GET("/unauthenticated")
    Result<?> unauthenticated(@OptionalParam("redirect") String redirect) {
        return new Result<>("You are not unauthenticated to access this: " + redirect, 401);
    }
```
#### @FileFromFolder
Use `@FileFromFolder` annotation to serve static files from the file system:
```java
    @GET("/*") @FileFromFolder("document")
    String get(@PathParam("path") String path) {
        return path;
    }
```

#### @FileFromResource
Use `@FileFromResource` annotation to serve static files from the classpath:
```java
    @GET("/*") @FileFromResource(UserAdminApplication.class)
    String get(@PathParam("path") String path) {
        return path;
    }
```
#### @NotNullResult
Use `@NotNullResult` annotation to return some response with text and code if the annotated method returns null:
```java
    @GET("/api/users/:id") @NotNullResult(value = "No user found", statusCode = 404)
    User getUser(@Param("id") String id) {
        return userService.getUser(id);
    }
```

### Annotate Method Parameters

#### @Param
Use `@Param` annotation to bind request parameters or form arguments to method arguments:

```java
   @HttpMethods.GET("/users/search")
    public List<User> searchUsers(@Param("name") String name, @Param("age") Integer age) {
        // Access query parameters like /users/search?name=John&age=30
    }
```

#### @OptionalParam
Use the `@OptionalParam` annotation to bind optional request parameters or form arguments to method arguments:
```java
   @HttpMethods.GET("/users/search")
   public List<User> searchUsers(@Param("name") String name, @OptionalParam("age") Integer age) {
       // Access query parameters like /users/search?name=John&age=30
   }
```

#### @BodyParam
Use the `@BodyParam` annotation to bind HTTP body content to a method argument:
```java
   @HttpMethods.POST("/users")
   public User createUser(@BodyParam("user") User user) {
       // Automatically converts JSON to User object
   }
```

#### @UploadsParam
Use the `@UploadsParam` annotation to bind a list of uploaded files to method arguments:
```java
@POST("/files/uploadFile")
public HandlerResult<String> uploadFiles(@Param("fileCount") int fileCount, @UploadsParam List<FileUpload> fileUploads) {
    return Result.saveFiles("files", fileUploads, "redirect:/");
}
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

To add to your build either:
- copy `com.gl.vertx.easyrouting` sources to your project 
- build the project and add the corresponding jar's from the _target_ folder to 
your class path or to your build tool dependencies.

## License

The Vert.x-EasyRouting is licensed under the terms of
the [MIT License](https://opensource.org/license/mit).
