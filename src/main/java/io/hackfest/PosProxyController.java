package io.hackfest;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.client.WebClient;

import javax.inject.Inject;
import javax.ws.rs.GET;
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
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
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

    @GET
    @Path("{subPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public String hello(
            @PathParam("subPath") String subPath,
            HttpHeaders headers,
            UriInfo uriInfo

    ) throws ExecutionException, InterruptedException, TimeoutException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException, KeyStoreException {
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
}