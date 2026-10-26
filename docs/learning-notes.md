# flashdrop: Learning Notes

Questions and answers from building flashdrop, organized by phase. Each answer is written to be useful as interview prep: the concept, a diagram where it helps, and a one-line "interview answer" at the end.

> Diagrams use [Mermaid](https://mermaid.js.org/), which GitHub renders automatically. In IntelliJ, enable the Mermaid option in the Markdown plugin settings to see them in the preview.

## Contents

- [Phase 0: Setup and Foundations](#phase-0-setup-and-foundations)
  - [Q1. What goes wrong when two users buy the last item at the same time?](#q1-what-goes-wrong-when-two-users-buy-the-last-item-at-the-same-time)
  - [Q2. How does the code know which user won the atomic UPDATE?](#q2-how-does-the-code-know-which-user-won-the-atomic-update)
  - [Q3. How do Docker port mappings work, and how do I find a port?](#q3-how-do-docker-port-mappings-work-and-how-do-i-find-a-port)
  - [Q4. How does a property name like `spring.data.redis.host` turn into YAML?](#q4-how-does-a-property-name-like-springdataredishost-turn-into-yaml)
  - [Q5. Why package by feature instead of by layer?](#q5-why-package-by-feature-instead-of-by-layer)
  - [Q6. What is Hibernate, and why use Flyway with `ddl-auto: validate` instead of `update`?](#q6-what-is-hibernate-and-why-use-flyway-with-ddl-auto-validate-instead-of-update)
  - [Q7. What happens if I add an entity field but forget the migration?](#q7-what-happens-if-i-add-an-entity-field-but-forget-the-migration)
- [Phase 1: Domain Model](#phase-1-domain-model)
  - [Q8. Should stock live on Product or in a separate Inventory table?](#q8-should-stock-live-on-product-or-in-a-separate-inventory-table)
  - [Q9. Why not FLOAT/DOUBLE for money?](#q9-why-not-floatdouble-for-money)
  - [Q10. Why add CHECK constraints if Java already validates?](#q10-why-add-check-constraints-if-java-already-validates)
  - [Q11. TIMESTAMPTZ vs TIMESTAMP: why does the time zone matter?](#q11-timestamptz-vs-timestamp-why-does-the-time-zone-matter)
  - [Q12. Why not store times as epoch numbers?](#q12-why-not-store-times-as-epoch-numbers)
  - [Q13. Why no `IF NOT EXISTS` in Flyway migrations?](#q13-why-no-if-not-exists-in-flyway-migrations)
  - [SQL patterns cheat sheet](#sql-patterns-cheat-sheet)
- [Mistakes Log](#mistakes-log)

---

## Phase 0: Setup and Foundations

### Q1. What goes wrong when two users buy the last item at the same time?

```java
Inventory inv = repo.findById(id);
if (inv.getStock() > 0) {
    inv.setStock(inv.getStock() - 1);
    repo.save(inv);
}
```

**Answer.** Both requests read `stock = 1` before either one writes. Both pass the `if` check, both compute `1 - 1 = 0`, and both save `0`. The stock ends at **0, not -1**, so the database looks healthy, but **two items were sold when only one existed**. That's what makes the bug dangerous: nothing looks wrong until two customers both expect the item.

```mermaid
sequenceDiagram
    participant A as Thread A (User 1)
    participant DB as Database (stock = 1)
    participant B as Thread B (User 2)
    A->>DB: SELECT stock
    DB-->>A: 1
    B->>DB: SELECT stock
    DB-->>B: 1
    Note over A,B: Both check "1 > 0" and both pass
    A->>DB: UPDATE stock = 0
    B->>DB: UPDATE stock = 0  (overwrites A's write)
    Note over DB: stock = 0, but 2 orders were created for 1 item
```

**Vocabulary**

| Term | Meaning |
|---|---|
| Race condition | The result depends on the timing of threads |
| Check-then-act | The check (`if`) and the action (update) are separate steps, so another thread can run in between them |
| Lost update | One write silently overwrites another write that was based on the same old value |

**Three fixes** (built and benchmarked in Phase 2)

| Strategy | How it works | What the losing request experiences |
|---|---|---|
| Pessimistic lock | `SELECT ... FOR UPDATE` locks the row until the transaction ends | It waits, then reads `stock = 0` |
| Optimistic lock | A `@Version` column; the update succeeds only if the version is unchanged | Its save fails, so it retries or gives up |
| Atomic conditional update | `UPDATE ... SET stock = stock - 1 WHERE id = ? AND stock > 0` | It updates 0 rows, which means sold out |

> **Interview answer:** "It's a check-then-act race that causes a lost update. Both threads read the same stale value, so we oversell while the stock still looks correct. I'd fix it with a pessimistic lock, an optimistic version check, or an atomic conditional UPDATE, depending on how much contention there is."

---

### Q2. How does the code know which user won the atomic UPDATE?

**Answer.** An `UPDATE` returns the **number of rows it changed**. The database applies the two updates to the row one at a time. The first one matches `stock > 0` and changes 1 row. The second one sees `stock = 0`, matches nothing, and changes 0 rows.

```mermaid
flowchart LR
    A[User A request] --> U["UPDATE inventory<br/>SET stock = stock - 1<br/>WHERE id = 42 AND stock > 0"]
    B[User B request] --> U
    U -->|"A arrives first: 1 row updated"| W[A gets the item and an order is created]
    U -->|"B arrives second: 0 rows updated"| L[B gets SoldOutException]
```

```java
@Modifying
@Query("UPDATE Inventory i SET i.stock = i.stock - 1 WHERE i.id = :id AND i.stock > 0")
int decrementIfAvailable(@Param("id") Long id);   // returns rows affected

int updated = inventoryRepo.decrementIfAvailable(id);
if (updated == 0) {
    throw new SoldOutException(id);
}
```

**Why it works:** the check and the write happen in **one atomic statement inside the database**. There's no gap between them for another request to slip into.

> **Interview answer:** "I push the check into the WHERE clause so the database enforces it atomically, and I use the affected-row count to decide whether this request won."

---

### Q3. How do Docker port mappings work, and how do I find a port?

**Answer.** In `docker-compose.yml`, `ports` uses the format **`HOST:CONTAINER`**.

```yaml
ports:
  - "6379:6379"
#    ↑      ↑
#  laptop  container
```

```mermaid
flowchart LR
    subgraph Laptop
        APP["Spring Boot app<br/>connects to localhost:6379"]
    end
    subgraph Docker
        R["Redis container<br/>listens on 6379 inside"]
    end
    APP -->|"host port 6379 is mapped to container port 6379"| R
```

The app connects to the **left** (host) number. With `"6380:6379"`, the app would use `localhost:6380`, while Redis still listens on 6379 inside the container.

**Three ways to find a port**

1. Read the `ports:` entry in `docker-compose.yml`.
2. Run `docker compose ps` and look at the PORTS column, for example `0.0.0.0:6379->6379/tcp`.
3. Know the defaults:

| Service | Default port |
|---|---|
| Redis | 6379 |
| PostgreSQL | 5432 |
| MongoDB | 27017 |
| Kafka | 9092 |
| Spring Boot | 8080 |

**Related gotchas**
- Editing `docker-compose.yml` doesn't start anything. Run `docker compose up -d` afterwards.
- `./mvnw spring-boot:run` keeps running in the foreground. Run `curl` from a **second terminal** while the app is up.

---

### Q4. How does a property name like `spring.data.redis.host` turn into YAML?

**Answer.** Each dot becomes one level of indentation, and **only the value** goes after the last colon. The property name never appears as text inside the YAML. **The indentation is the name.**

```mermaid
flowchart LR
    P["spring.data.redis.host = localhost"] --> S["spring:"]
    S --> D["data:"]
    D --> R["redis:"]
    R --> H["host: localhost"]
```

| `.properties` (one line) | YAML (nested) |
|---|---|
| `spring.data.redis.host=localhost`<br/>`spring.data.redis.port=6379` | <pre>spring:<br/>  data:<br/>    redis:<br/>      host: localhost<br/>      port: 6379</pre> |

**Rules**
- Indent with spaces, never tabs. Siblings must line up exactly.
- `host` is only the machine name (`localhost`). The port goes on its own line.
- A wrong path such as `spring.redis.host` doesn't crash the app. Spring Boot just **ignores** it and uses defaults, so the app can look like it works while your config is being ignored.

---

### Q5. Why package by feature instead of by layer?

```
By layer                          By feature (flashdrop)
├── controllers/                  ├── catalog/
│   ├── OrderController           ├── inventory/
│   └── InventoryController       │   ├── InventoryController
├── services/                     │   ├── InventoryService
│   ├── OrderService              │   ├── InventoryRepository
│   └── InventoryService          │   └── Inventory
└── repositories/                 ├── order/
    ├── OrderRepository           ├── payment/
    └── InventoryRepository       └── common/
```

```mermaid
flowchart TB
    subgraph order["order module"]
        OC[OrderController] --> OS[OrderService]
        OS --> OR[OrderRepository]
    end
    subgraph inventory["inventory module"]
        IS["InventoryService (public API)"] --> IR["InventoryRepository (package-private)"]
    end
    OS -->|"allowed: public service"| IS
    OS -.->|"blocked: package-private internals"| IR
```

**Answer.**
1. **High cohesion.** Everything about a feature lives in one folder. A change to order logic touches one place instead of four folders.
2. **Encapsulation.** Classes can be package-private (no `public` keyword), so other modules have to go through the feature's public service and can't reach into its internals.
3. **Easy extraction.** Moving `payment/` into its own microservice (Phase 4) is roughly "move one folder". With package-by-layer, payment code would be spread across every layer folder.

Smaller files are a side benefit, but layered packaging also gives you separate files, so that isn't the main reason.

> **Interview answer:** "I package by feature for high cohesion and clear module boundaries. That keeps changes local and lets each module be extracted into a service later."

---

### Q6. What is Hibernate, and why use Flyway with `ddl-auto: validate` instead of `update`?

**What Hibernate is.** JPA is the Java standard for mapping classes to database tables. **Hibernate is the library that implements JPA**, and it comes with `spring-boot-starter-data-jpa`. It reads `@Entity` classes and generates the SQL for `save()`, `findById()` and so on.

```java
@Entity
class Inventory {
    @Id Long id;
    int stock;      // maps to the inventory.stock column
}
```

**What `ddl-auto` controls:** whether Hibernate may create or change tables.

| Value | What Hibernate does |
|---|---|
| `create` / `create-drop` | Drops and recreates tables on every start (all data lost) |
| `update` | Adds whatever is missing compared with the entities |
| `validate` | Changes nothing. Checks that tables match the entities and refuses to start if they don't |
| `none` | Does nothing |

**Why not `update`?**
- **It only adds and never cleans up.** Renaming `stock` → `quantity` adds a new empty column and leaves the old one, with its data, behind.
- **It can't migrate data**, for example filling a new column from existing rows.
- **There's no history and no review.** Environments drift apart, and nobody knows what changed when.
- **It's unsafe in production.** Teams don't let an app change production tables on startup.

**Who does what in flashdrop**

```mermaid
flowchart LR
    M["db/migration/<br/>V1__create_inventory.sql<br/>V2__add_reserved.sql"] -->|"1. Flyway runs pending migrations"| DB[(PostgreSQL)]
    DB --- H["flyway_schema_history<br/>(created by Flyway automatically)"]
    E["@Entity classes"] -->|"2. Hibernate validates entities against tables"| DB
    E -->|mismatch| F["App refuses to start"]
    E -->|match| OK["App starts"]
```

**Do I need to create a table to track migrations?** No. Flyway creates `flyway_schema_history` automatically on first run. It records which `V…` files have already been applied, so each migration runs exactly once.

> **Interview answer:** "Schema changes are versioned SQL migrations in Git, run by Flyway and reviewed in PRs. Hibernate is set to `validate`, so if an entity doesn't match the schema, the app fails fast at startup instead of in production."

---

### Q7. What happens if I add an entity field but forget the migration?

**Scenario:** with `ddl-auto: validate`, you add `private int reserved;` to `Inventory` but don't write a Flyway migration.

**Answer.** The app **refuses to start**, with an error like:

```
Schema-validation: missing column [reserved] in table [inventory]
```

The important part is **when** it fails: at startup, on your laptop, within seconds. Not in production, when a customer's request finally reaches that code path. That's the "fail fast" principle.

---

## Phase 1: Domain Model

### Q8. Should stock live on Product or in a separate Inventory table?

**Answer.** In a separate `inventory` table, **one row per drop**. The same product (a sneaker) can be released in several drops (500 units in March, 1000 in June), so stock belongs to the drop, not the product.

```mermaid
erDiagram
    PRODUCTS ||--o{ DROPS : "released as"
    DROPS ||--|| INVENTORY : "has stock"
    DROPS ||--o{ ORDERS : "receives"
    PRODUCTS {
        bigint id PK
        varchar name
        varchar brand
        text description
        varchar image_url
        timestamptz created_at
    }
    DROPS {
        bigint id PK
        bigint product_id FK
        varchar name
        numeric price
        timestamptz starts_at
        timestamptz ends_at
        int max_per_user
        varchar status
        timestamptz created_at
    }
    INVENTORY {
        bigint drop_id PK, FK
        int total_units
        int available_units
    }
    ORDERS {
        bigint id PK
        bigint drop_id FK
        bigint user_id
        int quantity
        numeric unit_price
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }
```

**The deeper reason: separate data that changes often from data that rarely changes.**

| | Product | Inventory |
|---|---|---|
| Describes | *What* is sold | *How many* are left |
| Change rate | Rarely | Thousands of writes per second during a drop |
| Access pattern | Read-heavy, cacheable | Write-heavy, contended, needs locking |

If `stock` lived on `Product`, every purchase would lock the product row, and even viewing the product page would compete with buyers. Splitting them is Single Responsibility at the data level. It also lets `Product` move to MongoDB later while `Inventory` stays in Postgres for ACID guarantees.

> **Interview answer:** "I separate frequently written data from rarely written data. Inventory is a contended, write-heavy row per drop, while the product is read-heavy and cacheable, so locking one never blocks the other."

---

### Q9. Why not FLOAT/DOUBLE for money?

**Answer.** Floating-point numbers are stored in **binary**, and most decimal values (like `0.1`) have no exact binary representation, just as 1/3 has no exact decimal form.

```java
System.out.println(0.1 + 0.2);   // 0.30000000000000004
```

Across millions of transactions, these errors add up to real money discrepancies.

| Layer | Use | Never use |
|---|---|---|
| PostgreSQL | `NUMERIC(10, 2)`: exact, 10 digits with 2 after the decimal point | `REAL`, `DOUBLE PRECISION` |
| Java | `BigDecimal` | `double`, `float` |

> **Interview answer:** "Floats are binary approximations, so decimal amounts like 0.1 can't be represented exactly and rounding errors accumulate. I use NUMERIC in the database and BigDecimal in Java."

---

### Q10. Why add CHECK constraints if Java already validates?

**Answer.** Java validation only protects the code paths that go through that Java code. A database constraint protects against **every** writer.

```mermaid
flowchart LR
    A["Endpoint with validation"] --> DB
    B["Future endpoint with a bug (no validation)"] --> DB
    C["Payment service (Phase 4)"] --> DB
    D["Manual SQL fix in production"] --> DB
    E["Two concurrent requests (check-then-act race)"] --> DB
    DB[("PostgreSQL<br/>CHECK (available_units >= 0)")] -->|"any invalid write"| X["Rejected"]
```

The race-condition case matters most here. Java's `if (available >= 1)` is a check-then-act, so two threads can both pass it. The database CHECK is evaluated atomically at write time.

| Layer | Purpose |
|---|---|
| Java validation | Friendly error messages, fast feedback |
| Database constraints | Guaranteed integrity: the last line of defence |

> **Interview answer:** "Application validation gives good errors. Database constraints guarantee integrity no matter who writes or how requests interleave. I use both."

---

### Q11. TIMESTAMPTZ vs TIMESTAMP: why does the time zone matter?

**Answer.** `TIMESTAMPTZ` stores an **exact moment in time**. `TIMESTAMP` stores only a **wall-clock reading** with no zone attached.

| Type | What "10:00" means | Risk |
|---|---|---|
| `TIMESTAMP` | "10:00", but in which zone? | The server runs in UTC, an admin enters IST, and the drop opens 5.5 hours late |
| `TIMESTAMPTZ` | Converted to UTC on write (10:00 IST is stored as 04:30 UTC) and converted back to the session zone on read | Everyone agrees on the same instant |

```mermaid
flowchart LR
    IN["Admin in India enters<br/>2026-10-26 10:00 +05:30"] --> S[("Stored as<br/>2026-10-26 04:30 UTC")]
    S --> IND["User in India sees 10:00 IST"]
    S --> UK["User in London sees 04:30 GMT"]
```

Note: `TIMESTAMPTZ` doesn't store the zone itself. It converts everything to UTC. In Java, map it to `Instant` (or `OffsetDateTime`).

> **Interview answer:** "I store instants as TIMESTAMPTZ, which normalizes to UTC, so a drop opens at the same moment for every user regardless of server or client time zone."

---

### Q12. Why not store times as epoch numbers?

Epoch means time stored as a number, for example `1793001600000` milliseconds since 1970.

| | `BIGINT` epoch | `TIMESTAMPTZ` |
|---|---|---|
| Readable in SQL | No: `1793001600000` | Yes: `2026-10-26 10:00:00+05:30` |
| Date math (`now() - interval '1 day'`) | Convert first | Built in |
| Type safety | Any number is accepted. Is it seconds or milliseconds? Mixing them up puts dates 1000× off | Only valid timestamps |
| Java mapping | `long` | `Instant`, mapped automatically by JPA |
| Storage | 8 bytes | 8 bytes |

**Where epoch is the right choice:** systems that only understand numbers. In Phase 5, the Redis waiting room uses a **sorted set whose score is the join time in epoch millis**, because sorted sets sort by numeric score.

> **Interview answer:** "In the database I use the native timestamp type for readability, date math and type safety. I use epoch where the system only understands numbers, like Redis sorted-set scores."

---

### Q13. Why no `IF NOT EXISTS` in Flyway migrations?

**Answer.** Flyway already guarantees that each migration runs **exactly once**: it records applied files in `flyway_schema_history` and skips them afterwards. Flyway makes migrations idempotent at the tool level.

`IF NOT EXISTS` would **hide bugs**:

```mermaid
flowchart TB
    S["Someone manually created a wrong 'drops' table in production"] --> M{"Migration V1 runs"}
    M -->|"CREATE TABLE IF NOT EXISTS drops"| Q["Skipped silently. V1 marked successful.<br/>App runs on the wrong schema and nobody knows"]
    M -->|"CREATE TABLE drops"| L["Fails loudly: relation 'drops' already exists.<br/>Problem found immediately"]
```

**Where `IF NOT EXISTS` is useful:** scripts run by hand, or setup scripts that no migration tool tracks.

> **Interview answer:** "Flyway tracks applied versions, so each migration runs exactly once. I avoid IF NOT EXISTS because it would mask schema drift. I'd rather fail fast."

---

### SQL patterns cheat sheet

```sql
-- Auto-generated primary key
id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

-- Foreign key: this row belongs to an existing row in another table
product_id  BIGINT NOT NULL REFERENCES products(id),

-- Primary key that is also a foreign key (1:1 relationship)
drop_id  BIGINT PRIMARY KEY REFERENCES drops(id),

-- Exact money
price  NUMERIC(10, 2) NOT NULL CHECK (price > 0),

-- Default value plus a rule
max_per_user  INTEGER NOT NULL DEFAULT 1 CHECK (max_per_user > 0),

-- Only allowed values (enum-like)
status  VARCHAR(20) NOT NULL CHECK (status IN ('SCHEDULED', 'LIVE', 'ENDED', 'CANCELLED')),

-- Rule across two columns: its own line after the columns
CHECK (ends_at > starts_at)

-- Composite index for frequent lookups
CREATE INDEX idx_orders_user_drop ON orders (user_id, drop_id);
```

**Syntax rules**
- Every line inside `( ... )` ends with a comma, except the last.
- `;` goes only after the closing `)`.
- A foreign key links rows ("this drop belongs to product 5"). It never copies another column's definition.
- Don't put `DEFAULT now()` on business times such as `starts_at`. They must be set deliberately.

---

## Mistakes Log

Real mistakes made during the build, kept so they aren't repeated.

| Phase | Mistake | Fix | Lesson |
|---|---|---|---|
| 0 | Mixed indentation in the compose `redis:` block | Services at 2 spaces, properties at 4 | YAML structure is defined by indentation |
| 0 | `container name` (with a space) | `container_name` | Compose keys are exact |
| 0 | Healthcheck copied from Mongo (`--eval db.adminCommand`) | `["CMD", "redis-cli", "ping"]` | After copy-pasting, check that every line makes sense for the new service |
| 0 | `"redis-cli ping"` as one list item | `"redis-cli", "ping"` | In exec form, each word of the command is its own item |
| 0 | Redis volume at `/data/db` (Mongo's path) | `/data` | Every image has its own data directory |
| 0 | Wrote the property name as the YAML value | Nested keys with real values | The indentation is the name; only the value goes after the colon |
| 0 | `spring.redis` (missing `data:`) | `spring.data.redis` | Wrong paths are silently ignored, not reported |
| 0 | `host: localhost:6379` | `host: localhost`, plus a separate `port: 6379` | Host and port are separate settings |
| 0 | Ran curl while the app wasn't running | App in terminal 1, curl in terminal 2 | `spring-boot:run` runs in the foreground |
| 1 | Foreign-key column with no type (`product_id FOREIGN AS products.id`) | `product_id BIGINT NOT NULL REFERENCES products(id)` | Every column needs a type. A foreign key is written with `REFERENCES` |
| 1 | `price VARCHAR` | `NUMERIC(10, 2)` with `CHECK (price > 0)` | Text can't be compared numerically or used in math. Use exact decimals for money |
| 1 | `DEFAULT now()` on `starts_at` / `ends_at` | No default; the times must be given | A forgotten value would start the drop immediately |
| 1 | `ends_at` declared twice | Declare once, and add `CHECK (ends_at > starts_at)` | Postgres rejects duplicate columns |
| 1 | `DEFAULT 1 AND > 0;` | `DEFAULT 1 CHECK (max_per_user > 0),` | Rules go in `CHECK (...)`, and `;` would end the whole statement |
| 1 | Missing comma after `status`, and no allowed values | `CHECK (status IN (...)),` | Every column line needs a comma except the last |
| 1 | `created_at FOREIGN AS products.created_at` | `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` | A foreign key links rows; it doesn't copy a column definition |
| 1 | `inventory.drop_id` without `PRIMARY KEY` | `drop_id BIGINT PRIMARY KEY REFERENCES drops(id)` | Without it, one drop could have two stock counters: overselling built into the schema |
| 1 | `CHECK (available_units > 0 AND available_units < total_units)` | `>= 0 AND <= total_units` | Off-by-one: a fresh drop (500/500) and selling the last unit (0) were both rejected. Run boundary value analysis on every constraint |
| 1 | `orders.drop_id REFERENCES products(id)` | `REFERENCES drops(id)` | Point each foreign key at the table the row actually belongs to |
| 1 | `drop_id` copied from the `id` line (`GENERATED ... PRIMARY KEY`) | `drop_id BIGINT NOT NULL REFERENCES drops(id)` | One primary key per table. `GENERATED` is only for the row's own id, never for a pointer |
| 1 | `REFERENCES` on `unit_price` and `created_at` | No foreign key; `unit_price` is a snapshot copy | Only `_id` columns get `REFERENCES` |
| 1 | `CREATE INDEX` written inside `CREATE TABLE` | A separate statement after `);`: `CREATE INDEX idx_orders_user_drop ON orders (user_id, drop_id);` | An index is its own statement, with a name and `ON table` |
| 1 | `=<` | `<=` | Comparison operators are written less-than or greater-than first: `<=`, `>=` |
| 1 | Leftover `KEY` after deleting `PRIMARY KEY` | Remove it | Re-read the whole line after editing it |
| 1 | Comma after the last column | No comma before `);` | Every line inside the table ends with a comma except the last |

**Takeaway from Phase 1 schema work:** "It ran without errors" isn't the same as "it's correct". Syntax errors appear immediately; logic errors only appear with data at the boundaries. Test valid and invalid rows against every constraint.
