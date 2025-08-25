package com.gl.vertx.easyrouting;

import com.gl.vertx.easyrouting.annotations.*;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.gl.vertx.easyrouting.TestApplication.User.of;
import static com.gl.vertx.easyrouting.TestUtils.testGET;
import static com.gl.vertx.easyrouting.TestUtils.testPOST;
import static com.gl.vertx.easyrouting.annotations.HttpMethods.GET;
import static com.gl.vertx.easyrouting.annotations.HttpMethods.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestApplication {

    static final String JWT_TOKEN_USER_ADMIN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0VXNlciIsInJvbGVzIjpbInVzZXIiLCJhZG1pbiJdLCJpYXQiOjE3NTQyNTUwMDN9.VgvXLusig-wC447NHetSonDfP60qlYI7yjFGvqvOqfo";
    static final String JWT_TOKEN_USER = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0VXNlciIsInJvbGVzIjpbInVzZXIiXSwiaWF0IjoxNzU0MjU1MDUwfQ.zcTUXFJxHnWSVdnU306tl4gKJZUlhujW5kPS1njjd4M";

    @BeforeEach
    void setUp() {
        com.gl.vertx.easyrouting.User.clearUserIDCounter();
    }

    @Test
    void testApplication() throws Throwable {
        testGET(TestApplicationImpl::new, 8080, "", httpResponse -> {
            assertEquals(200, httpResponse.statusCode());
            assertEquals("Hello from TestApplication!", httpResponse.body());
        });
    }

    @Test
    void testController() throws Throwable {
        testGET(() -> new TestApplicationImpl().controller(new TestController()), 8080, "testcontroller/",
                null,
                httpResponse -> {
                    assertEquals(200, httpResponse.statusCode());
                    assertEquals("Hello from TestController!", httpResponse.body());
                });
    }

    @Test
    void testAnnotatedConvertersDiscovery() throws Throwable {
        Application app = new TestApplicationImpl().
                module(new TestConverters()).
                onStartCompletion(application -> {
                    try {
                        assertEquals("""
                                     {
                                       "text/user-string"->[Lcom.gl.vertx.easyrouting.TestApplication$User; = java.lang.String com.gl.vertx.easyrouting.TestApplication$TestConverters.convertUsersToString([Lcom.gl.vertx.easyrouting.TestApplication$User;)
                                       "text/user-string"->com.gl.vertx.easyrouting.TestApplication$User = java.lang.String com.gl.vertx.easyrouting.TestApplication$TestConverters.convertUserToString(com.gl.vertx.easyrouting.TestApplication$User)
                                       "text/user-string"<-[Lcom.gl.vertx.easyrouting.TestApplication$User; = [Lcom.gl.vertx.easyrouting.TestApplication$User; com.gl.vertx.easyrouting.TestApplication$TestConverters.convertUsersFromString(java.lang.String)
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
        testGET(TestApplicationImpl::new, 8080, "testCustomHandler",
                null,
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("Hello World!", response.body());
                });
    }

    @Test
    void testComposeUser() throws Throwable {
        testPOST(TestApplicationImpl::new, 8080, "composeUser",
                builder -> builder.header("Content-Type", "application/json"),
                "{\"name\":\"John\", \"email\":\"john@aaa.com\"}",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"name":"John","email":"john@aaa.com"}""", response.body());
                });
    }

    @Test
    void testJsonMultiply() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcTestApplicationModule()), 8080, "api/jsonrpc/test",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "multiply", "params": {"a":2, "b":3}, "id": 2}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":6}""", response.body());
                });
    }

    @Test
    void testJsonMultiplyList() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcTestApplicationModule()), 8080, "api/jsonrpc/test",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "multiply", "params": {"list":[1, 2, 3, 4]}, "id": 2}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":24}""", response.body());
                });
    }

    @Test
    void testJsonMultiplyArray() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcTestApplicationModule()), 8080, "api/jsonrpc/test",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "multiply", "params": {"list":[1, 2, 3, 4, 5]}, "id": 2}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":120}""", response.body());
                });
    }

    @Test
    void testJsonIntArray() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcTestApplicationModule()), 8080, "api/jsonrpc/test",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "intArray", "params": {"a":2, "b":3}, "id": 2}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":[2,3]}""", response.body());
                });
    }

    @Test
    void testJsonIntList() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcTestApplicationModule()), 8080, "api/jsonrpc/test",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "intList", "params": {"a":2, "b":3}, "id": 2}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":[2,3]}""", response.body());
                });
    }

    @Test
    void testJsonIntMap() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcTestApplicationModule()), 8080, "api/jsonrpc/test",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "intMap", "params": {"a":2, "b":3}, "id": 2}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    JsonObject jsonObject = new JsonObject(response.body());
                    assertEquals("2", jsonObject.getJsonObject("result").getString("a"));
                    assertEquals("3", jsonObject.getJsonObject("result").getString("b"));
                });
    }

    @Test
    void testJsonVoidMethod() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcTestApplicationModule()), 8080, "api/jsonrpc/test",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "voidMethod", "params": {"a":2, "b":3}, "id": 2}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":null}""", response.body());
                });
    }

    @Test
    void testJsonNotification() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcTestApplicationModule()), 8080, "api/jsonrpc/test",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "voidMethod", "params": {"a":2, "b":3}}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("", response.body());
                    System.out.println(response.body());
                });
    }

    @Test
    void testJsonScheme() throws Throwable {
        testGET(() -> new TestApplicationImpl().module(new JsonRpcTestApplicationModule()), 8080, "api/jsonrpc/test",
                builder -> builder.header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                response -> {
                    assertEquals(200, response.statusCode());
//                        assertEquals("", response.body());
                    System.out.println(response.body());
                });
    }

    @Test
    void testJsonMultiplyAsString() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcTestApplicationModule()), 8080, "api/jsonrpc/test",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "multiplyAsString", "params": {"a":2, "b":3}, "id": 2}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":"6"}""", response.body());
                });
    }

    @Test
    void testJsonBlockingHello() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcTestApplicationModule()), 8080, "api/jsonrpc/test",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "blockingHello", "params": {}, "id": 2}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":"Blocking hello from RPC TestApplication!"}""", response.body());
                });
    }

    @Test
    void testJsonRootAdd() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcTestApplicationModule()), 8080, "api/jsonrpc/root/",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "add", "params": {"a":2, "b":3}, "id": 2}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":5}""", response.body());
                });
    }

    @Test
    void testJsonRpcGetUsers() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcUserApplicationModule()), 8080, "api/users/jsonrpc",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "getUsers", "params": {}, "id": 2}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":[{"id":"1","name":"John Doe","email":"john.doe@example.com"},{"id":"2","name":"Sarah Wilson","email":"sarah.wilson@example.com"},{"id":"3","name":"Michael Smith","email":"mike.smith@example.com"},{"id":"4","name":"Emily Johnson","email":"emily.j@example.com"},{"id":"5","name":"David Brown","email":"d.brown@example.com"}]}""", response.body());
                });
    }

    @Test
    void testJsonRpcGetUserByID() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcUserApplicationModule()), 8080, "api/users/jsonrpc",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {"jsonrpc": "2.0", "method": "getUser", "params": {"id":"3"}, "id": 2}""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":{"id":"3","name":"Michael Smith","email":"mike.smith@example.com"}}""", response.body());
                });
    }

    @Test
    void testJsonRpcUpdateUser() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcUserApplicationModule()), 8080, "api/users/jsonrpc",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {
                   "jsonrpc":"2.0",
                   "method":"updateUser",
                   "params":{
                      "id":"3",
                      "user":{
                         "id":"3",
                         "name":"Michael Smith JJJJJJJ",
                         "email":"mike.smith@example.com"
                      }
                   },
                   "id":2
                }""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":{"id":"3","name":"Michael Smith JJJJJJJ","email":"mike.smith@example.com"}}""", response.body());
                });
    }

    @Test
    void testJsonRpcUpdateUsers() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcUserApplicationModule()), 8080, "api/users/jsonrpc",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {
                   "jsonrpc":"2.0",
                   "method":"updateUsers",
                   "params":{
                      "ids":["3"],
                      "users":[{
                         "id":"3",
                         "name":"Michael Smith JJJJJJJ",
                         "email":"mike.smith@example.com"
                      }]
                   },
                   "id":2
                }""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                 {"jsonrpc":"2.0","id":"2","result":[{"id":"3","name":"Michael Smith JJJJJJJ","email":"mike.smith@example.com"}]}""", response.body());
                });
    }

    @Test
    void testJsonRpcUpdateUserList() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new JsonRpcUserApplicationModule()), 8080, "api/users/jsonrpc",
                builder -> builder
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                """
                {
                   "jsonrpc":"2.0",
                   "method":"updateUserList",
                   "params":{
                      "ids":["3"],
                      "users":[{
                         "id":"3",
                         "name":"Michael Smith JJJJJJJ",
                         "email":"mike.smith@example.com"
                      }]
                   },
                   "id":2
                }""",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("""
                                     {"jsonrpc":"2.0","id":"2","result":[{"id":"3","name":"Michael Smith JJJJJJJ","email":"mike.smith@example.com"}]}""", response.body());
                });
    }

    @Test
    void testConversionTo() throws Throwable {
        testGET(() -> new TestApplicationImpl().module(new TestConverters()), 8080, "testConversionTo",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("text/user-string", response.headers().map().get("content-type").get(0));
                    assertEquals("User[name=John Doe, email=john.doe@gmail.com]", response.body());
                });
    }

    @Test
    void testConversionFrom() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new TestConverters()), 8080, "testConversionFrom",
                builder -> builder.header("Content-Type", "text/user-string"),
                "User[name=John Doe, email=john.doe@gmail.com]",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("text/user-string", response.headers().map().get("content-type").get(0));
                    assertEquals("User[name=John Doe, email=john.doe@gmail.com]", response.body());
                });
    }

    @Test
    void testMultipleConversionFrom() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new TestConverters()), 8080, "testMultipleConversionFrom",
                builder -> builder.header("Content-Type", "text/user-string"),
                "User[name=John Doe, email=john.doe@gmail.com]+User[name=Mike Hardy, email=mike.hardy@gmail.com]",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("text/user-string", response.headers().map().get("content-type").get(0));
                    assertEquals("User[name=John Doe, email=john.doe@gmail.com]+User[name=Mike Hardy, email=mike.hardy@gmail.com]", response.body());
                });
    }

    @Test
    void testMultipleConversionFromList() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new TestConverters()), 8080, "testMultipleConversionFromList",
                builder -> builder.header("Content-Type", "text/user-string"),
                "User[name=John Doe, email=john.doe@gmail.com]+User[name=Mike Hardy, email=mike.hardy@gmail.com]",
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("text/user-string", response.headers().map().get("content-type").get(0));
                    assertEquals("User[name=John Doe, email=john.doe@gmail.com]+User[name=Mike Hardy, email=mike.hardy@gmail.com]", response.body());
                });
    }

    @Test
    void testMultipleHeaders() throws Throwable {
        testGET(TestApplicationImpl::new, 8080, "multipleHeaders",
                null,
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals(List.of("text/plain"), response.headers().map().get("content-type"));
                    assertEquals(List.of("Value1"), response.headers().map().get("header1"));
                    assertEquals(List.of("Value2"), response.headers().map().get("header2"));
                    assertEquals("Multiple Headers", response.body());
                });
    }

    @Test
    void testBlocking() throws Throwable {
        testGET(TestApplicationImpl::new, 8080, "blockingHello",
                null,
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("Blocking hello from TestApplication!", response.body());
                });
    }

    @Test
    void testMissingOptionalParam() throws Throwable {
        testGET(TestApplicationImpl::new, 8080, "concatenate?str1=Hello%20&str2=World",
                null,
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("Hello World", response.body());
                });
    }

    @Test
    void testOptionalParam() throws Throwable {
        testGET(TestApplicationImpl::new, 8080, "concatenate?str1=Hello%20&str2=World&str3=!",
                null,
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("Hello World!", response.body());
                });
    }

    @Test
    void testHeaderParam() throws Throwable {
        testGET(TestApplicationImpl::new, 8080, "concatenateWithHeader?str1=Hello%20&str2=World&str3=!",
                builder -> builder.header("content-type", "text/plain").header("Cookie", "SmallCookie=Oreo"),
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("Hello World!text/plainOreo", response.body());
                });
    }

    @Test
    void testFormSubmit() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new TestConverters()), 8080, "login?role=user",
                builder -> builder.header("Content-Type", "application/x-www-form-urlencoded"),
                "user=testUser&password=testPass",
                response -> {
                    assertEquals(200, response.statusCode());
                });
    }

    @Test
    void testFormSubmitNotAnnotated() throws Throwable {
        testPOST(() -> new TestApplicationImpl().module(new TestConverters()), 8080, "loginNotAnnotated?role=user",
                builder -> builder.header("Content-Type", "application/x-www-form-urlencoded"),
                "user=testUser&password=testPass",
                response -> {
                    assertEquals(200, response.statusCode());
                });
    }

    @Test
    void testUnauthenticated() throws Throwable {
        testGET(TestApplicationImpl::new, 8080, "api",
                null,
                response -> {
                    assertEquals(302, response.statusCode()); // redirect /loginForm
                    System.out.println(response.body());
                });
    }

    @Test
    void testAuthorized() throws Throwable {
        testGET(TestApplicationImpl::new, 8080, "api",
                builder -> builder.header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("Hello protected API!", response.body());
                });
    }

    @Test
    void testAuthorizedForUserAndAdmin() throws Throwable {
        testGET(TestApplicationImpl::new, 8080, "api/user/",
                builder -> builder.header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("Hello protected API (user)!", response.body());
                });
    }

    @Test
    void testAuthorizedForAdmin() throws Throwable {
        testGET(TestApplicationImpl::new, 8080, "api/admin/",
                builder -> builder.header("Authorization", "Bearer " + JWT_TOKEN_USER_ADMIN),
                response -> {
                    assertEquals(200, response.statusCode());
                    assertEquals("Hello protected API (admin)!", response.body());
                });
    }

    @Test
    void testUnauthorizedForAdmin() throws Throwable {
        testGET(TestApplicationImpl::new, 8080, "api/admin/",
                builder -> builder.header("Authorization", "Bearer " + JWT_TOKEN_USER),
                response -> {
                    assertEquals(403, response.statusCode());
                });
    }

    static class TestController {
        @GET("/testcontroller/*")
        public String hello() {
            return "Hello from TestController!";
        }
    }

    @Description("Test service for JSON-RPC methods")
    @Rpc(path = "/api/jsonrpc/test", provideScheme = true)
    static class JsonRpcTestApplicationModule extends ApplicationModule<TestApplicationImpl> {

        public JsonRpcTestApplicationModule(String... protectedRoutes) {
            super(protectedRoutes);
        }

        @Description("""
                     Multiplies two integers with an optional third integer
                     
                     @param a the first integer
                     @param b the second integer""")
        public int multiply(@Param("a") int a, @Param("b") int b, @Param(value = "c", defaultValue = "1") int c) {
            return a * b * c;
        }

        @Description("""
                     Multiplies list of integers
                     
                     @param a list""")
        public int multiply(@Param("list") List<Integer> list) {
            return list.stream().reduce(1, (x, y) -> x * y);
        }

        @Description("""
                     Multiplies array of integers
                     
                     @param a list""")
        public int multiply(@Param("list") int[] array) {
            return IntStream.of(array).reduce(1, (x, y) -> x * y);
        }

        @Description("Multiplies two integers and returns the result as a string")
        public String multiplyAsString(@Param("a") int a, @Param("b") int b) {
            return String.valueOf(a * b);
        }

        @Description("Returns an array of two integers")
        public int[] intArray(@Param("a") int a, @Param("b") int b) {
            return new int[]{a, b};
        }

        @Description("Returns a list of two integers")
        public List<Integer> intList(@Param("a") int a, @Param("b") int b) {
            return List.of(a, b);
        }

        @Description("Returns a map with two integer values")
        public Map<String, Integer> intMap(@Param("a") int a, @Param("b") int b) {
            return Map.of("a", a, "b", b);
        }

        @SuppressWarnings("EmptyMethod")
        @Description("A void method that does nothing")
        public void voidMethod(@Param("a") int a, @Param("b") int b) {
        }

        @Blocking
        public String blockingHello() {
            try {
                Thread.sleep(5000); // simulate a long-running task
            } catch (InterruptedException e) {
                // do nothing
            }
            return "Blocking hello from RPC TestApplication!";
        }
    }

    @Rpc(path = "/api/users/jsonrpc")
    static class JsonRpcUserApplicationModule extends ApplicationModule<TestApplicationImpl> {
        public JsonRpcUserApplicationModule(String... protectedRoutes) {
            super(protectedRoutes);
        }

        public List<com.gl.vertx.easyrouting.User> getUsers() {
            return application.userService.getUsers();
        }

        public com.gl.vertx.easyrouting.User getUser(@Param("id") String id) {
            return application.userService.getUser(id);
        }

        public com.gl.vertx.easyrouting.User addUser(@BodyParam("user") com.gl.vertx.easyrouting.User user) {
            return application.userService.addUser(user);
        }

        public com.gl.vertx.easyrouting.User updateUser(@Param("id") String id, @Param("user") com.gl.vertx.easyrouting.User user) {
            return application.userService.updateUser(user, id);
        }

        public List<com.gl.vertx.easyrouting.User> updateUsers(@Param("ids") String[] ids, @Param("users") com.gl.vertx.easyrouting.User[] users) {
            List<com.gl.vertx.easyrouting.User> result = new ArrayList<>();
            for (int i = 0; i < users.length; i++) {
                result.add(application.userService.updateUser(users[i], ids[i]));
            }
            return result;
        }

        public List<com.gl.vertx.easyrouting.User> updateUserList(@Param("ids") List<String> ids, @Param("users") List<com.gl.vertx.easyrouting.User> users) {
            List<com.gl.vertx.easyrouting.User> result = new ArrayList<>();
            for (int i = 0; i < users.size(); i++) {
                result.add(application.userService.updateUser(users.get(i), ids.get(i)));
            }
            return result;
        }

        public boolean deleteUser(@Param("id") String id) {
            return application.userService.deleteUser(id);
        }
    }

    static class TestConverters extends ApplicationModule<TestApplicationImpl> {
        @ConvertsTo("text/user-string")
        public static String convertUserToString(User user) {
            return user.toString();
        }

        @ConvertsFrom("text/user-string")
        public static User convertUserFromString(String content) {
            return of(content);
        }

        @ConvertsTo("text/user-string")
        public static String convertUsersToString(User[] users) {
            return Arrays.stream(users)
                    .map(User::toString)
                    .reduce((s1, s2) -> s1 + "+" + s2)
                    .orElse("");
        }

        @ConvertsFrom("text/user-string")
        public static User[] convertUsersFromString(String content) {
            String[] parts = content.split("\\+");
            if (parts.length > 0) {
                User[] users = new User[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    users[i] = of(parts[i]);
                }
                return users;
            }
            return new User[0];
        }

        @ConvertsTo("text/xml")
        public static String convertUserToXml(User user) {
            return user.toString();
        }

        @ConvertsFrom("text/xml")
        public static User convertUserFromXml(String content) {
            return of(content);
        }
    }

    @SuppressWarnings("SameReturnValue")
    @Rpc(path = "/api/jsonrpc/root", provideScheme = true)
    static class TestApplicationImpl extends Application {
        public static final String JWT_PASSWORD = "veeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeery long password";
        final UserService userService = new UserService();

        public TestApplicationImpl() {
            jwtAuth(JWT_PASSWORD, "/api/*");
        }

        public static void main(String[] args) {
            TestApplicationImpl app = new TestApplicationImpl().
                    module(new JsonRpcUserApplicationModule()).
                    module(new TestConverters()).
                    onStartCompletion(application -> {
                        System.out.println(application.getAnnotatedConverters().toString(false));
                        application.waitForInput();
                    }).
                    handleShutdown().
                    start(8080);
        }

        public int add(@Param("a") int a, @Param("b") int b, @Param(value = "c", defaultValue = "0") int c) {
            return a + b + c;
        }

        @SuppressWarnings("EmptyMethod")
        @Deprecated
        void doNothing() {
        }

        @GET(value = "/*")
        public String hello() {
            return "Hello from TestApplication!";
        }

        @Blocking
        @GET(value = "/blockingHello")
        public String blockingHello() {
            try {
                Thread.sleep(5000); // simulate a long-running task
            } catch (InterruptedException e) {
                // do nothing
            }
            return "Blocking hello from TestApplication!";
        }

        @Form
        @POST(value = "/login")
        public String login(@Param("user") String user, @Param("password") String password, @Param(value = "role", defaultValue = "") String role) {
            return JWTUtil.generateToken(getVertx(), user, Arrays.asList(role.split(",")), null, JWT_PASSWORD);
        }

        @Form
        @POST(value = "/loginNotAnnotated")
        public String loginNotAnnotated(String user, String password, String role) {
            return JWTUtil.generateToken(getVertx(), user, Arrays.asList(role.split(",")), null, JWT_PASSWORD);
        }

        @HandlesStatusCode(401)
        @GET(value = "/loginForm")
        public String loginForm(@Param(value = "redirect", defaultValue = "") String redirect) {
            return "Login Form - redirect back to: " + redirect;
        }

        @GET(value = "/api/*")
        public String api() {
            return "Hello protected API!";
        }

        @RequiredRoles({"user", "admin"})
        @GET(value = "/api/user/*")
        public String apiUser() {
            return "Hello protected API (user)!";
        }

        @RequiredRoles({"admin"})
        @GET(value = "/api/admin/*")
        public String apiAdmin() {
            return "Hello protected API (admin)!";
        }

        @GET(value = "/concatenate")
        public String concatenate(@Param("str1") String str1, @Param("str2") String str2, @Param(value = "str3", defaultValue = "") String str3) {
            return str1 + str2 + (str3 != null && str3.isEmpty() ? "" : str3);
        }

        @GET(value = "/concatenateWithHeader")
        public String concatenateWithHeader(@Param("str1") String str1,
                                            @Param("str2") String str2,
                                            @Param(value = "str3", defaultValue = "") String str3,
                                            @HeaderParam("content-type") String header,
                                            @CookieParam("SmallCookie") String cookie) {
            return str1 + str2 + (str3 != null && str3.isEmpty() ? "" : str3) + header + cookie;
        }

        @HttpHeader("content-type: text/plain")
        @HttpHeader("header1: Value1")
        @HttpHeader("header2: Value2")
        @GET("/multipleHeaders")
        public String getMultipleHeaders() {
            return "Multiple Headers";
        }

        @GET("/testCustomHandler")
        public Result<String> testCustomHandler() {
            return new Result<>("Hello").handler((result, ctx) ->
                    result.setResult(result.getResult() + " World!"));
        }

        @ContentType("text/user-string")
        @GET("/testConversionTo")
        public User testConversionTo() {
            return new User("John Doe", "john.doe@gmail.com");
        }

        @ContentType("text/user-string")
        @POST("/testConversionFrom")
        public User testConversionFrom(@BodyParam("user") User user) {
            return user;
        }

        @ContentType("text/user-string")
        @POST("/testMultipleConversionFrom")
        public User[] testMultipleConversionFrom(@BodyParam("users") User[] users) {
            return users;
        }

        @ContentType("text/user-string")
        @POST("/testMultipleConversionFromList")
        public List<User> testMultipleConversionFromList(@BodyParam("users") List<User> users) {
            return users;
        }

        @DecomposeBody
        @POST("/composeUser")
        public User composeUser(String name, String email) {
            return new User(name, email);
        }

        public UserService userService() {
            return userService;
        }
    }

    record User(String name, String email) {
        public static User of(String content) {
            String content2 = content.substring(content.indexOf("[") + 1, content.indexOf("]"));
            String[] parts = content2.split(",\\s*", 2);
            return new User(parts[0].substring(parts[0].indexOf("=") + 1), parts[1].substring(parts[1].indexOf("=") + 1));
        }
    }

    @SuppressWarnings("SameReturnValue")
    static class HelloWorld extends Application {
        public static void main(String[] args) {
            new HelloWorld().start();
        }

        @GET(value = "/*")
        String hello() {
            return "Hello World!";
        }
    }
}
