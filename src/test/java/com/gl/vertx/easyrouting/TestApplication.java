package com.gl.vertx.easyrouting;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.gl.vertx.easyrouting.HttpMethods.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestApplication {
    static class TestApplicationImpl extends Application {

        public static final String JWT_PASSWORD = "veeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeery long password";

        @GET("/*")
        String hello() {
            return "Hello from TestApplication!";
        }

        @Form
        @POST("/login")
        String login(@Param("user") String user, @Param("password") String password, @OptionalParam(value = "role") String role) {
            return JWTUtil.generateToken(getVertx(), user, Arrays.asList(role.split(",")), JWT_PASSWORD);
        }

        @StatusCode(401)
        @GET("/loginForm")
        String loginForm(@OptionalParam("redirect") String redirect) {
            return "Login Form - redirect back to: " + redirect;
        }

        @GET("/api/*")
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
            super(JWT_PASSWORD, "/api/*");
        }

        public static void main(String[] args) {
            TestApplicationImpl app = new TestApplicationImpl();
            app.start(8080, Application::waitForInput);
        }
    }

    @Test
    void testApplication() throws Throwable {
        final List<Throwable> exceptions = new ArrayList<>();

        TestApplicationImpl app = new TestApplicationImpl();
        app.start(8080, application -> {

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
                exceptions.add(e);
            } finally {
                application.stop();
            }
        },  (application, throwable) -> exceptions.add(throwable));
        if (exceptions.size() > 0) {
            throw exceptions.get(0);
        }
    }

    static final String JWT_TOKEN_USER_ADMIN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0VXNlciIsInJvbGVzIjpbInVzZXIiLCJhZG1pbiJdLCJpYXQiOjE3NTQyNTUwMDN9.VgvXLusig-wC447NHetSonDfP60qlYI7yjFGvqvOqfo";
    static final String JWT_TOKEN_USER = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0VXNlciIsInJvbGVzIjpbInVzZXIiXSwiaWF0IjoxNzU0MjU1MDUwfQ.zcTUXFJxHnWSVdnU306tl4gKJZUlhujW5kPS1njjd4M";

    @Test
    void testFormSubmit() throws Throwable {
        final List<Throwable> exceptions = new ArrayList<>();

        TestApplicationImpl app = new TestApplicationImpl();
        app.start(8080, application -> {

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
                exceptions.add(e);
            } finally {
                application.stop();
            }
        },  (application, throwable) -> exceptions.add(throwable));

        if (exceptions.size() > 0) {
            throw exceptions.get(0);
        }
    }

    @Test
    void testUnauthenticated() throws Throwable {
        final List<Throwable> exceptions = new ArrayList<>();

        TestApplicationImpl app = new TestApplicationImpl();
        app.start(8080, application -> {

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
                exceptions.add(e);
            } finally {
                application.stop();
            }
        },  (application, throwable) -> exceptions.add(throwable));
        if (exceptions.size() > 0) {
            throw exceptions.get(0);
        }
    }

    @Test
    void testAuthorized() throws Throwable {
        final List<Throwable> exceptions = new ArrayList<>();

        TestApplicationImpl app = new TestApplicationImpl();
        app.start(8080, application -> {

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
                exceptions.add(e);
            } finally {
                application.stop();
            }
        },  (application, throwable) -> exceptions.add(throwable));
        if (exceptions.size() > 0) {
            throw exceptions.get(0);
        }
    }

    @Test
    void testAuthorizedForUserAndAdmin() throws Throwable {
        final List<Throwable> exceptions = new ArrayList<>();

        TestApplicationImpl app = new TestApplicationImpl();
        app.start(8080, application -> {

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
                exceptions.add(e);
            } finally {
                application.stop();
            }
        },  (application, throwable) -> exceptions.add(throwable));
        if (exceptions.size() > 0) {
            throw exceptions.get(0);
        }
    }

    @Test
    void testAuthorizedForAdmin() throws Throwable {
        final List<Throwable> exceptions = new ArrayList<>();

        TestApplicationImpl app = new TestApplicationImpl();
        app.start(8080, application -> {

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
                exceptions.add(e);
            } finally {
                application.stop();
            }
        },  (application, throwable) -> exceptions.add(throwable));
        if (exceptions.size() > 0) {
            throw exceptions.get(0);
        }
    }

    @Test
    void testUnauthorizedForAdmin() throws Throwable {
        final List<Throwable> exceptions = new ArrayList<>();

        TestApplicationImpl app = new TestApplicationImpl();
        app.start(8080, application -> {

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
                exceptions.add(e);
            } finally {
                application.stop();
            }
        },  (application, throwable) -> exceptions.add(throwable));
        if (exceptions.size() > 0) {
            throw exceptions.get(0);
        }
    }
}
