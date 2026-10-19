package com.absolutebonkers.apidesign.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookApiServerTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private BookApiServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new BookApiServer(new BookCatalog());
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void createsAResourceAndReturnsItsLocation() throws Exception {
        HttpResponse<String> response = send("POST", server.baseUri(),
                "{\"title\":\"Domain-Driven Design\",\"author\":\"Eric Evans\"}", "application/json");

        assertEquals(201, response.statusCode());
        assertEquals(server.baseUri() + "/1", response.headers().firstValue("Location").orElseThrow());
        assertEquals("Domain-Driven Design", json.readTree(response.body()).get("title").asText());
    }

    @Test
    void retrievesAResourceAndReportsMissingIds() throws Exception {
        send("POST", server.baseUri(), "{\"title\":\"API Design\",\"author\":\"Example\"}", "application/json");

        assertEquals(200, send("GET", URI.create(server.baseUri() + "/1"), null, null).statusCode());
        assertEquals(404, send("GET", URI.create(server.baseUri() + "/9"), null, null).statusCode());
    }

    @Test
    void validatesMethodMediaTypeJsonAndFields() throws Exception {
        assertEquals(405, send("DELETE", server.baseUri(), null, null).statusCode());
        assertEquals(415, send("POST", server.baseUri(), "{}", "text/plain").statusCode());
        assertEquals(400, send("POST", server.baseUri(), "{", "application/json").statusCode());
        HttpResponse<String> invalid = send("POST", server.baseUri(),
                "{\"title\":\"  \",\"author\":\"Example\"}", "application/json");
        assertEquals(422, invalid.statusCode());
    }

    @Test
    void negotiatesJsonAndBoundsInput() throws Exception {
        HttpRequest notAcceptingJson = HttpRequest.newBuilder(server.baseUri())
                .header("Accept", "text/plain").GET().build();
        assertEquals(406, client.send(notAcceptingJson, HttpResponse.BodyHandlers.ofString()).statusCode());
        HttpRequest explicitlyExcludingJson = HttpRequest.newBuilder(server.baseUri())
                .header("Accept", "application/json;q=0").GET().build();
        assertEquals(406, client.send(explicitlyExcludingJson, HttpResponse.BodyHandlers.ofString()).statusCode());
        HttpRequest exclusionMustOverrideWildcard = HttpRequest.newBuilder(server.baseUri())
                .header("Accept", "application/json;q=0, */*;q=1").GET().build();
        assertEquals(406, client.send(exclusionMustOverrideWildcard, HttpResponse.BodyHandlers.ofString()).statusCode());

        HttpResponse<String> large = send("POST", server.baseUri(), " ".repeat(16_385), "application/json");
        assertEquals(413, large.statusCode());

        JsonNode response = json.readTree(send("GET", server.baseUri(), null, null).body());
        assertTrue(response.isArray());
        assertEquals("application/json", send("GET", server.baseUri(), null, null)
                .headers().firstValue("Content-Type").orElseThrow().split(";")[0]);
    }

    private HttpResponse<String> send(String method, URI uri, String body, String contentType) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri);
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
