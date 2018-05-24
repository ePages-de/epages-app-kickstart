package de.thokari.epages.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.thokari.epages.app.EpagesApiClientVerticle;
import de.thokari.epages.app.model.AppConfig;
import de.thokari.epages.app.model.Model;
import de.thokari.epages.test.model.EndToEndConfig;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class CategoryTreeEndpointTest {

    static String apiUrl;
    static String token;

    final int tries = 50;
    final int pause = 100;

    final Vertx vertx = Vertx.vertx();

    static AppConfig appConfig;
    static DeploymentOptions deploymentOpts;

    static EndToEndConfig endToEndConfig;

    final Future<HttpServer> apiMockStarted = Future.future();

    @BeforeClass
    public static void readConfig() throws IOException {
        JsonObject configJson = new JsonObject(new String(Files.readAllBytes(Paths.get("config.test.json"))));
        deploymentOpts = new DeploymentOptions().setConfig(configJson);

        JsonObject endToEndConfigJson = new JsonObject(
            new String(Files.readAllBytes(Paths.get("end-to-end-config.json"))));
        endToEndConfig = Model.fromJsonObject(endToEndConfigJson, EndToEndConfig.class);

        apiUrl = String.format("http://%s/rs/shops/%s", endToEndConfig.epagesHostname, endToEndConfig.shopName);
        token = endToEndConfig.token;
    }

    @Test
    public void testCategoryRenamingDoesNotTemporarilyBreakCategoryTree(TestContext context) {
        Async async = context.async();

        // GIVEN
        vertx.deployVerticle(EpagesApiClientVerticle.class.getName(), deploymentOpts, deployed -> {
            if (deployed.failed()) {
                deployed.cause().printStackTrace();
                context.fail();
                async.complete();
            }

            AtomicInteger remaining = new AtomicInteger(tries);
            JsonObject categoryTreeCall = buildApiCall("category-tree");

            requestApi(categoryTreeCall, categoryTreeResponse -> {

                String categoryIdToBeRenamed = categoryTreeResponse.result().body().getString("categoryId");

                // WHILE
                vertx.setPeriodic(pause, event -> {
                    if (remaining.decrementAndGet() >= 0) {
                        requestApi(categoryTreeCall, response -> {

                            // THEN
                            context.assertTrue(response.succeeded(),
                                "Received an error from category-tree endpoint:\n" + response.cause());
                        });
                    } else {
                        async.complete();
                    }
                });

                // WHEN
                String renamePayload = new JsonObject() //
                    .put("categoryId", categoryIdToBeRenamed) //
                    .put("alias", "Categories") //
                    .put("name", UUID.randomUUID().toString()) //
                    .encodePrettily();

                JsonObject categoriesCall = buildApiCall("categories/" + categoryIdToBeRenamed, "PUT", renamePayload);
                requestApi(categoriesCall, response -> {

                    // THEN
                    context.assertTrue(response.succeeded(),
                        "Category renaming failed with error:\n" + response.cause());
                });
            });

        });

        async.awaitSuccess(pause * tries + 1000);
    }

    private void requestApi(JsonObject apiCall, Handler<AsyncResult<Message<JsonObject>>> handler) {
        vertx.eventBus().<JsonObject>send(EpagesApiClientVerticle.EVENT_BUS_ADDRESS, apiCall, handler);
    }

    private JsonObject buildApiCall(String path) {
        return new JsonObject()
            .put("apiUrl", apiUrl)
            .put("token", token)
            .put("path", path);
    }

    private JsonObject buildApiCall(String path, String method, String body) {
        return buildApiCall(path)
            .put("method", method)
            .put("body", body);
    }
}
