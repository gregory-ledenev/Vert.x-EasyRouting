/*
Copyright 2025 Gregory Ledenev (gregory.ledenev37@gmail.com)

MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the “Software”), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package com.gl.vertx.easyrouting;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.MimeMapping;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import java.net.FileNameMap;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.*;

import static com.gl.vertx.easyrouting.EasyRouting.REDIRECT;

/**
 * Represents a result of a handler method execution in the routing system. This class wraps various types of responses
 * and provides unified handling for different return types, including files, JSON, collections, and primitive types.
 *
 * @param <T> The type of the result value
 */
public class HandlerResult<T> {
    private static final Logger logger = LoggerFactory.getLogger(HandlerResult.class);

    public static final String CONTENT_TYPE = "content-type";
    public static final String CONTENT_DISPOSITION = "content-disposition";

    public static final String CT_TEXT_PLAIN = "text/plain";
    public static final String CT_TEXT_HTML = "text/html";
    public static final String CT_APPLICATION_JSON = "application/json";
    public static final String CT_APPLICATION_OCTET_STREAM = "application/octet-stream";

    private final T result;
    private Map<String, String> headers = new HashMap<>();
    private int statusCode;

    /**
     * Creates a HandlerResult for sending plain text content.
     *
     * @param text The plain text content to send
     * @return HandlerResult configured for response
     */
    public static HandlerResult<String> plainText(String text) {
        Objects.requireNonNull(text);

        return new HandlerResult<>(text,
                Map.of(CONTENT_TYPE, CT_TEXT_PLAIN));
    }

    /**
     * Creates a HandlerResult for sending HTML content.
     *
     * @param path path to an HTML file to send
     * @return HandlerResult configured for HTML response
     */
    public static HandlerResult<String> html(Path path) {
        Objects.requireNonNull(path);

        try {
            String html = Files.readString(path);
            return new HandlerResult<>(html,
                    Map.of(CONTENT_TYPE, CT_TEXT_HTML));
        } catch (IOException e) {
            logger.error("Failed to read HTML file: " + path, e);
            return new HandlerResult<>("Failed to read HTML file: " + path.getFileName().toString(), 500);
        }
    }

    /**
     * Creates a HandlerResult that saves uploaded files and performs a redirect.
     *
     * @param folder      The destination folder for uploaded files
     * @param fileUploads List of file uploads to process
     * @param redirect    The URL to redirect to after saving files
     * @return HandlerResult configured for file saving and redirect
     */
    public static HandlerResult<String> saveFiles(String folder, List<FileUpload> fileUploads, String redirect) {
        Objects.requireNonNull(folder);
        Objects.requireNonNull(fileUploads);

        return new HandlerResult<>(redirect) {

            @Override
            public void handle(RoutingContext ctx) {
                for (FileUpload fileUpload : fileUploads) {
                    String uploadedFileName = fileUpload.uploadedFileName();
                    String fileName = fileUpload.fileName();
                    if (! fileName.isEmpty()) {
                        try {
                            Files.copy(Path.of(uploadedFileName), Path.of(folder, fileName), StandardCopyOption.REPLACE_EXISTING);
                        } catch (Exception e) {
                            logger.error("Failed to save uploaded file: " + fileName, e);
                            ctx.response().setStatusCode(400).end("Failed to save uploaded file: " + fileName);
                            return;
                        }
                    } else {
                        logger.warn("Uploaded file is missing");
                        ctx.response().setStatusCode(400).end("Uploaded file is missing");
                        return;
                    }
                }

                super.handle(ctx);
            }
        };
    }

    /**
     * Creates a HandlerResult for sending a file from a buffer.
     *
     * @param buffer   The buffer containing file data
     * @param fileName The name of the file to be sent
     * @return HandlerResult configured for file download
     */
    public static HandlerResult<Buffer> file(Buffer buffer, String fileName) {
        Objects.requireNonNull(buffer);
        Objects.requireNonNull(fileName);

        return new HandlerResult<>(buffer, Map.of(
                CONTENT_TYPE, getMimeType(fileName),
                CONTENT_DISPOSITION, MessageFormat.format("attachment; filename=\"{0}\"", fileName)
        ));
    }

    public static HandlerResult<String> file(String content, String fileName) {
        Objects.requireNonNull(content);
        Objects.requireNonNull(fileName);

        return new HandlerResult<>(content, Map.of(
                CONTENT_TYPE, getMimeType(fileName))
        );
    }

    /**
     * Creates a HandlerResult for sending a file from a Path.
     *
     * @param filePath The path to the file to be sent
     * @return HandlerResult configured for file download
     */
    public static HandlerResult<Path> file(Path filePath) {
        return new HandlerResult<>(filePath);
    }


    /**
     * Determines the MIME type of file based on its name.
     * If the MIME type cannot be determined, it defaults to "application/octet-stream".
     *
     * @param fileName The name of the file
     * @return The MIME type of the file
     */
    public static String getMimeType(String fileName) {
        FileNameMap fileNameMap = URLConnection.getFileNameMap();
        String mimeType = MimeMapping.getMimeTypeForFilename(fileName);
        if (mimeType == null)
            mimeType = CT_APPLICATION_OCTET_STREAM;

        return mimeType;
    }

    /**
     * Constructs a HandlerResult with a result value and default status code 200.
     *
     * @param result The result value
     */
    public HandlerResult(T result) {
        this.result = result;
        this.statusCode = 200;
    }

    /**
     * Constructs a HandlerResult with a result value, headers, and status code.
     *
     * @param result     The result value
     * @param headers    The HTTP headers to include in the response
     * @param statusCode The HTTP status code
     */
    public HandlerResult(T result, Map<String, String> headers, int statusCode) {
        this.result = result;
        if (headers != null)
            this.headers = headers;
        this.statusCode = statusCode;
    }

    /**
     * Constructs a HandlerResult with a result value and headers.
     *
     * @param result  The result value
     * @param headers The HTTP headers to include in the response
     */
    public HandlerResult(T result, Map<String, String> headers) {
        this(result, headers, 200);
    }

    /**
     * Constructs a HandlerResult with a result value and status code.
     *
     * @param result     The result value
     * @param statusCode The HTTP status code
     */
    public HandlerResult(T result, int statusCode) {
        this.result = result;
        this.statusCode = statusCode;
    }

    /**
     * Gets the result value.
     *
     * @return The result value
     */
    public T getResult() {
        return result;
    }

    /**
     * Gets the HTTP headers.
     *
     * @return Map of HTTP headers
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Sets the HTTP status code.
     *
     * @param statusCode The HTTP status code to set
     */
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * Gets the HTTP status code.
     *
     * @return The HTTP status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Adds or updates an HTTP header.
     *
     * @param key   The header name
     * @param value The header value
     */
    public void putHeader(String key, String value) {
        this.headers.put(key, value);
    }

    public void handle(RoutingContext ctx) {
        try {
            headers.forEach(ctx.response()::putHeader);

            if (getStatusCode() == 200) {
                if (result instanceof Path filePath) {
                    ctx.response().putHeader(CONTENT_TYPE, CT_APPLICATION_OCTET_STREAM);
                    ctx.response().putHeader(CONTENT_DISPOSITION, MessageFormat.format("attachment; filename=\"{0}\"", filePath.getFileName().toString()));
                    ctx.response().sendFile(filePath.toString())
                            .onSuccess(v -> ctx.response())
                            .onFailure(ctx::fail);
                } else if (result instanceof Buffer buffer) {
                    ctx.response().putHeader(CONTENT_TYPE, CT_APPLICATION_OCTET_STREAM);
                    ctx.response().send(buffer);
                } else if (result instanceof JsonObject jsonObject) {
                    sendJsonResponse(ctx, jsonObject.encodePrettily());
                } else if (result instanceof JsonArray jsonArray) {
                    sendJsonResponse(ctx, jsonArray.encodePrettily());
                } else if (result instanceof Map<?, ?> map) {
                    handleMapResult(ctx, map);
                } else if (result instanceof Collection<?> collection) {
                    handleCollectionResult(ctx, collection);
                } else if (result != null && result.getClass().isArray()) {
                    handleArrayResult(ctx, result);
                } else if (result instanceof Number || result instanceof Boolean) {
                    sendPlainTextResponse(ctx, result.toString());
                } else if (result instanceof String string) {
                    handleStringResult(ctx, string);
                } else {
                    handleOtherResult(ctx, result);
                }
            } else {
                ctx.response().setStatusCode(getStatusCode()).end();
            }
        } catch (HttpException e) {
            logger.error("Error sending result: ", e);
            ctx.response().setStatusCode(e.getStatusCode()).end(e.getMessage());
        } catch (Exception e) {
            logger.error("Error handling result: ", e);
            ctx.response().setStatusCode(500).end();
        }
    }

    private void sendJsonResponse(RoutingContext ctx, String json) {
        ctx.response()
                .putHeader(CONTENT_TYPE, CT_APPLICATION_JSON)
                .end(json);
    }

    private void sendPlainTextResponse(RoutingContext ctx, String text) {
        ctx.response()
                .putHeader(CONTENT_TYPE, CT_TEXT_PLAIN)
                .end(text);
    }

    private void handleMapResult(RoutingContext ctx, Map<?, ?> map) {
        JsonObject jsonObject = new JsonObject();
        map.forEach((key, value) -> jsonObject.put(key.toString(), value));
        sendJsonResponse(ctx, jsonObject.encodePrettily());
    }

    private void handleCollectionResult(RoutingContext ctx, Collection<?> collection) {
        JsonArray jsonArray = new JsonArray();
        collection.forEach(jsonArray::add);
        sendJsonResponse(ctx, jsonArray.encodePrettily());
    }

    private void handleArrayResult(RoutingContext ctx, Object array) {
        JsonArray jsonArray = new JsonArray();
        int length = java.lang.reflect.Array.getLength(array);
        for (int i = 0; i < length; i++) {
            jsonArray.add(java.lang.reflect.Array.get(array, i));
        }
        sendJsonResponse(ctx, jsonArray.encodePrettily());
    }

    private void handleStringResult(RoutingContext ctx, String string) {
        if (string.startsWith(REDIRECT) && string.length() > REDIRECT.length()) {
            String redirectPath = string.substring(REDIRECT.length());
            ctx.redirect(redirectPath);
        } else {
            if (! ctx.response().headers().contains(CONTENT_TYPE))
                ctx.response()
                        .putHeader(CONTENT_TYPE, CT_TEXT_HTML)
                        .end(string);
            else
                ctx.response().end(string);
        }
    }

    private void handleOtherResult(RoutingContext ctx, Object result) {
        try {
            JsonObject jsonObject = JsonObject.mapFrom(result);
            sendJsonResponse(ctx, jsonObject.encodePrettily());
        } catch (Exception e) {
            logger.error("Failed to convert result to JSON: " + result, e);
            sendPlainTextResponse(ctx, result.toString());
        }
    }
}
