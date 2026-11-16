<p><img src="fabriclab/store/src/main/resources/static/images/mark.png" alt="FABRICLAB mark" width="72"></p>

# FABRICLAB

[中文版](README.md)

The implementation of Lab 1 for the Software Development Architecture and Platform course: two independent Spring Boot applications sharing one MySQL database, covering product browsing, accounts, cart, orders, mock payment, pre-purchase inquiries, and administration.

The mark is built from interlocking pattern pieces. Brand colors: Ink `#1A1A1A`, Ecru `#F2EFE9`, Clay `#B4643A`. Product image sources and licenses are listed in the [database notes](fabriclab/store/src/main/resources/db/README.md).

## Environments

| Component | Version | Notes |
|---|---|---|
| JDK | Temurin 25.0.5+8 | Compile and runtime target `java.version=25` |
| Maven | 3.9.16 | Build tool |
| Spring Boot | 3.5.16 | Application framework for both apps |
| MyBatis-Plus | 3.5.18 | Data access |
| MySQL | 8.4 (port 3306) | Demo database `FABRICLAB`, test database `FABRICLAB_TEST` |
| Thymeleaf | Bundled with Spring Boot | Server-side rendering, no front-end build step |

## Run

One-time database preparation (creating the databases, the connection account, and importing the schema plus the two seed scripts) is documented in the [database and seed data notes](fabriclab/store/src/main/resources/db/README.md). The customer-service extension script is idempotent by thread ID and message body; without it only the service page is unavailable.

Connection settings are baked into both `application.yml` defaults (database `FABRICLAB`, account `fabriclab` / `fabriclab`, port 3306), so starting needs no environment variables:

```bash
cd fabriclab/store && mvn spring-boot:run   # storefront · 8080
cd fabriclab/admin && mvn spring-boot:run   # admin · 8081
```

| URL | Purpose |
|---|---|
| `http://localhost:8080/main` | Storefront home |
| `http://localhost:8081/admin` | Admin entry; redirects to sign-on when logged out |

Demo accounts (password `Passw0rd!2026` for all, local teaching only):

| Account | Role | Purpose |
|---|---|---|
| `alice`, `bob` | Customer | Cart and order demo data |
| `disabled` | Customer (disabled) | Sign-in blocking scenario |
| `admin` | Administrator | Admin sign-on |

Tests run against the isolated database `FABRICLAB_TEST` (pom default in both projects); integration tests need the connection credentials:

```bash
export MYSQL_USER=fabriclab
export MYSQL_PASSWORD=fabriclab
cd fabriclab/store && mvn test
cd fabriclab/admin && mvn test
```

Never point tests at the demo database `FABRICLAB`: integration tests clear and rebuild carts, orders, and other fixtures.

## Features

**Storefront · 8080**

- Browsing: `/main`, `/category`, `/series/{id}`; browse by series and category, with search, sorting, and pagination.
- Products: `/product?id=`, `/fabrics`, `/crafts`. Product records contain the style number, fabric, weight, fit, and stitching craft. Series, fabric, and craft pages are generated from the same product data.
- Accounts: registration, sign-on, server-side captcha, BCrypt, session checks, and an allowlist for return paths.
- Cart and orders: the cart is stored in the database; checkout verifies a server-side snapshot; orders are managed by state; cancellation restores stock through conditional updates.
- Mock payment: the amount comes only from `sales_order.total_amount`. Failures write nothing, and repeated success keeps the first receipt.
- Customer service: a standalone entry in the top navigation, `/service` as a single page. Guests see FAQs; members start and view inquiries.

**Admin · 8081**

- Products: categories, style numbers, listing controls, albums, covers, and stock.
- Customers: profile maintenance, password resets, and status changes. A role or status change invalidates active sessions.
- Orders: lookup by order number, state filters, delivery edits, conditional shipping, and restricted deletion.
- Customer service: the `/admin/service` workbench with inquiry list, detail, reply, close, and reopen.
- Language: interface copy and product content support Chinese and English.

Customer service handles pre-purchase questions only. The admin console does not display business metrics that are not measured.

## Technical conventions

- 7 course business tables plus 2 customer-service extension tables. Course-required table and column names and requirement IDs are retained; visible pages, copy, sample data, and comments use apparel semantics.
- `STORESESSION` and `ADMINSESSION` are cookie-only. Every POST is checked for CSRF; protected routes recheck role and status on each request.
- Money uses `BigDecimal`; order items keep transaction snapshots. Payment and cancellation are mutually exclusive through conditional updates; restocking uses `stock + version` to avoid overwrites.
- `CheckoutFaultPoint` and `CancelFaultPoint` are empty in production and are used only in acceptance scenarios.

## Verification

| Item | Result |
|---|---|
| Storefront automated tests | 284 passing, 0 skipped |
| Admin automated tests | 138 passing, 0 skipped |
| End-to-end browser walkthrough | 50 / 50 passing, no JS errors and no 4xx / 5xx responses |
| Live database acceptance | AC-01 to AC-32 mapped to V01 to V18 with records |
| Concurrency | AC-28 verified with two running applications |

The walkthrough covers a full purchase, payment, cancellation, service handoff, admin product, customer, order, and service flows, language switching, registration, and access protection. AC-16 and AC-23 are reproduced only through their dedicated fault-injection points.

## Layout

| Path | Contents |
|---|---|
| `fabriclab/store/` | Storefront application, templates, styles, images, tests |
| `fabriclab/admin/` | Admin application, bilingual copy, admin tests |
| `fabriclab/docs/` | Admin design, admin visual specification, service design |
| `01-schema.sql` | Course schema script (database name `MyPetStore`, replace with `FABRICLAB` on import) |
| `02-schema-with-demo.sql` | Course comparison script, not executed |
| `实验一实施方案.md` | Task breakdown and delivery checklist |
| `MyPetStore-PRD.pdf` | Course requirement specification |
| `MyPetstore用户端参考UI/` | Course UI reference |

All code lives under `fabriclab/`; the IntelliJ project root is `fabriclab/`, with configuration in `fabriclab/.idea/`.

## Documentation

- [Storefront notes](fabriclab/store/README.md)
- [Admin notes](fabriclab/admin/README.md)
- [Database and seed data](fabriclab/store/src/main/resources/db/README.md)
- [Admin design](fabriclab/docs/admin-design.md)
- [Service design](fabriclab/docs/service-design.md)
- [Lab 1 implementation plan](实验一实施方案.md)
