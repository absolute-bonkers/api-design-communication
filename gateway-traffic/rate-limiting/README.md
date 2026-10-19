# Rate limiting and throttling

The token bucket allows a bounded burst and replenishes capacity at a steady
rate. A rejected request receives a retry delay; separate client keys get
independent buckets. Bucket updates are synchronized so concurrent requests
cannot consume the same token twice.

```mermaid
flowchart LR
    Request["Client request"] --> Bucket["Token bucket"]
    Bucket -->|token available| Allow["Allow and consume token"]
    Bucket -->|empty| Deny["429 Too Many Requests"]
    Deny --> Retry["Retry-After guidance"]
    Clock["Monotonic clock"] --> Bucket
```

This is a single-process teaching implementation. A production deployment
needs shared/distributed state or deliberate per-instance limits, bounded key
cardinality, policy configuration, and a documented response contract.

Example: [`TokenBucketRateLimiter`](src/main/java/com/absolutebonkers/apidesign/ratelimit/TokenBucketRateLimiter.java) ·
Tests: [`TokenBucketRateLimiterTest`](src/test/java/com/absolutebonkers/apidesign/ratelimit/TokenBucketRateLimiterTest.java).

Run with `./gradlew :gateway-traffic:rate-limiting:test`.
