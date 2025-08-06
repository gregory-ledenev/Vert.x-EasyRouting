package com.gl.vertx.easyrouting;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

import static com.gl.vertx.easyrouting.HttpMethods.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestApplication {
    static class TestApplicationImpl extends Application {

        public static final String JWT_PASSWORD = "veeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeery long password";

        @Deprecated
        void doNothing() {
        }

        @GET(value = "/*")
        String hello() {
            return "Hello from TestApplication!";
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

        @StatusCode(401)
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

        public TestApplicationImpl() {
            jwtAuth(JWT_PASSWORD, "/api/*");
        }

        public static void main(String[] args) {
            TestApplicationImpl app = new TestApplicationImpl()
                    .onStartCompletion(Application::waitForInput)
                    .handleShutdown()
                    .start(8080);
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
