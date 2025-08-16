package com.gl.vertx.easyrouting;

import com.gl.vertx.easyrouting.annotations.HttpMethods;
import com.gl.vertx.easyrouting.annotations.Param;
import com.gl.vertx.easyrouting.annotations.UploadsParam;
import io.vertx.ext.web.FileUpload;

import java.io.File;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FilesApplication extends Application {
    public static void main(String[] args) {
        FilesApplication app = new FilesApplication();
        app.start();
    }

    @HttpMethods.GET(value = "/")
    public String get() {
        File folder = new File("files");
        StringBuilder fileList = new StringBuilder();
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                Arrays.stream(files)
                        .sorted(Comparator.comparing(File::lastModified))
                        .forEach(file -> fileList.append(MessageFormat.
                                format("<li><a href=\"/files/serveFile?fileName={0}\">{0}</a> ({1} bytes)</li>\n",
                                        file.getName(), file.length())));
            }
        }
        return MessageFormat.format(HTML, fileList.toString());
    }

    @HttpMethods.GET(value = "/files/serveFile")
    public Path serveFile(@Param("fileName") String fileName) {
        return Path.of("files", fileName);
    }

    @HttpMethods.POST(value = "/files/uploadFile")
    public Result<String> handleUploads(@Param("fileCount") int fileCount, @UploadsParam List<FileUpload> fileUploads) {
        return Result.saveFiles(fileUploads, "files", "redirect:/");
    }

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
}
