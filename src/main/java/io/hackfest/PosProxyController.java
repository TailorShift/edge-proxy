package io.hackfest;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Path("/pos")
public class PosProxyController {
    private static final Logger logger = LoggerFactory.getLogger(PosProxyController.class);

    @ConfigProperty(name = "posmanager.url")
    String destination;

    @Inject
    SignatureService signatureService;

    private final WebClient webClient = WebClient.create(Vertx.vertx());

    private RequestOptions buildRequestOptions(
            HttpHeaders headers,
            UriInfo uriInfo
    ) throws SignatureException, InvalidKeyException {
        RequestOptions options = new RequestOptions();

        // Rewrite url to pos-manager
        UriBuilder uriBuilder = UriBuilder.fromUri(destination)
                .segment(uriInfo.getPathSegments().stream().map(PathSegment::getPath).toArray(String[]::new));

        uriInfo.getQueryParameters().forEach((key, values) -> {
            uriBuilder.queryParam(key, values.toArray());
        });
        String absoluteURI = uriBuilder.build().toString();
        options.setAbsoluteURI(absoluteURI);

        // Keep headers
        headers.getRequestHeaders().forEach((key, values) -> {
            values.forEach(value -> options.addHeader(key, value));
        });

        // Add crypto headers ensuring device identity
        signatureService.buildCryptoHeaders().applyTo(options);

        return options;
    }

    @GET
    @Path("{subPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public String proxyGet(
            @PathParam("subPath") String subPath,
            HttpHeaders headers,
            UriInfo uriInfo
    ) throws ExecutionException, InterruptedException, TimeoutException, InvalidKeyException, SignatureException {
        RequestOptions options = buildRequestOptions(headers, uriInfo);

        logger.info("GET passthrough to url: {}", options.getURI());

        // Send request
        var result = webClient.request(HttpMethod.GET, options)
                .send()
                .toCompletionStage()
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);

        // Pass through successes, fail otherwise
        if (result.statusCode() >= 200 && result.statusCode() < 400) {
            return result.bodyAsString();
        } else {
            logger.error("Request failed with {}. Response: {}", result.statusCode(), result.bodyAsString());
            throw new WebApplicationException(Response.status(result.statusCode()).build());
        }
    }

    @POST
    @Path("{subPath:.*}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public String proxyPostFormUrlEncoded(
            @PathParam("subPath") String subPath,
            HttpHeaders headers,
            // Quarkus, why is there no way to get this as Map<String, String>??, https://github.com/quarkusio/quarkus/discussions/25103
            String body,
            UriInfo uriInfo
    ) throws ExecutionException, InterruptedException, TimeoutException, InvalidKeyException, SignatureException {
        RequestOptions options = buildRequestOptions(headers, uriInfo);

        MultiMap map = MultiMap.caseInsensitiveMultiMap();
        // Oh god please kill me. There is no better way in Quarkus right now
        Arrays.stream(body.split("&")).forEach(keyValue -> {
            String[] kvArray = keyValue.split("=");
            map.add(kvArray[0], kvArray[1]);
        });

        logger.info("POST (application/x-www-form-urlencoded) passthrough to url: {}", options.getURI());

        // Send request
        var result = webClient.request(HttpMethod.POST, options)
                .sendForm(map)
                .toCompletionStage()
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);

        // Pass through successes, fail otherwise
        if (result.statusCode() >= 200 && result.statusCode() < 400) {
            return result.bodyAsString();
        } else {
            logger.error("Request failed with {}. Response: {}", result.statusCode(), result.bodyAsString());
            throw new WebApplicationException(Response.status(result.statusCode()).build());
        }
    }

    @POST
    @Path("{subPath:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String proxyPostJSON(
            @PathParam("subPath") String subPath,
            HttpHeaders headers,
            JsonObject body,
            UriInfo uriInfo
    ) throws ExecutionException, InterruptedException, TimeoutException, InvalidKeyException, SignatureException {
        RequestOptions options = buildRequestOptions(headers, uriInfo);

        // Send request
        var result = webClient.request(HttpMethod.POST, options)
                .sendJsonObject(body)
                .toCompletionStage()
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);

        logger.info("POST (application/json) passthrough to url: {}", options.getURI());

        // Pass through successes, fail otherwise
        if (result.statusCode() >= 200 && result.statusCode() < 400) {
            return result.bodyAsString();
        } else {
            logger.error("Request failed with {}. Response: {}", result.statusCode(), result.bodyAsString());
            throw new WebApplicationException(Response.status(result.statusCode()).build());
        }
    }
}