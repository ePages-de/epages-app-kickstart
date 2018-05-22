package de.thokari.epages.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.thokari.epages.app.model.AppConfig;
import de.thokari.epages.app.model.Model;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class EpagesApiClientVerticleTest {

    final Integer apiMockPort = 9999;
    final String apiMockUrl = "http://localhost:" + apiMockPort + "/api";
    HttpServer apiMock;

    static JsonObject configJson;
    static AppConfig appConfig;

    final String token = "Q9T6g8td0lHzQbIg9CwgRNCrU1SfCko4";

    final JsonObject apiCall = new JsonObject()
        .put("path", "shop-info")
        .put("token", token)
        .put("apiUrl", apiMockUrl);
    // .put("apiUrl",
    // "https://devshop.epages.com/rs/shops/epagesdevD20161020T212339R164");

    final Vertx vertx = Vertx.vertx();
    final DeploymentOptions deploymentOpts = new DeploymentOptions().setConfig(configJson);

    final Future<HttpServer> apiMockStarted = Future.future();

    @BeforeClass
    public static void readConfig() throws IOException {
        configJson = new JsonObject(new String(Files.readAllBytes(Paths.get("config.test.json"))));
        appConfig = Model.fromJsonObject(configJson, AppConfig.class);
    }

    @Before
    public void startApiMock() {
        apiMock = vertx.createHttpServer().requestHandler(request -> {

            System.out.println("Mock server received request:\n" + request.rawMethod() + " " + request.absoluteURI());

            System.err.println(request.absoluteURI().matches(".*/api/categories/category-id$"));
            System.err.println(request.method().equals(HttpMethod.PUT));

            if (request.absoluteURI().matches(".*/api$") && request.method().equals(HttpMethod.GET)) {
                request.response()
                    .setStatusCode(200)
                    .end(new JsonObject().put("name", "Milestones").encodePrettily());

            } else if (request.absoluteURI().matches(".*/api/categories/category-id$")
                && request.method().equals(HttpMethod.PUT)) {
                request.response()
                    .setStatusCode(200)
                    .end(new JsonObject().put("categoryId", "some-category-id").encodePrettily());

            } else {
                request.response().setStatusCode(404).end();
            }

        }).listen(apiMockPort, apiMockStarted);
    }

    @After
    public void stopApiMock() {
        apiMock.close();
    }

    @Test
    public void testApiCallFailed(TestContext context) {
        Async async = context.async();

        apiMockStarted.setHandler(started -> {
            if (apiMockStarted.failed()) {
                apiMockStarted.cause().printStackTrace();
                context.fail();
                async.complete();
            }

            apiMock.close(closed -> {

                vertx.deployVerticle(EpagesApiClientVerticle.class.getName(), deploymentOpts, deployed -> {
                    if (deployed.failed()) {
                        deployed.cause().printStackTrace();
                        context.fail();
                        async.complete();
                    }

                    vertx.eventBus().<JsonObject>send(
                        EpagesApiClientVerticle.EVENT_BUS_ADDRESS, apiCall, response -> {
                            context.assertTrue(response.failed());
                            context.assertTrue(response.cause().getMessage().startsWith(String
                                .format("API request to '%s' failed because of 'Connection refused", apiMockUrl)));
                            context.assertEquals(500, ((ReplyException) response.cause()).failureCode());
                            async.complete();
                        });
                });
            });
        });
        async.awaitSuccess(2000);
    }

    @Test
    public void testCanMakePutRequest(TestContext context) {
        Async async = context.async();

        apiMockStarted.setHandler(started -> {
            if (apiMockStarted.failed()) {
                apiMockStarted.cause().printStackTrace();
                context.fail();
                async.complete();
            }

            vertx.deployVerticle(EpagesApiClientVerticle.class.getName(), deploymentOpts, deployed -> {
                if (deployed.failed()) {
                    deployed.cause().printStackTrace();
                    context.fail();
                    async.complete();
                }

                JsonObject apiCall = new JsonObject()
                    .put("path", "categories/category-id")
                    .put("token", token)
                    .put("apiUrl", apiMockUrl)
                    .put("method", "PUT");

                vertx.eventBus().<JsonObject>send(
                    EpagesApiClientVerticle.EVENT_BUS_ADDRESS, apiCall, response -> {
                        context.assertTrue(response.succeeded());
                        JsonObject body = response.result().body();
                        context.assertEquals("some-category-id", body.getString("categoryId"));
                        async.complete();
                    });
            });
        });
        async.awaitSuccess(2000);
    }

    @Test
    public void testShopInfoCall(TestContext context) {
        Async async = context.async();

        apiMockStarted.setHandler(started -> {
            if (apiMockStarted.failed()) {
                apiMockStarted.cause().printStackTrace();
                context.fail();
                async.complete();
            }

            vertx.deployVerticle(EpagesApiClientVerticle.class.getName(), deploymentOpts, deployed -> {
                if (deployed.failed()) {
                    deployed.cause().printStackTrace();
                    context.fail();
                    async.complete();
                }

                vertx.eventBus().<JsonObject>send(
                    EpagesApiClientVerticle.EVENT_BUS_ADDRESS, apiCall, response -> {
                        context.assertTrue(response.succeeded());
                        JsonObject body = response.result().body();
                        context.assertEquals("Milestones", body.getString("name"));
                        async.complete();
                    });
            });
        });
        async.awaitSuccess(2000);
    }
}
