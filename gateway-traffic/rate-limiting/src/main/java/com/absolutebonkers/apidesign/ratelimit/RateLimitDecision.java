package com.absolutebonkers.apidesign.ratelimit;

import java.time.Duration;

public record RateLimitDecision(boolean allowed, int remaining, Duration retryAfter) {}
