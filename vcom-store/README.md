# vcom-store – Spring Boot microservices + Kafka + React

Java 21 · Spring Boot 3.3 · Spring Cloud 2023.0 · Kafka (KRaft) · PostgreSQL · Redis · React 18 (Vite)

| Module | Port | Data | Responsibility |
|---|---|---|---|
| discovery-server | 8761 | – | Eureka |
| api-gateway | 8080 | – | Routing, JWT check, forwards X-User-Id / X-User-Role |
| auth-service | 8081 | authdb | Register/login, JWT; publishes `user.registered` |
| product-service | 8082 | productdb | Product CRUD; publishes `product.updated` |
| store-service | 8083 | storedb | One store per merchant (name, slug, theme); public store pages |
| inventory-service | 8084 | inventorydb | Stock levels, reservations, compensation |
| cart-service | 8085 | Redis | Per-user cart; cleared on `order.confirmed` |
| order-service | 8086 | orderdb | Order lifecycle (saga state machine) |
| payment-service | 8087 | paymentdb | Payments through a `PaymentGateway` (mock included), commission, subscription plans |
| frontend | 5173 | – | Storefront, cart, checkout, orders, merchant dashboard, store pages |

## Multi-tenant model (each merchant has their own store and customers)
- **Merchants** sign up at `/merchant/register`, create a store, and manage their own products, stock, orders and customers at `/dashboard`.
- **Customers** belong to one merchant. They sign up and sign in at that merchant's storefront (`/store/:slug`) and can only shop that merchant's products. The same email can have separate accounts in different stores.
- The JWT carries a `tenant` claim (the merchant id). The gateway forwards it as `X-Tenant-Id`, and order-service rejects any product that isn't sold by the customer's own store.
- Merchants only see their own customers (`GET /api/auth/customers`) and their own order lines.
- Merchant sign-in uses the pseudo-tenant `00000000-0000-0000-0000-000000000000`, so a merchant email is unique platform-wide.

## Subscription plans (platform owner billing)
The owner (role `ADMIN`) earns from **subscription plans** and from **commission** on sales.
- **Plans:** created and priced at `/admin` (name, price, monthly or yearly, commission %, product limit, shown or hidden). Three example plans (Starter free, Growth, Pro) are created on first start; their prices are placeholders, so edit them.
- **New merchant flow:** register at `/merchant/register`, then `/merchant/onboarding`: step 1 choose a plan, step 2 store and billing details (name, address, GSTIN, mobile) with an order summary, then pay in Razorpay Checkout. Free plans open the store immediately. The home page shows the plans publicly.
- **After paying:** the store opens to customers, the merchant gets the plan's commission rate (copied when they pay, so later plan edits don't change what they agreed to) and its product limit (enforced when adding products).
- **Period and renewal:** a paid plan lasts one month or year and does **not** renew by itself. Renewing the same plan from the dashboard adds a period to the end of the current one. When the period ends the plan expires and the store closes to customers until it is renewed; the check runs every minute. Free plans never expire.
- **Without an active plan:** the merchant can still prepare up to 5 products (`app.billing.unsubscribed-max-products`) but the store stays closed, and the default commission set at `/admin` applies.
- Create the owner login by setting `ADMIN_EMAIL` and `ADMIN_PASSWORD` before starting auth-service, then sign in at `/merchant/login`; the owner lands on `/admin`.

Not covered yet: automatic recurring billing (Razorpay Subscriptions with mandates), renewal reminders by email, pro-rated upgrades and refunds, subscription invoices and GST on the fee, and a grace period before the store closes.

## Store admins (staff per merchant)
A merchant (store owner) can add store admins at the bottom of `/dashboard`. A store admin signs in at `/merchant/login` and acts for the owner's store:

| | Owner | Store admin |
|---|---|---|
| Products, stock, orders, customers | yes | yes |
| Store settings, plan, earnings, activation | yes | no |
| Add/remove store admins | yes | no |

Staff accounts carry the owner's id as their tenant, and services use the `X-Tenant-Id` header to decide whose data to touch, so a store admin can only ever reach their own store's data. Removing a staff member deletes the account, but an already-issued token stays valid until it expires (120 minutes by default).

## Managing customers, orders and invoices (merchant and store admins)
From `/dashboard`:
- **Orders** (`/dashboard/orders`): search and filter (to ship, shipped, delivered, cancelled), see who bought and where to ship, and move a paid order forward: packed, shipped (with an optional tracking note), delivered. Customers see the delivery status on their order page.
- **Customers** (`/dashboard/customers`): everyone who signed up to the store, with paid orders, total spent and last order; expand a customer to see their orders.
- **Invoices** (`/dashboard/invoices`): one invoice is issued automatically when an order is paid, numbered per merchant (`INV-000001`, ...). Open it to print or save as PDF. Customers can open the same invoice from their order.
- Business address and GSTIN printed on invoices come from **Store settings**. The seller details are copied onto each invoice when it is issued, so later edits don't change old invoices.

Not covered yet: tax calculation (the invoice shows the amount charged, with no GST breakup, so it is not a complete GST tax invoice), refunds and credit notes, shipping fees, and emailing invoices (a notification service would send them).

## Delivery tracking
- **Customer:** the order page shows a timeline (placed, confirmed, packed, shipped, delivered) with timestamps, the courier, a tracking number that links to the courier's page when the merchant adds one, the expected delivery date, and an activity log. It refreshes every 30 seconds until the parcel is delivered.
- **Merchant / store admin:** at `/dashboard/orders`, open **Delivery** on a paid order. Mark it packed; mark it shipped with courier, tracking number, optional tracking link and expected date; post in-transit updates ("Out for delivery"); then mark it delivered. Search also matches tracking numbers.
- Every change is written to the order's timeline and published to Kafka as `order.fulfillment`, ready for a notification service to email or SMS the customer.
- Tracking is entered by the merchant. There is no live courier API integration yet (for example Shiprocket or a courier webhook that updates the timeline automatically).

## Razorpay payments
Customers pay for orders, and merchants pay for their subscription plan, through Razorpay Standard Checkout.

**How it works**
1. Checkout creates the order and reserves stock. For method `ONLINE`, payment-service creates a Razorpay order on the server (the amount always comes from our database, never the browser) and the order page opens Razorpay Checkout.
2. After the customer pays, Checkout returns a signature. The server recomputes `HMAC-SHA256(razorpay_order_id|razorpay_payment_id, key secret)` using **its own stored order id** and only then marks the payment paid.
3. Razorpay's signed webhook (`payment.captured` / `order.paid`) confirms the same payment. This covers a customer who closes the tab after paying. Both paths are idempotent.
4. A customer who doesn't pay within 15 minutes (or presses Cancel order) has the order cancelled and the stock released.
5. Merchants pay for their plan the same way: choose a plan, pay in Checkout, then the store opens.
6. Cash on delivery is unchanged. With no Razorpay keys set, online payments use the development mock gateway so everything still runs locally.

**Set it up**
1. In the Razorpay dashboard (test mode first), generate an API Key ID and Secret. Set `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` for payment-service (compose passes them through).
2. In Razorpay settings, turn on **automatic payment capture**, otherwise payments stay authorised and are not captured.
3. Add a webhook (Settings, Webhooks) pointing to `https://<your public gateway>/api/payments/webhooks/razorpay`, choose the events `payment.captured` and `order.paid`, and set a secret. Put that secret in `RAZORPAY_WEBHOOK_SECRET`. The webhook secret is different from the API secret, and test and live modes have separate webhooks and secrets.
4. On your own machine Razorpay can't reach `localhost`, so use a tunnel (for example ngrok) to the gateway on port 8080 for the webhook. The browser callback works without one.
5. Use Razorpay's test card, UPI and netbanking details from their documentation to try it, then switch to live keys.

**Where the money goes:** all payments land in your (the platform owner's) Razorpay account. Commission and the merchant's share are recorded on each payment, but paying merchants out is still manual. Razorpay Route (transfers to merchants' linked accounts) would automate it, but it has to be enabled on your account and each merchant must be onboarded as a linked account.

**Not built yet:** refunds (including the rare payment that arrives after an order was cancelled; it is flagged "refund needed" on the payment), payout automation, and saved cards.

## Sales reports
- **Store owner:** `/dashboard/reports`. **Platform owner:** `/admin/reports`.
- Pick a preset (this or last month, this or last quarter, this or last financial year, all time) or any custom date range, then break it down **daily, monthly, quarterly or annually**. Totals for the selected period sit above the chart and table, with a bar chart, a table with a totals row, a split by payment method, CSV download and print.
- The store owner sees gross sales, platform commission and their own earnings. The platform owner sees gross sales, commission, subscription revenue, total revenue, average order value and the top merchants for the period.
- Quarters and years follow the **financial year, April to March**, and dates use the `Asia/Kolkata` time zone. Change `app.reports.fiscal-year-start-month` (1 for calendar years) and `app.reports.zone` in payment-service. Store admins (staff) can't open the money reports.
- Sales are dated by when they were paid. Orders from before this feature fall back to the payment record's creation time.
- Not included yet: refunds, GST breakup, product-wise or customer-wise sales, and scheduled emailed reports.

## Accounts: mobile number, forgot and reset password
- **Registration** (merchants and each store's customers) asks for name, email, an Indian mobile number and a password of at least 8 characters. Numbers are stored as `+91XXXXXXXXXX`, and like emails they are unique within one tenant.
- **Sign in** with either the email or the mobile number.
- **Forgot password** (`/merchant/forgot-password`, `/store/:slug/forgot-password`): the user enters their mobile number or email and gets a 6-digit code (sent by SMS to the account's number, or by email if it has none). Codes last 10 minutes, allow 5 wrong tries, and can be requested once a minute and 5 times an hour. The request always gets the same reply, so it cannot be used to find out which accounts exist. Customers reset within their own store only.
- **Change password** for signed-in users: `/account/password` (merchants) and `/store/:slug/account/password` (customers).
- The mobile number is also kept on each order so merchants can contact the customer for delivery, and it prefills Razorpay Checkout.

**Delivery of the code:** out of the box the code is only written to the auth-service log (`[DEV MESSAGE]`), so nothing reaches a phone. For production, add a `MessageSender` bean that calls your SMS provider (and an email service for accounts without a number) and set `app.messaging.provider` to its name. In India, transactional SMS normally needs the sender and message template registered with the provider (DLT), so check your provider's requirements. Never log codes in production.

Not covered yet: verifying a mobile number when registering (anyone can enter any number), signing in with a one-time code instead of a password, and signing out other devices after a password change (a token issued earlier stays valid until it expires, 120 minutes by default).

## Order saga (Kafka)
```
POST /api/orders ─► order-service saves PENDING ─► order.created
  inventory-service: reserve all lines or none ─► inventory.reserved | inventory.failed
  payment-service (on inventory.reserved)      ─► payment.completed | payment.failed
  order-service:  inventory.reserved  → STOCK_RESERVED
                  payment.completed   → CONFIRMED ─► order.confirmed ─► cart-service clears cart
                  inventory.failed / payment.failed → CANCELLED
  inventory-service: payment.failed → releases the reserved stock (compensation)
                     payment.completed → marks the reservation COMMITTED
```
Prices are read from product-service at order time, never taken from the browser.
Handlers are idempotent (reservation per order, one payment per order, guarded status transitions).

## Run locally

### Option A: your own PostgreSQL, Kafka and Redis, services run from Maven/IDE (simplest)
Defaults already point at `localhost`, so no Docker is needed.
1. Create the databases once: `psql -U postgres -f infra/local-postgres-setup.sql`
2. Make sure Kafka is on `localhost:9092` and Redis on `localhost:6379`.
3. Start in order: `discovery-server`, `api-gateway`, then the other services (`mvn -pl <module> spring-boot-run` or IDE).
4. `cd frontend && npm install && npm run dev`

### Option B: your own PostgreSQL, Kafka and Redis, services in Docker
```bash
psql -U postgres -f infra/local-postgres-setup.sql
docker compose --env-file .env.local-infra --profile apps up -d --build
```
Containers reach your machine through `host.docker.internal` (edit `.env.local-infra` for your passwords). Three things to check on your side:
- **Kafka** advertises `localhost:9092`, which a container can't use. Add a second listener in `server.properties` and restart Kafka:
  ```
  listeners=PLAINTEXT://:9092,DOCKER://:29092
  advertised.listeners=PLAINTEXT://localhost:9092,DOCKER://host.docker.internal:29092
  listener.security.protocol.map=PLAINTEXT:PLAINTEXT,DOCKER:PLAINTEXT
  ```
  (keep your existing controller/KRaft lines). The env file already points at port 29092.
- **PostgreSQL** must accept connections from Docker: `listen_addresses = '*'` in `postgresql.conf` and a `host all all 0.0.0.0/0 scram-sha-256` line in `pg_hba.conf` (narrow it to Docker's subnet if you prefer), then restart.
- **Redis** must not be bound to 127.0.0.1 only: set `bind 0.0.0.0` (and a password, put it in `REDIS_PASSWORD`).

Do not mix: run either every service in Docker or every service on your machine. A service in a container registers with Eureka under a container IP that your machine can't reach, and the reverse.

### Option C: everything in Docker, including Postgres/Kafka/Redis
```bash
docker compose --profile infra --profile apps up -d --build
```
Kafka UI is at http://localhost:8090. Use `docker compose down -v` to reset all data.

On Windows with services run outside Docker, set `EUREKA_INSTANCE_PREFERIPADDRESS=true` so the gateway can resolve them.

## Try the flow
1. Open `/merchant/register`, create a merchant account, then your store; add a product with stock (e.g. 3).
2. Open your storefront link (`/store/your-store`), create a customer account there, add the product to the cart, check out.
3. Watch the order go Checking stock → Processing payment → Confirmed, and the cart empty itself.
4. Check out with "Card that will be declined": the order is cancelled and the stock returns.
5. Order more than the stock: the order is cancelled for lack of stock.

## Before production
- Real `JWT_SECRET` (32+ bytes) for gateway and auth-service; only the gateway should be public.
- Flyway migrations instead of `ddl-auto: update`.
- Swap `MockPaymentGateway` for Razorpay/Stripe, with webhook signature verification.
- Outbox pattern: today a service commits to its DB and then sends to Kafka, so a crash in between can lose an event.
- Dead-letter topics and retry policy for the Kafka listeners; notification-service (email/SMS) on `order.confirmed` and `user.registered`.
