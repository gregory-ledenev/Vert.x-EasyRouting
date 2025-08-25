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

import com.gl.vertx.easyrouting.annotations.FileFromFolder;
import com.gl.vertx.easyrouting.annotations.FileFromResource;
import com.gl.vertx.easyrouting.annotations.NullResult;
import com.gl.vertx.easyrouting.annotations.Template;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.MimeMapping;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.common.template.TemplateEngine;
import io.vertx.ext.web.handler.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static com.gl.vertx.easyrouting.EasyRouting.REDIRECT;

/**
 * Represents a result of a handler method execution in the routing system. This class wraps various types of responses
 * and provides unified handling for different return types, including files, JSON, collections, and primitive types.
 *
 * @param <T> The type of the result value
 */
public class Result<T> {
    public static final String CONTENT_TYPE = "content-type";
    public static final String CONTENT_DISPOSITION = "content-disposition";
    public static final String CT_TEXT_PLAIN = "text/plain";
    public static final String CT_TEXT_HTML = "text/html";
    public static final String CT_APPLICATION_JSON = "application/json";
    public static final String CT_APPLICATION_OCTET_STREAM = "application/octet-stream";
    public static final String ROOT_PATH = "/";
    public static final String PARENT_PATH = "..";
    static final String INDEX_HTML = "index.html";
    private static final Logger logger = LoggerFactory.getLogger(Result.class);
    private T result;
    private Map<String, String> headers = new HashMap<>();
    private int statusCode;
    private Annotation[] annotations;
    private Class<?> resultClass;
    private BiConsumer<Result<T>, RoutingContext> handler;

    private TemplateEngine templateEngine;

    /**
     * Constructs a HandlerResult with a result value and default status code 200.
     *
     * @param result The result value
     */
    public Result(T result) {
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
    public Result(T result, Map<String, String> headers, int statusCode) {
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
    public Result(T result, Map<String, String> headers) {
        this(result, headers, 200);
    }

    /**
     * Constructs a HandlerResult with a result value and status code.
     *
     * @param result     The result value
     * @param statusCode The HTTP status code
     */
    public Result(T result, int statusCode) {
        this.result = result;
        this.statusCode = statusCode;
    }

    /**
     * Creates a HandlerResult for sending plain text content.
     *
     * @param text The plain text content to send
     * @return HandlerResult configured for response
     */
    public static Result<String> plainText(String text) {
        Objects.requireNonNull(text);

        return new Result<>(text,
                Map.of(CONTENT_TYPE, CT_TEXT_PLAIN));
    }

    /**
     * Creates a HandlerResult for sending HTML content.
     *
     * @param path path to an HTML file to send
     * @return HandlerResult configured for HTML response
     */
    public static Result<String> html(Path path) {
        Objects.requireNonNull(path);

        try {
            String html = Files.readString(path);
            return new Result<>(html,
                    Map.of(CONTENT_TYPE, CT_TEXT_HTML));
        } catch (IOException e) {
            logger.error("Failed to read HTML file: " + path, e);
            return new Result<>("Failed to read HTML file: " + path.getFileName().toString(), 500);
        }
    }

    /**
     * Creates a HandlerResult that saves uploaded files and performs a redirect.
     *
     * @param files    List of file uploads to process
     * @param toFolder The destination folder for uploaded files
     * @param redirect The URL to redirect to after saving files
     * @return HandlerResult configured for file saving and redirect
     */
    public static Result<String> saveFiles(List<FileUpload> files, String toFolder, String redirect) {
        Objects.requireNonNull(files);
        Objects.requireNonNull(toFolder);

        return new Result<>(redirect).handler((result, ctx) -> {
            for (FileUpload fileUpload : files) {
                String uploadedFileName = fileUpload.uploadedFileName();
                String fileName = fileUpload.fileName();
                try {
                    Files.copy(Path.of(uploadedFileName), Path.of(toFolder, fileName), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    logger.error("Failed to save uploaded file: " + fileName, e);
                    ctx.response().setStatusCode(400).end("Failed to save uploaded file: " + fileName);
                    return;
                } finally {
                    fileUpload.delete(); // delete a temp file disregarding any errors to avoid leakage
                }
            }

            result.defaultHandle(ctx);
        });
    }

    /**
     * Saves a list of uploaded files using a provided file-saving function and performs a redirect. Each file upload in
     * the list is passed to the provided file-saving function, which is responsible for handling the file-saving logic.
     * If the save operation is successful, the uploaded file is deleted. If an error occurs during saving, an
     * appropriate response is sent to indicate failure.
     *
     * @param files     The list of uploaded files to save.
     * @param fileSaver A function that performs the file-saving operation. It takes a {@code Path} representing the
     *                  uploaded file's path and a {@code String} representing the file's name, and returns a
     *                  {@code Boolean} indicating success or failure.
     * @param redirect  The URL to redirect to after processing the files.
     * @return A {@code Result<String>} that handles the file-saving process and subsequent redirect.
     */
    public static Result<String> saveFiles(List<FileUpload> files, BiFunction<Path, String, Boolean> fileSaver, String redirect) {
        Objects.requireNonNull(files);
        Objects.requireNonNull(fileSaver);

        return new Result<>(redirect).handler((result, ctx) -> {
            for (FileUpload fileUpload : files) {
                String uploadedFileName = fileUpload.uploadedFileName();
                String fileName = fileUpload.fileName();
                if (!fileName.isEmpty()) {
                    boolean saved;
                    try {
                        saved = fileSaver.apply(Path.of(uploadedFileName), fileName);
                    } catch (Exception e) {
                        logger.error("Failed to save uploaded file: " + fileName, e);
                        ctx.response().setStatusCode(400).end("Failed to save uploaded file: " + fileName);
                        return;
                    }
                    if (saved)
                        fileUpload.delete();
                } else {
                    logger.warn("Uploaded file is missing");
                    ctx.response().setStatusCode(400).end("Uploaded file is missing");
                    return;
                }
            }
            result.defaultHandle(ctx);
        });
    }

    /**
     * Creates a HandlerResult for sending a file from a buffer.
     *
     * @param buffer   The buffer containing file data
     * @param fileName The name of the file to be sent
     * @return HandlerResult configured for file download
     */
    public static Result<Buffer> file(Buffer buffer, String fileName) {
        Objects.requireNonNull(buffer);
        Objects.requireNonNull(fileName);

        return new Result<>(buffer, Map.of(
                CONTENT_TYPE, getMimeType(fileName),
                CONTENT_DISPOSITION, MessageFormat.format("attachment; filename=\"{0}\"", fileName)
        ));
    }

    /**
     * Creates a HandlerResult for sending a file from a string.
     *
     * @param content  The string containing file data
     * @param fileName The name of the file to be sent
     * @return HandlerResult configured for file download
     */
    public static Result<String> file(String content, String fileName) {
        Objects.requireNonNull(content);
        Objects.requireNonNull(fileName);

        return new Result<>(content, Map.of(
                CONTENT_TYPE, getMimeType(fileName))
        );
    }

    /**
     * Creates a HandlerResult for sending a file from a class resource. This method attempts to load a file from the
     * resources associated with the specified class. If the requested name is "/", it defaults to "index.html".
     *
     * @param clazz The class used to locate the resource
     * @param name  The name/path of the resource to load
     * @return HandlerResult configured for file response. Returns 403 status if path contains "..", 404 status if file
     * not found, or file content if successful
     */
    public static Result<String> fileFromResource(Class<?> clazz, String name) {
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(name);

        if (name.contains(PARENT_PATH))
            return new Result<>("Forbidden", 403);

        String file;
        if (name.equals(ROOT_PATH))
            file = INDEX_HTML;
        else
            file = name.substring(name.lastIndexOf(ROOT_PATH) + 1);

        try (InputStream in = clazz.getResourceAsStream(file)) {
            if (in != null)
                return Result.file(new String(in.readAllBytes()), file);
            else
                return fileNotFound(file);
        } catch (IOException e) {
            return failedToReadFile(file);
        }
    }

    /**
     * Creates a HandlerResult for sending a file from a specified folder. This method attempts to load a file from the
     * given folder path. If the requested name is "/", it defaults to "index.html".
     *
     * @param folder The base folder path where the file should be loaded from
     * @param name   The name/path of the file to load
     * @return HandlerResult configured for file response. Returns 403 status if path contains "..", 404 status if file
     * not found, or file content if successful
     */
    public static Result<?> fileFromFolder(Path folder, String name, TemplateEngine templateEngine) {
        Objects.requireNonNull(folder);
        Objects.requireNonNull(name);

        if (name.contains(PARENT_PATH))
            return new Result<>("Forbidden", 403);

        String file;
        if (name.equals(ROOT_PATH))
            file = INDEX_HTML;
        else
            file = name.substring(name.lastIndexOf(ROOT_PATH) + 1);

        Path filePath = folder.resolve(file);
        try {
            if (Files.exists(filePath)) {
                if (templateEngine == null)
                    return Result.file(Files.readString(filePath), file);
                else
                    return new Result<>(filePath) {
                        @Override
                        public void defaultHandle(RoutingContext ctx) {
                            templateEngine.render(ctx.data(), filePath.toAbsolutePath().toString()).
                                    onComplete((buffer, ex) -> {
                                        if (ex == null) {
                                            Result.file(buffer.toString(), file).defaultHandle(ctx);
                                        } else {
                                            ctx.response().setStatusCode(500).end("Failed to process template: " + filePath + ex.getMessage());
                                            logger.error("Failed to process template: " + filePath, ex);
                                        }
                                    });
                        }
                    };
            } else {
                return fileNotFound(file);
            }
        } catch (IOException e) {
            return failedToReadFile(file);
        }
    }

    /**
     * Creates a HandlerResult that performs a redirect to the specified location. This method creates a response that
     * will redirect the client to a new URL.
     *
     * @param location The URL or path to redirect to
     * @return HandlerResult configured for redirect response
     */
    public static Result<String> redirect(String location) {
        return new Result<>(REDIRECT + location);
    }

    /**
     * Creates a HandlerResult for a failed file read operation.
     *
     * @param file The name of the file that failed to be read
     * @return HandlerResult with error message and 500 status code
     */
    public static Result<String> failedToReadFile(String file) {
        return new Result<>("Failed to read file: " + file, 500);
    }

    /**
     * Creates a HandlerResult for an invalid login attempt. This method returns a HandlerResult with a 401
     * (Unauthorized) status code and an appropriate error message.
     *
     * @return HandlerResult configured with error message and 401 status code
     */
    public static Result<String> invalidLogin() {
        return new Result<>("Invalid login or password", 401);
    }

    /**
     * Creates a HandlerResult for a file not found condition.
     *
     * @param file The name of the file that was not found
     * @return HandlerResult with error message and 404 status code
     */
    public static Result<String> fileNotFound(String file) {
        return new Result<>("File not found: " + file, 404);
    }

    /**
     * Creates a HandlerResult for sending a file from a Path.
     *
     * @param filePath The path to the file to be sent
     * @return HandlerResult configured for file download
     */
    public static Result<Path> file(Path filePath) {
        return new Result<>(filePath);
    }

    /**
     * Determines the MIME type of file based on its name. If the MIME type cannot be determined, it defaults to
     * "application/octet-stream".
     *
     * @param fileName The name of the file
     * @return The MIME type of the file
     */
    public static String getMimeType(String fileName) {
        FileNameMap fileNameMap = URLConnection.getFileNameMap();
        String mimeType = MimeMapping.mimeTypeForFilename(fileName);
        if (mimeType == null)
            mimeType = CT_APPLICATION_OCTET_STREAM;

        return mimeType;
    }

    public boolean isProcessTemplate() {
        return templateEngine != null;
    }

    public TemplateEngine getTemplateEngine() {
        return templateEngine;
    }

    void setTemplateEngine(TemplateEngine aTemplateEngine) {
        templateEngine = aTemplateEngine;
    }

    /**
     * Sets a custom handler for processing the result. This allows for custom handling logic to be applied when
     * processing the result instead of using the default handling behavior. Handler can end response either: <br> - by
     * calling the {@code ctx.response().end()} method, or <br> - by calling the {@code defaultHandle(RoutingContext)}
     * method, <br> otherwise {@code defaultHandle(RoutingContext)} will be called automatically
     * <p>TIP: if needed to continue processing with the default handler - call the
     * {@code defaultHandle(RoutingContext)} method.
     *
     * @param handler A BiConsumer that takes this Result instance and a RoutingContext as parameters and defines custom
     *                handling logic
     * @return This Result instance for method chaining
     */
    public Result<T> handler(BiConsumer<Result<T>, RoutingContext> handler) {
        this.handler = handler;
        return this;
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
     * Sets the result value.
     *
     * @param result The result value to set
     */
    public void setResult(T result) {
        this.result = result;
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
     * Gets the HTTP status code.
     *
     * @return The HTTP status code
     */
    public int getStatusCode() {
        return statusCode;
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
     * Adds or updates an HTTP header.
     *
     * @param key   The header name
     * @param value The header value
     */
    public void putHeader(String key, String value) {
        this.headers.put(key, value);
    }

    protected final void handle(RoutingContext ctx) {
        if (handler != null) {
            handler.accept(this, ctx);
            if (!ctx.response().ended())
                defaultHandle(ctx);
        } else {
            defaultHandle(ctx);
        }
    }

    /**
     * Performs default handling of the result based on its type and sends the appropriate HTTP response. This method
     * processes different types of results and configures the response accordingly: - Path: Sends a file as attachment
     * - Buffer: Sends binary data - JsonObject/JsonArray: Sends JSON response - Map: Converts to JSON and sends -
     * Collection/Array: Converts to JSON array and sends - Number/Boolean: Sends as plain text - String: Handles as
     * HTML, redirect, or file based on content and annotations - Other objects: Attempts to convert to JSON or falls
     * back to toString()
     *
     * @param ctx The Vert.x routing context to handle the response
     * @throws HttpException if there's an error during response processing
     */
    public void defaultHandle(RoutingContext ctx) {
        try {
            headers.forEach(ctx.response()::putHeader);
            ctx.response().setStatusCode(statusCode);
            RpcContext rpcContext = RpcContext.getRpcContext(ctx);
            if (rpcContext != null) {
                rpcContext.getRpcResponse(result).handle(ctx);
            } else {
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
                    sendJsonResponse(ctx, jsonObject.encode());
                } else if (result instanceof JsonArray jsonArray) {
                    sendJsonResponse(ctx, jsonArray.encode());
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
                } else if (result == null) {
                    handleNullResult(ctx);
                } else {
                    handleOtherResult(ctx, result);
                }
            }
        } catch (HttpException e) {
            logger.error("Error sending result: ", e);
            ctx.response().setStatusCode(e.getStatusCode()).end(e.getMessage());
        } catch (Exception e) {
            logger.error("Error handling result: ", e);
            ctx.response().setStatusCode(500).end();
        }
    }

    private void handleNullResult(RoutingContext ctx) {
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                if (annotation instanceof NullResult nullResult) {
                    ctx.response().setStatusCode(nullResult.statusCode()).end(nullResult.value());
                    return;
                }
            }
        }

        ctx.response().end();
    }

    private void sendJsonResponse(RoutingContext ctx, String json) {
        ctx.response().putHeader(CONTENT_TYPE, CT_APPLICATION_JSON);
        ctx.response().end(json);
    }

    private void sendPlainTextResponse(RoutingContext ctx, String text) {
        ctx.response()
                .putHeader(CONTENT_TYPE, CT_TEXT_PLAIN)
                .end(text);
    }

    private void handleMapResult(RoutingContext ctx, Map<?, ?> map) {
        JsonObject jsonObject = new JsonObject();
        map.forEach((key, value) -> jsonObject.put(key.toString(), value));
        sendJsonResponse(ctx, jsonObject.encode());
    }

    private void handleCollectionResult(RoutingContext ctx, Collection<?> collection) {
        JsonArray jsonArray = new JsonArray();
        collection.forEach(jsonArray::add);
        sendJsonResponse(ctx, jsonArray.encode());
    }

    private void handleArrayResult(RoutingContext ctx, Object array) {
        JsonArray jsonArray = new JsonArray();
        int length = java.lang.reflect.Array.getLength(array);
        for (int i = 0; i < length; i++) {
            jsonArray.add(java.lang.reflect.Array.get(array, i));
        }
        sendJsonResponse(ctx, jsonArray.encode());
    }

    private void handleStringResult(RoutingContext ctx, String string) {
        if (string.startsWith(REDIRECT) && string.length() > REDIRECT.length()) {
            String redirectPath = string.substring(REDIRECT.length());
            ctx.redirect(redirectPath);
        } else {

            if (annotations != null) {
                Template templateAnnotation = null;
                for (Annotation annotation : annotations) {
                    if (annotation instanceof Template) {
                        templateAnnotation = (Template) annotation;
                        break;
                    }
                }
                for (Annotation annotation : annotations) {
                    if (annotation instanceof FileFromResource fileFromResource) {
                        Result.fileFromResource(fileFromResource.value(), string).handle(ctx);
                        return;
                    } else if (annotation instanceof FileFromFolder fileFromFolder) {
                        String mimeType = getMimeType(string);
                        boolean processTemplate = templateAnnotation != null &&
                                (CT_TEXT_HTML.equals(mimeType) || Arrays.asList(templateAnnotation.processMimeTypes()).contains(mimeType));
                        if (processTemplate && templateEngine == null) {
                            logger.error("Skipped processing templateAnnotation: " + string + " No templateAnnotation engine registered with the app.");
                            processTemplate = false;
                        }
                        Result.fileFromFolder(Path.of(fileFromFolder.value()), string, processTemplate ? templateEngine : null).handle(ctx);
                        return;
                    }
                }
            }

            if (!ctx.response().headers().contains(CONTENT_TYPE))
                ctx.response().putHeader(CONTENT_TYPE, CT_TEXT_HTML);
            ctx.end(string);
        }
    }

    private void handleOtherResult(RoutingContext ctx, Object result) {
        try {
            JsonObject jsonObject = JsonObject.mapFrom(result);
            sendJsonResponse(ctx, jsonObject.encode());
        } catch (Exception e) {
            logger.error("Failed to convert result to JSON: " + result, e);
            sendPlainTextResponse(ctx, result.toString());
        }
    }

    public Annotation[] getAnnotations() {
        return annotations;
    }

    void setAnnotations(Annotation[] annotations) {
        this.annotations = annotations;
    }

    public Class<?> getResultClass() {
        return resultClass;
    }

    void setResultClass(Class<?> resultClass) {
        this.resultClass = resultClass;
    }
}
