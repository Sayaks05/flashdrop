# flashdrop

A flash-sale "drop" platform: thousands of buyers try to grab a few hundred units the moment a drop opens, and the system must never oversell, never double-charge, and stay up under the spike.

Built to explore concurrency control (pessimistic and optimistic locking, atomic updates), idempotency, event-driven design with Kafka, and high- and low-level system design.

## Stack

| Piece | Used for |
|---|---|
| Java 25 + Spring Boot 4.1 | Backend (modular monolith, split into services later) |
| PostgreSQL | Orders, inventory, payments: the source of truth |
| Redis | Waiting room, rate limiting, fast stock counters, idempotency keys |
| Kafka | Order → payment → confirmation pipeline |
| MongoDB | Product catalog and audit log |

## Run locally

Prerequisites: Docker (OrbStack or Docker Desktop) and JDK 25+.

```bash
docker compose up -d          # start Postgres, Redis, Kafka, Mongo
cd backend && ./mvnw spring-boot:run
curl localhost:8080/actuator/health
```

## Roadmap

- [ ] Phase 0: project skeleton and local infrastructure
- [ ] Phase 1: catalog, drops, inventory, orders (SOLID, layered design)
- [ ] Phase 2: concurrency (overselling demo, pessimistic, optimistic and atomic strategies, benchmarks)
- [ ] Phase 3: idempotency
- [ ] Phase 4: Kafka order/payment saga, outbox pattern
- [ ] Phase 5: waiting room and rate limiting
- [ ] Phase 6: MongoDB catalog, audit log, notifications
- [ ] Phase 7: UI, live updates, k6 load tests
- [ ] Phase 8: deployment
