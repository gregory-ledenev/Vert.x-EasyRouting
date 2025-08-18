# EasyRouting for Vert.x

EasyRouting is a lightweight, annotation-driven HTTP routing library designed
to build Vert.x web applications. It simplifies web development by letting you define
routes with Java annotations—no need to manage complex request or response
objects.

Inspired by JAX-RS and Spring Boot’s routing style, EasyRouting provides a
focused, minimal solution without the heaviness of a full framework. It’s ideal
for developers who want to build web apps quickly using just basic Java and HTTP
knowledge.

Key Highlights:
- Define routes and configuration using simple annotations on your Java methods.
- Automatic parameter binding and response handling.
- Use plain Java objects; no manual JSON serialization needed.
- Minimal setup; start by extending the `Application` class and calling `start()`.
- Designed for easy learning with no need to learn Vert.x to get started.

Get started in minutes and build clean, maintainable web apps with less 
boilerplate code. Here’s literally all it takes:

```java
class HelloWorld extends Application {
    @GET("/*")
    public String hello() {
        return "Hello World!";
    }

    public static void main(String[] args) {
        new HelloWorld().start();
    }
}
```
Making JSON-RPC application is as easy as:

```java
@Rpc
class TestApplication extends Application {
    
    public String hello() {
        return "Hello from JSON-RPC TestApplication!";
    }

    public static void main(String[] args) {
        TestApplication app = new TestApplication().start(8080);
    }
}
```

This library is ideal for rapid prototyping, testing, or learning the basics
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
- Automatic handling of blocking operations
- Redirect handling
- Builtin JWT authentication and role-based authorization
- Binding methods to HTTP error codes
- Ready-to use application class for prototyping and testing
- No code JSON-RPC 2.0 support
- Automatic scheme discovery for JSON-RPC applications and modules
- Easy implementation of custom data converters

## EasyRouting Application

There is a special `Application` class that allows building applications with easy
for production, prototyping and testing purposes without learning how to create 
a full Vert.x application:

1. Make your class extend `Application`.
2. Write regular methods for your app’s logic, add simple annotations for HTTP
   verbs and paths.
3. Use Java objects for parameters and return values - no dealing with JSON.
4. Optionally, mark method parameters to bind query/form stuff if you want.
5. Add a main method that creates your applications and calls `start()`.

```java
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

### Using Application for Tests 
You may use that application to simplify making your tests:

- add your test code to `onStartCompletion` handler
- call `handleCompletionHandlerFailure()` to propagate collected failures, like
  assertion errors, and to make your tests functional

```java
class TestApplication extends Application {
    @GET("/*")
    String hello() {
        return "Hello from TestApplication!";
    }

    public static void main(String[] args) {
        Application app = new TestApplication().
                onStartCompletion(application -> {
                // add your teststart code here
                }).
                start();

        app.handleCompletionHandlerFailure();
    }
}
```
### JWT and SSL Support

To run an `Application` with JWT authentication and SSL:

- use `jwtAuth(jwtSecret, paths)` method to enable JWT authentication with a
  secret key and path's to protected resources
- use `sslWithJks(jksKeyStorePath, jksKeyStorePassword)` or
  `sslWithPem(keyPath, certPath)` method to enable SSL
- call `start(port)` method with an SSL port number `443` by default for
  production or `8443` for development)

```java
new UserAdminApplication().
    jwtAuth(<JWT SECRET>, "/api/*").
    sslWithJks("keystore",<KEYSTORE PASSWORD>).
    start(443);
```
### JSON-RPC Support

EasyRoting provides an ability to add support of JSON-RPC 2.0 to any `Application` 
or `ApplicationMoudle`. The only thing you should do - add a `@Rpc` annotation. 
Properly annotated `Application` or `ApplicationMoudle` classes automatically handle 
JSON-RPC request parsing, response formatting, and error handling, allowing you 
to focus on implementing the actual method logic.

To add support for JSON-RPC, you need:

1. Subclass the `Application` or `ApplicationMoudle` class.
2. Annotate the class with `@Rpc` with an endpoint path to indicate that it is
   an RPC module.
3. Implement all required methods that can be accessible via JSON-RPC. Note:
   that methods don't require `@POST` annotations as the endpoint for entire
   application module is clearly defined by the `@Rpc` annotation.
4. Optionally allow scheme discovery and use `@Description` annotations to add a 
   description for the module and the methods to make a generated scheme be 
   self-descriptive.

```java
import com.gl.vertx.easyrouting.annotations.Description;

@Rpc("/api/jsonrpc/test", provideScheme = true)
@Description("Sample service that provides simple test operations")
public class JsonRpcTestApplication extends Application {
    @Description("Multiplies two integers")
    public int multiply(@Param("a") int a, @Param("b") int b) {
        return a * b;
    }
}
```

#### JSON-RPC Scheme Discovery
You can request a scheme for the JSON-RPC app or module by sending a simple `GET`
request to the endpoint. By default, schema discovery is turned off for
security reasons, but you can enable it by specifying the `provideScheme = true`
for an `@Rpc` annotation
```java
@Rpc(path = "/api/jsonrpc/test" , provideScheme = true)
```
Sample scheme output can be:
```java
java.lang.String;
java.util.List;
java.util.Map;

/**
 * Sample service that provides simple test operations
 */
public interface Service {
    /**
     * Multiplies two integers with an optional third integer
     */
    int multiply(int a, int b, int c);
    int[] intArray(int a, int b);
    List intList(int a, int b);
    Map intMap(int a, int b);
    void voidMethod(int a, int b);
    String multiplyAsString(int a, int b);
}
```
#### Controlling Which Methods are Exported

`@Rpc` annotation allows you to define a policy which methods are exported via
JSON-RPC. You can set that policy via the`exportPolicy` parameter of the `@Rpc`
annotation. The default policy is `RpcExportPolicy.ALL`, which means that all
public methods are exported. You can also set `RpcExportPolicy.NONE` to disable
exporting any methods.

You can annotate particular methods with `@RpcInclude` to explicitly export
them, or with `@RpcExclude` to explicitly exclude them from being exported. This
allows you to have fine-grained control over which methods are available via
JSON-RPC.

#### Limitations
The following are limitations:
- Batch calling is not supported.
- Positional parameters are not supported

### Application Modules and Controllers

If your application is small, you can supply all the handler methods inside the `Application` itself. Otherwise, you can
use Application Modules or Controllers to organize and modularize application functionality by grouping related endpoint
handlers together. Also, you can define all the converters in a dedicated module or controller to keep them in one place 
and to allow easy reuse.

You can create an Application Module by extending the `ApplicationModule` class, adding all required and properly
annotated handler methods, and then registering the module with the Application using the `Application.module(...)`
method. You can override `started()` and `stopped()` methods to perform any initialization or cleanup tasks when the
module is started or stopped.

```java
static class UserApplicationModule extends ApplicationModule<UserAdminApplication> {
    @GET("/api/users")
    public List<User> getUsers() {
        return application.userService.getUsers();
    }
}
...
public static void main(String[] args) {
    new UserAdminApplication(new LoginService(), new UserService()).
            module(new UserApplicationModule()).
            jwtAuth(JWT_SECRET, "/api/*").
            start();
}
```

If you don't want or can't use `ApplicationModule`, you can use any class as a Controller. You can create a Controller
using any class, adding all required and properly annotated handler methods, and then registering the module with the
Application using the `Application.controller(...)` method.

```java
static class UserApplicationController {
    @GET("/api/users")
    public List<User> getUsers() {
        return application.userService.getUsers();
    }
}
...
public static void main(String[] args) {
    new UserAdminApplication(new LoginService(), new UserService()).
            controller(new UserApplicationController()).
            jwtAuth(JWT_SECRET, "/api/*").
            start();
}
```

## Using With Vert.x Router

If `Application` is too simple for your needs, or if you want to use and mix
EasyRouting with your exiting Vert.x code — you can use EasyRouting with Vert.x
Router.

### 1. Create a Controller Class

```java
public class Controller {
    @GET("/*")
    String hello() {
        return "Hello from TestApplication!";
    }
}
```

### 2. Register the Controller with Vert.x Router

```java
public class Application {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        
        // Enable body handling for parsing request bodies
        router.route().handler(BodyHandler.create());
        
        // Create controller instance
        UserController userController = new UserController();
        
        // Setup controller using EasyRouting
        EasyRouting.setupController(router, userController);
        
        // Start the server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8080);
    }
}
```

## Adding and Configuring Handler Methods

To create handler methods in your controller classes, define usual Java methods
that handle requests and produce response and annotate them with one of the HTTP
method annotations:

- `@GET(path)` - For HTTP GET requests
- `@POST(path)` - For HTTP POST requests
- `@PUT(path)` - For HTTP PUT requests
- `@DELETE(path)` - For HTTP DELETE requests
- `@PATCH(path)` - For HTTP PATCH requests

### Annotating Methods

#### @GET, @POST, @PUT, @DELETE, @PATCH

Use `@GET`, `@POST`, `@PUT`, `@DELETE`, or `@PATCH` annotations to define
handler methods for specific HTTP methods. The `value` parameter specifies the
URL path for the handler. You may use the the optional `requiredRoles` parameter
to specify required roles for the method, which will be checked during JWT
authentication. If nor roles are specified, the method will be accessible to all
authenticated users.

```java
@DELETE(value = "/api/users/:id", requiredRoles = {"admin"})
boolean deleteUser(@Param("id") String id) {
    return userService.deleteUser(id);
}
```

#### @Blocking

Use `@Blocking` annotation to mark methods that should be executed as blocking
operations. When applied to a method, it indicates that the method's execution
will be automatically processed on a worker thread rather than the event loop,
preventing the event loop from being blocked. Use this annotation to mark
operations that may take a long time and to safely add any blocking code to
such methods.

```java
@Blocking
@GET(value = "/blockingHello")
String blockingHello() {
    try {
        Thread.sleep(5000); // simulate blocking operation
    } catch (InterruptedException e) {
    }
    return "Blocking hello from TestApplication!";
}
```

#### @Form

Use `@Form` annotation to mark methods that should handle form data:

```java
@Form
@POST("/loginForm")
String loginForm(@Param("user") String user, @Param("password") String password) {
        ...
}
```

#### @ContentType

Use `@ContentType` annotation to set the response content type:

```java
@GET("/text")
@ContentType("text/plain")
String getText() {
    return "Hello <b>World!</b>";
}
```

#### @HandleStatusCode

Use `@HandleStatusCode` annotation to specify that a method handles specific errors
with codes:

```java
@HandleStatusCode(401)
@GET("/unauthenticated")
Result<?> unauthenticated(@OptionalParam("redirect") String redirect) {
    return new Result<>("You are not unauthenticated to access this: " + redirect, 401);
}
```

#### @FileFromFolder

Use `@FileFromFolder` annotation to serve static files from the file system:

```java
@GET("/*")
@FileFromFolder("document")
String get(@PathParam("path") String path) {
    return path;
}
```

#### @FileFromResource

Use `@FileFromResource` annotation to serve static files from the classpath:

```java
@GET("/*")
@FileFromResource(UserAdminApplication.class)
String get(@PathParam("path") String path) {
    return path;
}
```

#### @NotNullResult

Use `@NotNullResult` annotation to return some response with text and code if
the annotated method returns null:

```java
@GET("/api/users/:id")
@NotNullResult(value = "No user found", statusCode = 404)
User getUser(@Param("id") String id) {
    return userService.getUser(id);
}
```

#### @HttpHeaders.Header

Use `@HttpHeaders.Header` annotation to set HTTP headers in the response. You can use it multiple times to set multiple
headers:

```java
@Header("content-type: text/plain")
@Header("header1: value1")
@Header("header2: value2")
@GET("/multipleHeaders")
String getMultipleHeaders() {
    return "Multiple Headers";
}
```

### Annotating Method Parameters

EasyRouting automatically tries to bind request parameters, form arguments, and
body content to method arguments. It can
be done in several ways:

- direct binding of parameters by their names. To make this work, your project
  must be compiled with the `-parameters` option. Otherwise, parameters will have generic names like `arg0`, `arg1`,
  etc. In that case the warning will be logged.
- using `@Param` annotation to bind request parameters or form arguments to
  method arguments
- using `@OptionalParam` annotation to bind optional request parameters or form
  arguments to method arguments
- using `@BodyParam` annotation to bind HTTP body content to a method argument
- using `@UploadsParam` annotation to bind a list of uploaded files to method
  arguments

#### @Param

Use `@Param` annotation to bind request parameters or form arguments to method
parameters:

```java
@HttpMethods.GET("/users/search")
public List<User> searchUsers(@Param("name") String name, @Param("age") Integer age) {
    // Access query parameters like /users/search?name=John&age=30
}
```

#### @OptionalParam

Use the `@OptionalParam` annotation to bind optional request parameters or form
arguments to method parameters:

```java
@HttpMethods.GET("/users/search")
public List<User> searchUsers(@Param("name") String name, @OptionalParam("age") Integer age) {
    // Access query parameters like /users/search?name=John&age=30
}
```

#### @BodyParam

Use the `@BodyParam` annotation to bind HTTP body content to a method parameter:

```java
@HttpMethods.POST("/users")
public User createUser(@BodyParam("user") User user) {
    // Automatically converts JSON to User object
}
```

#### @UploadsParam

Use the `@UploadsParam` annotation to bind a list of uploaded files to a method
parameter. Note: the method parameter must be a `List<FileUpload>`.

```java
@POST("/files/uploadFile")
public HandlerResult<String> uploadFiles(@Param("fileCount") int fileCount, @UploadsParam List<FileUpload> fileUploads) {
    return Result.saveFiles("files", fileUploads, "redirect:/");
}
```

### Returning Results

EasyRouting simplifies handling method results by:

- Automatically converting Java objects to JSON when needed.
- Determining and setting the appropriate content type for the response.

If you need more control and want to return a custom response, you can return an instance of the Result class. This
class lets you specify custom headers, status codes, and more. It also provides convenient factory methods for common
scenarios. For example, to save uploaded files to the file system and then return a redirect response, your upload
handling method can return: `Result.saveFiles("files", fileUploads, "redirect:/")`.

If you need custom processing, possibly involving direct access to context - you can create a {@code Result} with a
handler and return it.

```java
@GET("/testCustomHandler")
Result<String> testCustomHandler() {
    return new Result<>("Hello").handler((result, ctx) -> result.setResult(result.getResult() + " World!"));
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

## Data Conversion

EasyRouting provides a powerful custom data conversion system that allows you to define how objects are converted
between different content types. This enables you to handle custom data formats and serialization beyond the default
JSON support. This is done by defining methods annotated with `@ConvertsTo` and `@ConvertsFrom` that convert Java
objects to and from specific content types for response output. The conversion system automatically detects and applies the
appropriate converters based on the method return types, parameter types, and specified content types, making data
format handling seamless and flexible.

### @ConvertsTo Annotation

`@ConvertsTo` annotation used to mark methods that converts the input value into a result according to
a specified content type. Such converters will be used to convert result values. Input type is explicitly defined by the
converter method's single parameter.
The contract for conversion methods:
- Must be a public static method.
- Must accept exactly one parameter of any type that defines a type of the objects to convert from.
- Must return a non-void type compatible with the specified content type.
- Must not throw checked exceptions.

For example, the following converter converts objects of the `User` type to objects that correspond to the _"text/plain"_
content type:
```java
@ConvertsTo("text/user-string")
public String convertUserToText(User user) {
    return user.toString();
}
```

### @ConvertsFrom Annotation

`@ConvertsFrom` annotation used to mark methods that convert input data from specific content types into target objects.
Such converters will be used to convert body values to appropriate objects. Output type is explicitly defined by the
converter method's return type.

The contract for conversion methods:
- Must be a public static method.
- Must accept exactly one parameter: input content that corresponds to the content type defined by annotation.
- Must be non-void, where the return type defines the object's type to convert to.
- Must not throw checked exceptions.

For example, the following converter converts objects that correspond to the _"text/user-string"_ content type to objects
of the `User` type:
```java
@ConvertsFrom("text/user-string")
public static User convertUserFromText(String text) {
    // Convert text to User object
    return User.of(text);
}
```

NOTE: if you need to convert multiple elements - you must always use arrays NOT
lists as converter methods' parameters. Framework automatically uses that
converters for conversion lists values too.

## Error Handling

EasyRouting supports error handling by binding HTTP error codes to handler
methods using `@HandlesStatusCode` annotations:

```java
@HandlesStatusCode(401)
@GET("/loginForm")
public String loginForm(@OptionalParam("redirect") String redirect) {
    ...
}
```

## Sample Test Applications

There are several sample applications in the
_src/test/java/com/gl/vertx/easyrouting_
folder that demonstrate various features of the EasyRouting library:

- **FilesApplication**: demonstrates file upload and download handling
- **UserAdminApplication**: demonstrates sample user management application with
  handling static pages, implementing API, JWT authentication and role-based
  authorization, and SSL support
- **TestApplication**: demonstrates using EasyRouting Application class for
  prototyping and testing

## Adding to Your Build

To add to your build either:

- copy `com.gl.vertx.easyrouting` sources to your project and compile them.
- build the project and add the corresponding jar's from the _target_ folder to
  your class path or to your build tool dependencies.

## License

The Vert.x-EasyRouting is licensed under the terms of
the [MIT License](https://opensource.org/license/mit).
