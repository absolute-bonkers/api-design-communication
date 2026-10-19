# Gateway and traffic control

An API gateway is a carefully bounded edge that routes client requests toward
services. Traffic controls protect those services; centralizing them should
not hide important service behavior from operators.

```mermaid
flowchart LR
    Client["API client"] --> Gateway["API Gateway"]
    Gateway --> Route{"Route"}
    Route --> Catalog["Catalog service"]
    Route --> Orders["Orders service"]
    Gateway --> Limit["Rate limit policy"]
    Limit -. protects .-> Catalog
    Limit -. protects .-> Orders
```

| Topic | Use it to learn |
| --- | --- |
| [API Gateway](api-gateway/README.md) | Route selection, path forwarding, shared credentials, and boundary behavior. |
| [Rate limiting](rate-limiting/README.md) | A thread-safe token bucket, burst capacity, refill, and retry guidance. |

Return to the [Module 5 map](../README.md).
