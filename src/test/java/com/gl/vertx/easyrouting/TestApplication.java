package com.gl.vertx.easyrouting;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.gl.vertx.easyrouting.HttpMethods.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestApplication {
    static class TestApplicationImpl extends Application {
        @GET("/*")
        String hello() {
            return "Hello from TestApplication!";
        }

        public static void main(String[] args) {
            TestApplicationImpl app = new TestApplicationImpl();
            app.start(8080, Application::waitForInput);
        }
    }

    @Test
    void testApplication() {
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
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                application.stop();
            }
        });
    }
}
