package com.gl.vertx.easyrouting;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.HttpException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.io.File;
import java.util.Arrays;
import java.util.Map;

import static com.gl.vertx.easyrouting.HttpMethods.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestEasyRouting {

    @Test
    void testAccessToVerticle() {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new TestVerticle())
                .onComplete(deployment -> {
                    if (deployment.succeeded()) {
                        System.out.println("Test verticle deployed successfully.");
                        HttpClient client = HttpClient.newHttpClient();
//                        testPath(client, "/");
//                        testPath(client, "/?name=John");
//                        testPath(client, "/greeting/?name=Woo%20Hoo!");
//                        testPath(client, "/files/serveFile?fileName=nntws-overview.jpeg");
                    } else {
                        System.err.println("Failed to deploy test verticle: " + deployment.cause());
                    }
                });
        try {
            Thread.sleep(500000);
        } catch (InterruptedException aE) {
        }
    }

    private static void testPath(HttpClient client, String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://" + HOST + ":" + PORT + path));
        HttpRequest request = builder.GET().build();

        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(MessageFormat.format("Response for path - \"{0}\" {1}", path, response.body()));
        } catch (Exception e) {
            fail(e);
        }
    }

    public static final int PORT = 8080;
    public static final String HOST = "localhost";

    static class TestVerticle extends AbstractVerticle {

        public static final String HTML = """
                <html>
                <body>
                <div>
                    <form method="POST" enctype="multipart/form-data" action="/files/uploadFile?filecount=1">
                        <table>
                            <tr><td>File to upload:</td><td><input type="file" name="file" /></td></tr>
                            <tr>
                                <td><input type="submit" value="Upload"/></td>
                            </tr>
                        </table>
                    </form>
                </div>
                <div>
                <ul>
                {0}
                </ul>
                </dive>
                </body>
                </html>
                """;

        @Override
        public void start(Promise<Void> startPromise) {
            Router router = Router.router(vertx);
            router.route().handler(BodyHandler.create());
            createFailureHandler(router);
            EasyRouting.setupHandlers(router, this);
            createHttpServer(startPromise, router);
        }

        @GET("/")
        public HandlerResult<String> get() {
            File folder = new File("files");
            StringBuilder fileList = new StringBuilder();
            if (folder.exists() && folder.isDirectory()) {
                Arrays.stream(folder.listFiles())
                        .forEach(file -> fileList.append(MessageFormat.
                                format("<li><a href=\"/files/serveFile?fileName={0}\">{0}</a> ({1} bytes)</li>\n",
                                        file.getName(), file.length())));
            }
            return HandlerResult.html(MessageFormat.format(HTML, fileList.toString()));
        }

        @GET("/files/serveFile")
        public Path serveFile(@Param("fileName") String fileName) {
            return Path.of("files", fileName);
        }

        @POST("/files/uploadFile")
        public HandlerResult<String> uploadFiles(@Param("fileCount") int fileCount, @UploadsParam List<FileUpload> fileUploads) {
            return HandlerResult.saveFiles("files", fileUploads, "redirect:/");
        }

        @GET("/*")
        public String root() {
            return "Hello World!";
        }

        @GET("/greeting/*")
        public String getGreeting(@Param("name") String name) {
            return "Greeting, " + (name != null ? name : "Hi") + "!";
        }

        @GET("/greeting/*")
        public String getGreeting() {
            return "Greeting!!!";
        }

        private void createHttpServer(Promise<Void> startPromise, Router router) {
            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(8080, HOST)
                    .onComplete(http -> {
                        if (http.succeeded()) {
                            System.out.println("Test verticle started on port: " + PORT);
                            startPromise.complete();
                        } else {
                            System.out.println("Test verticle failed to start on port: " + PORT);
                            startPromise.fail(http.cause());
                        }
                    });
        }

        private static void createFailureHandler(Router router) {
            router.route().failureHandler(ctx -> {
                Throwable failure = ctx.failure();
                if (failure instanceof HttpException) {
                    HttpException httpEx = (HttpException) failure;
                    if (httpEx.getStatusCode() == 401) {
                        ctx.response()
                                .setStatusCode(401)
                                .end("Unauthorized: " + httpEx.getMessage());
                        return;
                    }
                }
                // For other errors, you can handle or propagate
                ctx.next();
            });
        }
    }
}