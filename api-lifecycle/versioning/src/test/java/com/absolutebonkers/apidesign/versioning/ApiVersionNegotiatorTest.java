package com.absolutebonkers.apidesign.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ApiVersionNegotiatorTest {
    private final ApiVersionNegotiator negotiator = new ApiVersionNegotiator();

    @Test
    void resolvesUriAndVendorMediaTypeVersions() {
        assertEquals(ApiVersion.V2, negotiator.fromPath("/api/v2/orders"));
        assertEquals(ApiVersion.V1, negotiator.fromAccept(
                "application/vnd.absolute-bonkers.orders.v1+json"));
    }

    @Test
    void marksDeprecatedVersionsAndLeavesCurrentVersionsUnmarked() {
        assertEquals("true", negotiator.responseHeaders(ApiVersion.V1).get("Deprecation"));
        assertEquals("Fri, 01 Jan 2027 00:00:00 GMT",
                negotiator.responseHeaders(ApiVersion.V1).get("Sunset"));
        assertEquals("</api/v2/orders>; rel=\"successor-version\"",
                negotiator.responseHeaders(ApiVersion.V1).get("Link"));
        assertEquals(java.util.Map.of(), negotiator.responseHeaders(ApiVersion.V2));
    }

    @Test
    void rejectsMissingUnsupportedAndUnversionedRequests() {
        assertEquals(400, assertThrows(ApiVersionException.class,
                () -> negotiator.fromPath("/api/orders")).statusCode());
        assertEquals(404, assertThrows(ApiVersionException.class,
                () -> negotiator.fromPath("/api/v3/orders")).statusCode());
        assertEquals(406, assertThrows(ApiVersionException.class,
                () -> negotiator.fromAccept("application/json")).statusCode());
        assertEquals(406, assertThrows(ApiVersionException.class,
                () -> negotiator.fromAccept("application/vnd.absolute-bonkers.orders.v3+json")).statusCode());
        assertEquals(ApiVersion.V2, negotiator.fromAccept(
                "application/vnd.absolute-bonkers.orders.v1+json, "
                        + "application/vnd.absolute-bonkers.orders.v2+json"));
        assertEquals(ApiVersion.V2, negotiator.fromAccept(
                "application/vnd.absolute-bonkers.orders.v3+json, "
                        + "application/vnd.absolute-bonkers.orders.v2+json;q=0.5"));
    }

    @Test
    void ignoresExplicitlyUnacceptableVersionRanges() {
        assertEquals(ApiVersion.V2, negotiator.fromAccept(
                "application/vnd.absolute-bonkers.orders.v1+json;q=0, "
                        + "application/vnd.absolute-bonkers.orders.v2+json;q=1"));
    }
}
