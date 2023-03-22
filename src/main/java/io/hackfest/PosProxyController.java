package io.hackfest;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.jboss.resteasy.reactive.server.WithFormRead;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Path("/pos")
public class PosProxyController {

    private final String destination = "http://localhost:8081";
    private final String DEVICE_ID = UUID.randomUUID().toString();

    @Inject
    private SignatureService signatureService;

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
        options.setAbsoluteURI(uriBuilder.build().toString());

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
            throw new WebApplicationException(Response.status(result.statusCode()).build());
        }
    }

    @POST
    @Path("{subPath:.*}")
    @WithFormRead
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

        // Pass through successes, fail otherwise
        if (result.statusCode() >= 200 && result.statusCode() < 400) {
            return result.bodyAsString();
        } else {
            throw new WebApplicationException(Response.status(result.statusCode()).build());
        }
    }
}