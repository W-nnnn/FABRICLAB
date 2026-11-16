<p><img src="fabriclab/store/src/main/resources/static/images/mark.png" alt="FABRICLAB mark" width="72"></p>

# FABRICLAB

[Chinese edition](README.md)

FABRICLAB is the implementation of Lab 1 for the Software Development Architecture and Platform course. It consists of two independent Spring Boot applications sharing one MySQL database, covering product browsing, accounts, cart, orders, mock payment, pre-purchase inquiries, and administration.

## Brand

The transparent mark is based on two interlocking pattern pieces. Colors: Ink `#1A1A1A`, Ecru `#F2EFE9`, Clay `#B4643A`.

Product image sources and licenses are listed in the [database notes](fabriclab/store/src/main/resources/db/README.md).

## Features

**Storefront · 8080**

- Browsing: `/main`, `/category`, `/series/{id}`; browse by series and category, with search, sorting, and pagination.
- Products: `/product?id=`, `/fabrics`, `/crafts`. Product records contain the style number, fabric, weight, fit, and stitching craft. Series, fabric, and craft pages are generated from the same product data.
- Accounts: registration, sign-on, server-side captcha, BCrypt, session checks, and an allowlist for return paths.
- Cart and orders: the cart is stored in the database; checkout verifies a server-side snapshot; orders are managed by state; cancellation restores stock through conditional updates.
- Mock payment: the amount comes only from `sales_order.total_amount`. Failures write nothing, and repeated success keeps the first receipt.
- Customer service: a standalone entry in the top navigation, `/service` as a single page. Guests see FAQs; members start and view inquiries. Service is kept separate from the account center.

**Admin · 8081**

- Products: categories, style numbers, listing controls, albums, covers, and stock.
- Customers: profile maintenance, password resets, and status changes. A role or status change invalidates active sessions.
- Orders: lookup by order number, state filters, delivery edits, conditional shipping, and restricted deletion.
- Customer service: the `/admin/service` workbench with inquiry list, detail, reply, close, and reopen.
- Language: interface copy and product content support Chinese and English.

Customer service handles pre-purchase questions only. The admin console does not display business metrics that are not measured.

## Technical conventions

- JDK 25, Maven 3.9.16, Spring Boot 3.5.16, MyBatis-Plus 3.5.18.
- MySQL 8.4 (local instance on 3306), demo database `FABRICLAB`, test database `FABRICLAB_TEST`, with 7 course business tables and 2 customer-service extension tables. Course-required table and column names and requirement IDs are retained; visible pages, copy, sample data, and comments use apparel semantics.
- Thymeleaf server-side rendering, with no front-end build step.
- `STORESESSION` and `ADMINSESSION` are cookie-only. Every POST is checked for CSRF; protected routes recheck role and status on each request.
- Money uses `BigDecimal`; order items keep transaction snapshots. Payment and cancellation are mutually exclusive through conditional updates; restocking uses `stock + version` to avoid overwrites.
- `CheckoutFaultPoint` and `CancelFaultPoint` are empty in production and are used only in acceptance scenarios.

## Run

Requirements: JDK 25, Maven 3.9.16, and MySQL 8.4.

One-time database preparation (creating `FABRICLAB` / `FABRICLAB_TEST`, the connection account, and importing the schema and the two seed scripts) is documented in the [database notes](fabriclab/store/src/main/resources/db/README.md). The customer-service extension script is idempotent by thread ID and message body. Without it, only the service page is unavailable.

Connection settings are baked into both `application.yml` defaults (database `FABRICLAB`, account `fabriclab/fabriclab`), so starting needs no environment variables:

```bash
cd fabriclab/store && mvn spring-boot:run
cd fabriclab/admin && mvn spring-boot:run
```

| URL | Purpose |
|---|---|
| `http://localhost:8080/main` | Storefront home |
| `http://localhost:8081/admin` | Admin entry; redirects to sign-on when logged out |

The demo password is `Passw0rd!2026`, for local teaching only. `alice` and `bob` are customers, `disabled` is a blocked example, and `admin` is the administrator.

Run the tests:

```bash
export MYSQL_USER=fabriclab
export MYSQL_PASSWORD=fabriclab
cd fabriclab/store && mvn test
cd fabriclab/admin && mvn test
```

Both projects pin their test database to `FABRICLAB_TEST` (pom default). Integration tests need the connection password; without it the affected cases are skipped. Never point tests at the demo database `FABRICLAB`: integration tests clear and rebuild orders, carts, and other fixtures.

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
| `01-schema.sql` | Course schema script (database name `MyPetStore`, replaced with `FABRICLAB` on import) |
| `02-schema-with-demo.sql` | Course comparison script, not executed |
| `MyPetStore-PRD.pdf` | Course requirement specification |
| Course UI reference directory | Original course mockups |

Course handouts with Chinese filenames remain in the repository root. All code lives under `fabriclab/`; the IntelliJ project root is `fabriclab/`, with configuration in `fabriclab/.idea/`.

## Documentation

- [Storefront notes](fabriclab/store/README.md)
- [Admin notes](fabriclab/admin/README.md)
- [Database and seed data](fabriclab/store/src/main/resources/db/README.md)
- [Admin design](fabriclab/docs/admin-design.md)
- [Service design](fabriclab/docs/service-design.md)
