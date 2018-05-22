package de.thokari.epages.app;

import java.net.MalformedURLException;
import java.net.URL;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class EpagesApiClientVerticle extends AbstractVerticle {

    public static final String EVENT_BUS_ADDRESS = "api_client";
    private static final Logger LOG = LoggerFactory.getLogger(EpagesApiClientVerticle.class);
    private HttpClient client;

    public void start() {

        client = vertx.createHttpClient();

        vertx.eventBus().<JsonObject>consumer(EVENT_BUS_ADDRESS).handler(message -> {
            JsonObject payload = message.body();

            final String apiUrl = payload.getString("apiUrl");
            final String path = payload.getString("path");

            final String finalRequestUrl = apiUrl + (path != null ? "/" + path : "");
            final String token = payload.getString("token");

            final String methodName = payload.getString("method");
            final HttpMethod method = methodName != null ? HttpMethod.valueOf(methodName) : HttpMethod.GET;

            final String body = payload.getString("body");

            LOG.info(String.format("Requesting API at '%s'", finalRequestUrl));

            makeApiRequest(finalRequestUrl, token, method, body).setHandler(response -> {
                if (response.failed()) {
                    String errorMsg = String.format("API request to '%s' failed because of '%s'", finalRequestUrl,
                        response.cause().getMessage());
                    LOG.error(errorMsg);
                    message.fail(500, errorMsg);
                } else {
                    message.reply(response.result());
                }
            });
        });
    }

    @SuppressWarnings("unused")
    private Future<JsonObject> makeApiRequest(String apiUrl) {
        return makeApiRequest(apiUrl, null);
    }

    private Future<JsonObject> makeApiRequest(String apiUrl, String token) {
        return makeApiRequest(apiUrl, token, null, null);
    }

    private Future<JsonObject> makeApiRequest(String apiUrl, String token, HttpMethod method, String input) {
        Future<JsonObject> future = Future.future();

        URL url = null;
        try {
            url = new URL(apiUrl);
        } catch (MalformedURLException malformedUrl) {
            future.fail(malformedUrl);
        }

        boolean useSsl = "https".equals(url.getProtocol());
        RequestOptions options = new RequestOptions()
            .setSsl(useSsl)
            .setPort(url.getPort() != -1 ? url.getPort() : (useSsl ? 443 : 80))
            .setHost(url.getHost())
            .setURI(url.getPath());

        HttpClientRequest request = client.request(method, options, response -> {
            response.exceptionHandler(exception -> future.fail(exception));
            Boolean responseIsOk = String.valueOf(response.statusCode()).startsWith("2")
                || String.valueOf(response.statusCode()).startsWith("3");
            response.bodyHandler(output -> {
                if (responseIsOk) {
                    future.complete(output.toJsonObject());
                } else {
                    future.fail(output.toString());
                }
            });
        });
        if (token != null) {
            request.headers().add("Authorization", "Bearer " + token);
        }
        if (input != null) {
            request.write(input);
        }
        request.exceptionHandler(exception -> future.fail(exception));
        request.end();

        return future;
    }
}
