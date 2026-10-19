package com.absolutebonkers.apidesign.versioning;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiVersionNegotiator {
    private static final Pattern URI_VERSION = Pattern.compile("^/api/v([0-9]+)(?:/.*)?$");
    private static final Pattern VENDOR_TYPE = Pattern.compile(
            "^application/vnd\\.absolute-bonkers\\.orders\\.v([0-9]+)\\+json$",
            Pattern.CASE_INSENSITIVE);
    private static final Instant V1_DEPRECATED = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant V1_SUNSET = Instant.parse("2027-01-01T00:00:00Z");
    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

    public ApiVersion fromPath(String path) {
        if (path == null) {
            throw new ApiVersionException(400, "API version must be present in the URI path");
        }
        Matcher matcher = URI_VERSION.matcher(path);
        if (!matcher.matches()) {
            throw new ApiVersionException(400, "API version must be present in the URI path");
        }
        return supportedVersion(matcher.group(1));
    }

    public ApiVersion fromAccept(String accept) {
        if (accept == null || accept.isBlank()) {
            throw new ApiVersionException(406, "Accept must specify a supported API version");
        }
        ApiVersion resolved = null;
        double bestQuality = 0;
        for (String range : accept.split(",")) {
            String[] parts = range.trim().split(";", 2);
            Matcher matcher = VENDOR_TYPE.matcher(parts[0].trim());
            double quality = quality(parts);
            if (!matcher.matches() || quality <= 0) {
                continue;
            }
            Optional<ApiVersion> supported = findSupportedVersion(matcher.group(1));
            if (supported.isEmpty()) {
                continue;
            }
            ApiVersion candidate = supported.get();
            if (quality > bestQuality
                    || (quality == bestQuality && (resolved == null || candidate.number() > resolved.number()))) {
                resolved = candidate;
                bestQuality = quality;
            }
        }
        if (resolved == null) {
            throw new ApiVersionException(406, "Accept must specify a supported API version");
        }
        return resolved;
    }

    public Map<String, String> responseHeaders(ApiVersion version) {
        if (!version.deprecated()) {
            return Map.of();
        }
        return Map.of(
                "Deprecation", "@" + V1_DEPRECATED.getEpochSecond(),
                "Sunset", HTTP_DATE.format(V1_SUNSET),
                "Link", "</api/v2/orders>; rel=\"successor-version\"");
    }

    private static ApiVersion supportedVersion(String value) {
        return findSupportedVersion(value)
                .orElseThrow(() -> new ApiVersionException(404, "Unsupported API version v" + value));
    }

    private static Optional<ApiVersion> findSupportedVersion(String value) {
        int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        return Arrays.stream(ApiVersion.values())
                .filter(version -> version.number() == number)
                .findFirst();
    }

    private static double quality(String[] parts) {
        if (parts.length == 1) {
            return 1.0;
        }
        for (String parameter : parts[1].split(";")) {
            String[] pair = parameter.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equalsIgnoreCase("q")) {
                try {
                    double quality = Double.parseDouble(pair[1]);
                    return Double.isFinite(quality) && quality >= 0.0 && quality <= 1.0 ? quality : 0.0;
                } catch (NumberFormatException exception) {
                    return 0.0;
                }
            }
        }
        return 1.0;
    }
}
