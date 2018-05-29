package de.thokari.epages.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
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
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class CategoryTreeEndpointTest {

    static String privateApiUrl;
    static String privateApiShopUrl;
    static String shopInfoSoapUrl;
    static String publicApiUrl;

    static AppConfig appConfig;
    static DeploymentOptions deploymentOpts;
    static EndToEndConfig endToEndConfig;

    final Vertx vertx = Vertx.vertx();
    Future<Void> apiClientVerticleDeployed = Future.future();

    @BeforeClass
    public static void readConfig() throws IOException {

        JsonObject configJson = new JsonObject(new String(Files.readAllBytes(Paths.get("config.test.json"))));
        deploymentOpts = new DeploymentOptions().setConfig(configJson);
        appConfig = Model.fromJsonObject(configJson, AppConfig.class);

        JsonObject endToEndConfigJson = new JsonObject(
            new String(Files.readAllBytes(Paths.get("end-to-end-config.json"))));
        endToEndConfig = Model.fromJsonObject(endToEndConfigJson, EndToEndConfig.class);

        privateApiUrl = String.format("http://%s:8088/rs", endToEndConfig.epagesHostname);
        privateApiShopUrl = String.format("http://%s:8088/rs/appstore", endToEndConfig.epagesHostname);
        shopInfoSoapUrl = String.format("http://%s/epages/%s.soap", endToEndConfig.epagesHostname,
            endToEndConfig.shopName);
        publicApiUrl = String.format("http://%s/rs/shops/%s", endToEndConfig.epagesHostname, endToEndConfig.shopName);
    }

    @Before
    public void deployApiClientVerticle() {
        vertx.deployVerticle(EpagesApiClientVerticle.class.getName(), deploymentOpts, deployed -> {
            if (deployed.failed()) {
                deployed.cause().printStackTrace();
                apiClientVerticleDeployed.fail(deployed.cause());
            } else {
                apiClientVerticleDeployed.complete();
            }
        });
    }

    @Test
    public void testCategoryRenamingDoesNotTemporarilyBreakCategoryTree(TestContext context) {
        Async async = context.async();

        final int tries = 50;
        final int pause = 100; // milliseconds
        final int gracePeriod = 60000; // milliseconds, keep waiting this much longer for the test to finish

        // GIVEN
        apiClientVerticleDeployed.setHandler(deployed -> {
            if (deployed.failed()) {
                deployed.cause().printStackTrace();
                context.fail();
                async.complete();
            }

            maybeCreateShopAndPrivateAppAndGetToken().setHandler(tokenResult -> {

                context.assertTrue(tokenResult.succeeded());
                String token = tokenResult.result();
                AtomicInteger remaining = new AtomicInteger(tries);
                JsonObject categoryTreeCall = buildPublicApiCall(token, "category-tree");

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

                    JsonObject categoriesCall = buildPublicApiCall(token, "categories/" + categoryIdToBeRenamed, "PUT",
                        renamePayload);
                    requestApi(categoriesCall, response -> {

                        // THEN
                        context.assertTrue(response.succeeded(),
                            "Category renaming failed with error:\n" + response.cause());
                    });
                });
            });
        });

        async.awaitSuccess(pause * tries + gracePeriod);
    }

    public Future<String> maybeCreateShopAndPrivateAppAndGetToken() {

        Future<String> token = Future.future();

        if (!endToEndConfig.createShop) {
            obtainShopGuid().setHandler(shopGuidResult -> {

                if (endToEndConfig.createPrivateApp) {
                    createPrivateApp(shopGuidResult.result()).setHandler(result -> {
                        token.complete(result.result().getString("token"));
                    });
                } else {
                    token.complete(endToEndConfig.token);
                }
            });

        } else {
            token.fail("Shop creation not implemented yet, please use an already existing shop!");
        }

        return token;
    }

    private Future<JsonObject> createPrivateApp(String shopGuid) {
        String createPrivateAppUrl = String.format("appstore/%s/private-apps", shopGuid);
        JsonObject createPrivateAppApiCall = buildPrivateApiCall(createPrivateAppUrl, "POST",
            createPrivateAppPayload().encodePrettily());

        Future<JsonObject> createAppResult = Future.future();
        requestApi(createPrivateAppApiCall, result -> {
            if (result.succeeded()) {
                createAppResult.complete(result.result().body());
            } else {
                createAppResult.fail(result.cause());
            }
        });
        return createAppResult;
    }

    @SuppressWarnings("unused")
    private Future<Void> importOAuth2Clients() {
        JsonObject importClientsApiCall = buildPrivateApiCall("oauth2/import/clients", "PUT",
            createAppStoreClientPayload().encodePrettily());

        Future<Void> importResult = Future.future();
        requestApi(importClientsApiCall, result -> {
            importResult.complete();
        });
        return importResult;
    }

    private JsonObject createPrivateAppPayload() {
        return new JsonObject()
            .put("name", "End-To-End-Test-App")
            .put("callbackUrl", "https://example.com")
            .put("scopes", new JsonArray()
                .add("products_read").add("products_write")
                .add("products_read_batch")
                .add("orders_read").add("orders_write")
                .add("customers_read").add("customers_write")
                .add("legal_read").add("legal_write")
                .add("carts_read").add("carts_write")
                .add("newsletters_read").add("newsletters_write")
                .add("scripttags_read").add("scripttags_write")
                .add("coupons_read").add("coupons_write"));
    }

    private JsonArray createAppStoreClientPayload() {
        return new JsonArray().add(
            new JsonObject()
                .put("name", "End-To-End-Test-Client")
                .put("id", "my-client-id")
                .put("secret", "my-client-secret")
                .put("scopes", new JsonArray()
                    .add("products_read").add("products_write")
                    .add("products_read_batch")
                    .add("orders_read").add("orders_write")
                    .add("customers_read").add("customers_write")
                    .add("legal_read").add("legal_write")
                    .add("carts_read").add("carts_write")
                    .add("newsletters_read").add("newsletters_write")
                    .add("scripttags_read").add("scripttags_write")
                    .add("coupons_read").add("coupons_write")));
    }

    private Future<String> obtainShopGuid() {
        HttpClientOptions options = new HttpClientOptions();
        HttpClient client = vertx.createHttpClient(options);
        Future<String> shopGuidResult = Future.future();

        HttpClientRequest shopInfoRequest = client.get(endToEndConfig.epagesHostname, shopInfoSoapUrl);
        shopInfoRequest.handler(response -> {
            // The Request is expected to return HTTP 405, but we don't care, because we only want
            // the X-EPAGES-SITE header, which contains the shop GUID.
            String shopGuid = response.getHeader("X-EPAGES-SITE");
            shopGuidResult.complete(shopGuid);
        });
        shopInfoRequest.end();
        return shopGuidResult;
    }

    private void requestApi(JsonObject apiCall, Handler<AsyncResult<Message<JsonObject>>> handler) {
        vertx.eventBus().<JsonObject>send(EpagesApiClientVerticle.EVENT_BUS_ADDRESS, apiCall, handler);
    }

    private JsonObject buildPrivateApiCall(String path, String method, String body) {
        return new JsonObject()
            .put("apiUrl", privateApiUrl)
            .put("path", path)
            .put("method", method)
            .put("body", body);
    }

    private JsonObject buildPublicApiCall(String token, String path) {
        return new JsonObject()
            .put("apiUrl", publicApiUrl)
            .put("token", token)
            .put("path", path);
    }

    private JsonObject buildPublicApiCall(String token, String path, String method, String body) {
        return buildPublicApiCall(token, path)
            .put("method", method)
            .put("body", body);
    }
}
