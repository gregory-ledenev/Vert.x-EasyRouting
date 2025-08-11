package com.gl.vertx.easyrouting;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

import static com.gl.vertx.easyrouting.HttpMethods.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestApplication {
    static class TestConverters extends ApplicationModule<TestApplicationImpl> {
        @ConvertsTo("text/user-string")
        public static String convertUserToString(User user) {
            return user.toString();
        }

        @ConvertsFrom("text/user-string")
        public static User convertUserFromString(String content) {
            return User.of(content);
        }

        @ConvertsTo("text/xml")
        public static String convertUserToXml(User user) {
            return user.toString();
        }

        @ConvertsFrom("text/xml")
        public static User convertUserFromXml(String content) {
            return User.of(content);
        }
    }

    static class TestApplicationImpl extends Application {

        public static final String JWT_PASSWORD = "veeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeery long password";

        @Deprecated
        void doNothing() {
        }

        @GET(value = "/*")
        String hello() {
            return "Hello from TestApplication!";
        }

        @Blocking
        @GET(value = "/blockingHello")
        String blockingHello() {
            try {
                Thread.sleep(5000); // simulate a long-running task
            } catch (InterruptedException e) {
                // do nothing
            }
            return "Blocking hello from TestApplication!";
        }

        @Form
        @POST(value = "/login")
        String login(@Param("user") String user, @Param("password") String password, @OptionalParam(value = "role") String role) {
            return JWTUtil.generateToken(getVertx(), user, Arrays.asList(role.split(",")), JWT_PASSWORD);
        }

        @Form
        @POST(value = "/loginNotAnnotated")
        String loginNotAnnotated(String user, String password, String role) {
            return JWTUtil.generateToken(getVertx(), user, Arrays.asList(role.split(",")), JWT_PASSWORD);
        }

        @HandlesStatusCode(401)
        @GET(value = "/loginForm")
        String loginForm(@OptionalParam("redirect") String redirect) {
            return "Login Form - redirect back to: " + redirect;
        }

        @GET(value = "/api/*")
        String api() {
            return "Hello protected API!";
        }

        @GET(value = "/api/user/*", requiredRoles = {"user", "admin"})
        String apiUser() {
            return "Hello protected API (user)!";
        }

        @GET(value = "/api/admin/*", requiredRoles = {"admin"})
        String apiAdmin() {
            return "Hello protected API (admin)!";
        }

        @GET(value = "/concatenate")
        String concatenate(@Param("str1") String str1, @Param("str2") String str2, @OptionalParam("str3") String str3) {
            return str1 + str2 + (str3 != null && str3.isEmpty() ? "" : str3);
        }

        @HttpHeader("content-type: text/plain")
        @HttpHeader("header1: Value1")
        @HttpHeader("header2: Value2")
        @GET("/multipleHeaders")
        String getMultipleHeaders() {
            return "Multiple Headers";
        }

        @GET("/testCustomHandler")
        Result<String> testCustomHandler() {
            return new Result<>("Hello").handler((result, ctx) ->
                    result.setResult(result.getResult() + " World!"));
        }

        @ContentType("text/user-string")
        @GET("/testConversionTo")
        User testConversionTo() {
            return new User("John Doe", "john.doe@gmail.com");
        }

        @ContentType("text/user-string")
        @POST("/testConversionFrom")
        User testConversionFrom(@BodyParam("user") User user) {
            return user;
        }

        public TestApplicationImpl() {
            jwtAuth(JWT_PASSWORD, "/api/*");
        }

        public static void main(String[] args) {
            TestApplicationImpl app = new TestApplicationImpl().
                    module(new TestConverters()).
                    onStartCompletion(application -> {
                        System.out.println(application.getAnnotatedConverters().toString(false));
                        application.waitForInput();
                    }).
                    handleShutdown().
                    start(8080);
        }
    }

    record User(String name, String email) {
        public static User of(String content) {
            String content2 = content.substring(content.indexOf("[") + 1, content.indexOf("]"));
            String[] parts = content2.split(",\\s*", 2);
            return new User(parts[0].substring(parts[0].indexOf("=") + 1), parts[1].substring(parts[1].indexOf("=") + 1));
        }
    }

    @Test
    void testApplication() throws Throwable {
        Application app = new TestApplicationImpl().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/"))
                            .GET()
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("Hello from TestApplication!", response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testAnnotatedConvertersDiscovery() throws Throwable {
        Application app = new TestApplicationImpl().
                module(new TestConverters()).
                onStartCompletion(application -> {
                    try {
                        assertEquals("""
                                {
                                  "text/user-string"->com.gl.vertx.easyrouting.TestApplication$User = java.lang.String com.gl.vertx.easyrouting.TestApplication$TestConverters.convertUserToString(com.gl.vertx.easyrouting.TestApplication$User)
                                  "text/user-string"<-com.gl.vertx.easyrouting.TestApplication$User = com.gl.vertx.easyrouting.TestApplication$User com.gl.vertx.easyrouting.TestApplication$TestConverters.convertUserFromString(java.lang.String)
                                  "text/xml"->com.gl.vertx.easyrouting.TestApplication$User = java.lang.String com.gl.vertx.easyrouting.TestApplication$TestConverters.convertUserToXml(com.gl.vertx.easyrouting.TestApplication$User)
                                  "text/xml"<-com.gl.vertx.easyrouting.TestApplication$User = com.gl.vertx.easyrouting.TestApplication$User com.gl.vertx.easyrouting.TestApplication$TestConverters.convertUserFromXml(java.lang.String)
                                }""", application.getAnnotatedConverters().toString());
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testCustomHandler() throws Throwable {
        Application app = new TestApplicationImpl().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/testCustomHandler"))
                            .GET()
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("Hello World!", response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testConversionTo() throws Throwable {
        TestApplicationImpl app = new TestApplicationImpl().
                module(new TestConverters()).
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/testConversionTo"))
                            .GET()
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("text/user-string", response.headers().map().get("content-type").get(0));
                        assertEquals("User[name=John Doe, email=john.doe@gmail.com]", response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testConversionFrom() throws Throwable {
        TestApplicationImpl app = new TestApplicationImpl().
                module(new TestConverters()).
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/testConversionFrom"))
                            .header("Content-Type", "text/user-string")
                            .POST(HttpRequest.BodyPublishers.ofString("User[name=John Doe, email=john.doe@gmail.com]"))
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("text/user-string", response.headers().map().get("content-type").get(0));
                        assertEquals("User[name=John Doe, email=john.doe@gmail.com]", response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testMultipleHeaders() throws Throwable {
        Application app = new TestApplicationImpl().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/multipleHeaders"))
                            .GET()
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals(List.of("text/plain"), response.headers().map().get("content-type"));
                        assertEquals(List.of("Value1"), response.headers().map().get("header1"));
                        assertEquals(List.of("Value2"), response.headers().map().get("header2"));
                        assertEquals("Multiple Headers", response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testBlocking() throws Throwable {
        Application app = new TestApplicationImpl().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/blockingHello"))
                            .GET()
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("Blocking hello from TestApplication!", response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testOptionalParam() throws Throwable {
        Application app = new TestApplicationImpl().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();


                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:8080/concatenate?str1=Hello%20&str2=World&str3=!"))
                                .GET()
                                .build();

                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("Hello World!", response.body());

                        request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:8080/concatenate?str1=Hello%20&str2=World"))
                                .GET()
                                .build();

                        response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("Hello World", response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    static final String JWT_TOKEN_USER_ADMIN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0VXNlciIsInJvbGVzIjpbInVzZXIiLCJhZG1pbiJdLCJpYXQiOjE3NTQyNTUwMDN9.VgvXLusig-wC447NHetSonDfP60qlYI7yjFGvqvOqfo";
    static final String JWT_TOKEN_USER = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0VXNlciIsInJvbGVzIjpbInVzZXIiXSwiaWF0IjoxNzU0MjU1MDUwfQ.zcTUXFJxHnWSVdnU306tl4gKJZUlhujW5kPS1njjd4M";

    @Test
    void testFormSubmit() throws Throwable {
        Application app = new TestApplicationImpl().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/login?role=user"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString("user=testUser&password=testPass"))
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        System.out.println(response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testFormSubmitNotAnnotated() throws Throwable {
        Application app = new TestApplicationImpl().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/loginNotAnnotated?role=user"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString("user=testUser&password=testPass"))
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        System.out.println(response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testUnauthenticated() throws Throwable {
        Application app = new TestApplicationImpl().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/api/"))
                            .GET()
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(302, response.statusCode()); // redirect /loginForm
                        System.out.println(response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                })
                .start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testAuthorized() throws Throwable {
        Application app = new TestApplicationImpl().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN)
                            .uri(URI.create("http://localhost:8080/api/"))
                            .GET()
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("Hello protected API!", response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                })
                .start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testAuthorizedForUserAndAdmin() throws Throwable {
        Application app = new TestApplicationImpl().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN)
                            .uri(URI.create("http://localhost:8080/api/user/"))
                            .GET()
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("Hello protected API (user)!", response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                }).
                start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testAuthorizedForAdmin() throws Throwable {
        Application app = new TestApplicationImpl().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN)
                            .uri(URI.create("http://localhost:8080/api/admin/"))
                            .GET()
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, response.statusCode());
                        assertEquals("Hello protected API (admin)!", response.body());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                })
                .start(8080);

        app.handleCompletionHandlerFailure();
    }

    @Test
    void testUnauthorizedForAdmin() throws Throwable {
        Application app = new TestApplicationImpl().
                onStartCompletion(application -> {

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .header("Authorization", "Bearer " + JWT_TOKEN_USER)
                            .uri(URI.create("http://localhost:8080/api/admin/"))
                            .GET()
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(403, response.statusCode());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        application.stop();
                    }
                })
                .start(8080);

        app.handleCompletionHandlerFailure();
    }

    static class HelloWorld extends Application {
        @GET(value = "/*")
        String hello() {
            return "Hello World!";
        }

        public static void main(String[] args) {
            new HelloWorld().start();
        }
    }
}
