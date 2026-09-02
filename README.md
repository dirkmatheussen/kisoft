# KNAPP KiSoft One Mock Server

A mock of the **KNAPP KiSoft One** warehouse-control-system (WCS) API. It lets a higher-level host system (WMS/ERP) develop and test its KiSoft One integration **without** a running KiSoft One installation: it accepts the same HTTP requests, validates them like KiSoft One would, keeps state, drives the order lifecycles, and sends back the same asynchronous reply/event messages.

- **Deployed base URL:** `https://wispelberg.eu/kisoft`
- **Local base URL:** `http://localhost:8084/kisoft`
- **Interactive API docs (Swagger UI):** [`https://wispelberg.eu/kisoft/swagger-ui.html`](https://wispelberg.eu/kisoft/swagger-ui.html)

The exposed API is deliberately limited to the **message subset in scope for VOLVO TRUCKS Tacoma**. Out-of-scope KiSoft One messages (Outbound Delivery, Storage Order, Crossdocking, Master-Data Route/Partner, Relocation requests, Direct Control, Delete Inbound Delivery, multiphase picking, …) are intentionally **not** exposed. KiSoft One also exposes **no GET** endpoints (HIS Appendix §2.3.1); this mock adds optional OData GET reads for inspection only — see [Mock OData read (NOT KiSoft API)](#mock-odata-read-endpoints-not-kisoft-api).

---

## Reference documents

This mock is built against three KNAPP specifications. Where they disagree, the **HIS Appendix** wins.

| Document | What it defines | How the mock uses it |
|----------|-----------------|----------------------|
| **Product One API** — *Product One API V2.11.3* (HTML, incl. JSON message schema) | The complete, product-level KiSoft One interface: every operation, path and field. | Source of the real `operationId`s, URL paths and payload shapes. |
| **HOST Interface Specification – One API Appendix** — *P000-013061* (the "HIS Appendix") | The customer/project-specific **scope**: which messages and data fields are used, allowed values, and reply triggers. Takes priority over the Product One API. | Defines exactly which endpoints are exposed and which fields are validated. Section references (e.g. §7.4) in this README point here. |
| **General Specification** — *P000-013061* (the "GS") | The logistics / warehouse-process specification (goods-in, goods-out, inventory, …). | **Chapter 5 (Warehouse processes)** is implemented on the KiSoft side — the status transitions and reply/event messages each process produces (multiphase picking §5.2.2 excluded). GS references (e.g. §5.2.3) point here. |

---

## How the mock works (for developers)

KiSoft One communicates in **two directions**, and so does this mock:

1. **HOST → KiSoft One (synchronous HTTP):** the host calls the KiSoft-scoped endpoints below (`POST`/`PUT`/`PATCH`/`DELETE` under `{base}/oneapi/v1/…`). The mock validates the request and answers immediately with a small JSON acknowledgement (`{"http":200,"status":"OK",...}`) or an error (`{"codes":["E-AKO-…"],...}`). **Separate mock-only OData GET endpoints** exist for inspection but are **not part of the KiSoft API** — see [Mock OData read (NOT KiSoft API)](#mock-odata-read-endpoints-not-kisoft-api).

2. **KiSoft One → HOST (asynchronous webhooks):** as orders progress, the mock POSTs **reply / event messages** back to the host. You tell the mock where to send them with `knapp.mock.reply-callback-url`; each message is delivered to `{reply-callback-url}/{messageName}` (see [Reply webhooks](#reply-webhooks-kisoft-one--host)).

The mock is **stateful**. Master data, deliveries and orders are stored in a **persistent H2 file database** (`./data/kisoftmock.mv.db` by default) and survive process restarts. Each order carries a `processingStatus` that moves through a lifecycle. Real KiSoft One advances that status as automation and warehouse operators do their work; since the mock has no warehouse, it exposes **mock "operator" endpoints** that you call to advance an order and trigger the corresponding webhooks (see [Order lifecycles](#order-lifecycles--mock-operator-endpoints)).

Override the DB file location with env `KNAPP_H2_FILE` (e.g. `/opt/knapp-kisoft-mock/data/kisoftmock`) or `SPRING_DATASOURCE_URL`. The `test` profile still uses an in-memory H2 database.

Typical integration loop:

```
HOST  --PutPackUnit-------------------->  KiSoft (master data)
HOST  --PostInboundDelivery------------>  KiSoft           (status NEW)
HOST  <-PostInboundDeliveryReply-------  KiSoft            (NEW)
        … operator start / receive …
HOST  <-PostStockReceived--------------  KiSoft            (per load unit)
HOST  <-PostInboundDeliveryReply-------  KiSoft            (FINISHED)
```

### Conventions

- **`clientNumber`** identifies the warehouse client; transmit `DEFAULT` unless told otherwise.
- **`businessCase`** groups work: `GOODS_IN`, `GOODS_OUT`, `INVENTORY`, `RELOCATION`.
- **`processingStatus`** values: `NEW`, `STARTED`, `PROCESSED`, `FINISHED`, `CANCELLED` (multiphase-only `MP_WAIT`/`MP_RELEASED` are not used here).
- **Reply payloads** omit empty fields (`null` values are not serialized).

---

## Authentication

### Swagger UI and homepage

The homepage (`/`) and Swagger UI (`/swagger-ui.html`) are protected with **HTTP Basic Auth** when the server runs without the `dev` or `test` profile.

| Setting | Default | Description |
|---------|---------|-------------|
| `knapp.mock.ui-auth-enabled` | `true` | Set `false` to disable UI login (done automatically in `dev`/`test`) |
| `knapp.mock.ui-username` | `knapp` | Basic-auth username |
| `knapp.mock.ui-password` | *(required)* | Set via `MOCK_UI_PASSWORD` environment variable |

Your browser will prompt for username and password when you open Swagger UI or the homepage.

### KiSoft API (`/oneapi/**`)

Every request to `{base}/oneapi/**` must carry an OAuth 2.0 Bearer token (Microsoft Entra ID):

```
Authorization: Bearer <token>
```

The token is **required but not validated** — any non-empty Bearer token is accepted (this is a mock). For local development you can disable the check entirely with the `dev` profile (`knapp.mock.bypass-auth=true`).

---

## Running locally

Requirements: **Java 17+**, **Maven 3.8+**.

```bash
# Development — no Bearer token or UI login required
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Production-like — UI login required; webhook credentials are in application.yml
export MOCK_UI_PASSWORD='choose-a-strong-password'
mvn spring-boot:run
```

Then open the homepage at `http://localhost:8084/kisoft/` (this page) or Swagger UI at `http://localhost:8084/kisoft/swagger-ui.html`.

---

## API endpoints — HOST → KiSoft One

All paths are relative to the base URL, e.g. `https://wispelberg.eu/kisoft/oneapi/v1/packUnit`.

| operationId | Method | Path (`{base}` + …) | Description |
|-------------|--------|---------------------|-------------|
| `PostPackUnitUpdateSession` | POST | `/oneapi/v1/packUnit/updateSession` | Open (`SET`) / close (`CLEANUP`) a master-data update session (§6.1). Optional — `PutPackUnit` can be used standalone. |
| `PutPackUnit` | PUT | `/oneapi/v1/packUnit` | Create or update article/pack-unit master data (batch ≤ 10 000 items). |
| `DeletePackUnit` | DELETE | `/oneapi/v1/packUnit` | Delete pack units. Rejected with `E-AKO-STOC-0002` while the ASRS still holds inventory for the part. |
| `PostInboundDelivery` | POST | `/oneapi/v1/inboundDelivery` | Create an inbound delivery (goods-in, §7.1). |
| `PatchInboundDelivery` | PATCH | `/oneapi/v1/inboundDelivery` | Update an inbound delivery — only while `NEW`, otherwise **409** (`E-AKO-MOVM-0005`). |
| `PostGoodsOutOrder` | POST | `/oneapi/v1/goodsOutOrder` | Create a goods-out (picking) order (§7.4). Invalid lines → HTTP **400** with Product One API `lineCode`s (see below). |
| `PatchGoodsOutOrder` | PATCH | `/oneapi/v1/goodsOutOrder` | Update a goods-out order — only while `NEW`, otherwise **409**. |
| `PostInventoryRequest` | POST | `/oneapi/v1/inventoryRequest` | Create an inventory (cycle-count) request (§7.2). |
| `DeleteInventoryRequest` | DELETE | `/oneapi/v1/inventoryRequest` | Delete an inventory request. |
| `PostRequestInventoryReport` | POST | `/oneapi/v1/requestInventoryReport` | Request an inventory report (§8.2). The report is delivered as a reply webhook. |
| `PostRequestStorageCapacityReport` | POST | `/oneapi/v1/requestStorageCapacityReport` | Request a storage-capacity report (§8.3). Delivered as a reply webhook. |

A goods-out order is keyed by `clientNumber` + `orderNumber` + `sheetNumber` and refers to exactly **one** shipping load unit; the host performs all order splitting/cubing before transmission.

**Goods-out planning (GS §5.2.1):** `PostGoodsOutOrder` / `PatchGoodsOutOrder` (add lines) reject invalid lines synchronously with HTTP **400** and Product One API `lineCode`s in `lineCodes`:

| Condition | `lineCode` |
|-----------|------------|
| Unknown article/packSize | `E-AKO-MAST-0001` |
| Not enough ASRS stock | `E-AKO-STOC-0001` |
| Zero or negative `requestedQuantity` | `E-AKO-GENR-0002` |
| Article locked or taken off sale (`articleFeatures`: `ARTICLE_IS_LOCKED` / `TAKEN_OFF_SALE`) | `E-AKO-GENR-0001` |

Accepted orders return HTTP 200; `PostGoodsOutOrderReply` (status `NEW`) reports all lines as `UNTOUCHED`.

---

## Mock OData read endpoints (NOT KiSoft API)

> **Not part of the KiSoft One Product API or HIS Appendix.** KiSoft One exposes **no GET** requests (HIS Appendix §2.3.1). The endpoints below are **mock-only** helpers so you can inspect stored master data, orders and ASRS stock during development and test. They do **not** exist on a real KiSoft installation — do not use them in production WMS integration against KiSoft.
>
> In Swagger UI they appear under the tag **`Mock OData Read (NOT KiSoft API)`**, separate from the KiSoft-scoped operations.

These GET endpoints return stored state in **OData v4 JSON** format:

```json
{
  "@odata.context": "/kisoft/oneapi/v1/$metadata#PackUnits",
  "@odata.count": 1,
  "value": [ … ]
}
```

| operationId | GET path | `value` item type |
|-------------|----------|-------------------|
| `GetPackUnits` | `/oneapi/v1/packUnit` | `PackUnitFull` |
| `GetInboundDeliveries` | `/oneapi/v1/inboundDelivery` | `{ processingStatus, inboundDelivery }` |
| `GetGoodsOutOrders` | `/oneapi/v1/goodsOutOrder` | `{ processingStatus, goodsOutOrder }` |
| `GetInventoryItems` | `/oneapi/v1/inventoryItem` | `{ clientNumber, articleNumber, packSize, quantity }` (ASRS stock; same keys as `inventoryRequestLine`) |

Supported query options (OData-style):

| Option | Example | Description |
|--------|---------|-------------|
| `$filter` | `clientNumber eq 'OB' and articleNumber eq 'ART-1'` | Equality filters combined with `and` |
| `$top` | `50` | Page size (default 100, max 1000) |
| `$skip` | `100` | Skip records |
| `$count` | `true` | Include `@odata.count` (total matches before paging) |

Example:

```bash
curl -s -H "Authorization: Bearer test-token" \
  "https://wispelberg.eu/kisoft/oneapi/v1/packUnit?\$filter=clientNumber%20eq%20'OB'&\$count=true"
```

---

## Order lifecycles & mock operator endpoints

Real KiSoft One advances an order's `processingStatus` as automation and operators do the work; each transition triggers a reply webhook (HIS Appendix §4.3.1). Because the mock has no physical warehouse, these endpoints let you **play the operator** and drive the lifecycle. They are **not** part of the KiSoft One API — they exist only to make the mock useful.

| Method | Path (`{base}` + …) | GS ref | Effect |
|--------|---------------------|--------|--------|
| POST | `/oneapi/v1/inboundDelivery/operator/start` | §5.1.2 | Inbound delivery → `STARTED`; emits `PostInboundDeliveryReply(STARTED)` |
| POST | `/oneapi/v1/inboundDelivery/operator/loadUnit` | §5.1.2 | Confirm one received load unit (decant); emits `PostStockReceived`. With default `inbound-auto-stock=true`, ASRS was already booked on `PostInboundDelivery` (no double-book). Auto-`FINISHED` when all lines are received. |
| POST | `/oneapi/v1/inboundDelivery/operator/finish` | §5.1.2 | Force `FINISHED` (short receipt); emits `PostInboundDeliveryReply(FINISHED)`; with auto-stock, ASRS is corrected down for unreceived qty |
| POST | `/oneapi/v1/goodsOutOrder/operator/start` | §5.2.1 | Goods-out order → `STARTED`; emits `PostGoodsOutOrderReply(STARTED)` |
| POST | `/oneapi/v1/goodsOutOrder/operator/pick` | §5.2.3 | Confirm picking → `PROCESSED`; decrements ASRS stock. Short pick → `PostStockCorrected` + line result `QUANTITY_ERROR`; damaged source → `PostStockLockChanged`. |
| POST | `/oneapi/v1/goodsOutOrder/operator/finalCheck` | §5.2.4/5 | Final check / dispatch → `FINISHED`; emits `PostGoodsOutOrderReply(FINISHED)` |
| POST | `/oneapi/v1/inventoryRequest/operator/count` | §5.3.5 | Record the counted quantity. A deviation from booked stock corrects it and emits `PostStockCorrected`; then `PostInventoryRequestReply(FINISHED)` + `PostStockLockChanged` (unlock). |
| POST | `/oneapi/v1/loadUnit/retrieve` | §5.3.3 | Targeted retrieval of a load unit → `PostLoadUnitMoved` (+ `PostStockCorrected` when `toConventional=true`). |
| POST | `/oneapi/v1/loadUnit/repack` | §5.3.4 | Repacking / defragmentation → `PostStockCorrected`. |
| POST | `/oneapi/v1/stock/operator/correct` | §5.3 (IN-02) | Spontaneous stock correction (absolute counted qty) → `PostStockCorrected` without prior inventory request. |

Lifecycle errors use `E-AKO-MOVM-0003` (order not found) and `E-AKO-MOVM-0004` (wrong status for the requested transition).

---

## Reply webhooks (KiSoft One → HOST)

When `knapp.mock.reply-callback-url` is set, the mock acts as an HTTP client and POSTs reply/event messages to the host via **IBM API Connect** (VOLVO test gateway). For a message named `goodsOutOrderReply` and the configured callback base, the mock calls:

```
POST {reply-callback-url}/oneapi/v1/_webhooks/goodsOutOrderReply
Content-Type: application/json
Authorization: Bearer <Entra ID access token>
X-IBM-Client-Id: <IBM APIC client id>
X-IBM-Client-Secret: <IBM APIC client secret>
```

The Bearer token is obtained automatically via **Microsoft Entra ID** client credentials (`knapp.mock.webhook-oauth-*` in `application.yml`) and cached until shortly before expiry. IBM APIC credentials are applied **server-side only** — they are never embedded in Swagger UI.

Your host endpoint should accept the POST and return any `2xx` as acknowledgement. Lifecycle flows deliver callbacks **asynchronously** (they do not block the originating API call). Test-oriented endpoints support `wait=true` to return the APIC response in the HTTP reply (see [Testing webhooks](#testing-webhooks)). Callbacks are POSTed to `{reply-callback-url}/{reply-callback-path-prefix}/{messageName}` (default prefix: `oneapi/v1/_webhooks`).

In-scope reply messages and what triggers them:

| Message (operationId) | Delivered to | Trigger |
|-----------------------|--------------|---------|
| `PostInboundDeliveryReply` | `…/oneapi/v1/_webhooks/inboundDeliveryReply` | Inbound delivery status change (`NEW` / `STARTED` / `FINISHED`) — §7.1.1 |
| `PostStockReceived` | `…/stockReceived` | Each goods-in load unit confirmed (stock now pickable) — §8.1.1 |
| `PostStorageOrderReply` | `…/storageOrderReply` | Optional per load unit when `knapp.mock.storage-order-reply-enabled=true` (IB-02) |
| `PostGoodsOutOrderReply` | `…/goodsOutOrderReply` | Goods-out order status change (`NEW`/`STARTED`/`PROCESSED`/`FINISHED`/`CANCELLED`) — §7.4.2 |
| `PostInventoryRequestReply` | `…/inventoryRequestReply` | Inventory request status change — §7.2.1 |
| `PostLoadUnitMoved` | `…/loadUnitMoved` | A load unit was moved / relocated — §7.3.1 |
| `PostStockCorrected` | `…/stockCorrected` | Stock corrected (short pick, inventory deviation, repack, retrieval) — §8.1.2 |
| `PostStockLockChanged` | `…/stockLockChanged` | A stock lock was added/removed (damage, inventory unlock) — §8.1.3 |
| `PostInventoryReport` | `…/inventoryReport` | Answer to `PostRequestInventoryReport` — §8.2.1 |
| `PostStorageCapacityReport` | `…/storageCapacityReport` | Answer to `PostRequestStorageCapacityReport` — §8.3.1 |

The exact JSON schema of every reply is listed under the **Webhooks (outgoing)** tag in Swagger UI.

---

## Testing webhooks

Outgoing callbacks are sent from the mock server to IBM APIC. You can verify delivery in three ways: **synchronous test endpoints** (recommended for Swagger), **lifecycle/operator calls** (async — check logs), or **direct webhook relay** from Swagger.

### Prerequisites

1. Ensure `knapp.mock.reply-callback-*` and `knapp.mock.webhook-*` are configured in `application.yml` (or overridden at startup) and start the server.
2. Confirm at startup:

   ```
   Outgoing KiSoft → HOST webhooks ENABLED → https://apitest-awe.volvo.com/...
   Entra ID Bearer token: acquired (length=...)
   ```

3. Log in to Swagger UI (HTTP Basic Auth) when `ui-auth-enabled` is on.

### Option A — Swagger with `wait=true` (see APIC response in the reply)

Use this when you want the HTTP response to include what IBM APIC returned.

1. Open Swagger UI → select server **KiSoft Mock** (`https://wispelberg.eu/kisoft` or `http://localhost:8084/kisoft`).
2. Expand **Webhooks (outgoing)** or **Stock Operator (mock)**.
3. Leave query parameter **`wait=true`** (default).
4. Execute, e.g. `POST /oneapi/v1/_webhooks/stockCorrected` or `POST /oneapi/v1/stock/operator/correct`.

A successful mock action returns HTTP 200 with a `callback` object:

```json
{
  "http": 200,
  "status": "OK",
  "message": "Callback delivered to POST https://apitest-awe.volvo.com/.../stockCorrected",
  "callback": {
    "targetUrl": "https://apitest-awe.volvo.com/.../oneapi/v1/_webhooks/stockCorrected",
    "delivered": true,
    "callbackHttpStatus": 200,
    "callbackResponseBody": "...",
    "errorMessage": null
  }
}
```

When APIC rejects the call, the mock returns the **same HTTP status as APIC** (e.g. `500`), `status` is `CALLBACK_FAILED`, and the response body includes the full log line in `message` plus structured fields in `callback`:

```json
{
  "http": 500,
  "status": "CALLBACK_FAILED",
  "message": "Failed to send StockReceived to https://apitest-awe.volvo.com/.../stockReceived: 500 URL Open error:\"Could not connect to endpoint\"",
  "callback": {
    "targetUrl": "https://apitest-awe.volvo.com/.../oneapi/v1/_webhooks/stockReceived",
    "delivered": false,
    "callbackHttpStatus": 500,
    "callbackResponseBody": "{\"httpCode\":\"500\",\"httpMessage\":\"URL Open error\",\"moreInformation\":\"Could not connect to endpoint\"}",
    "callbackHttpMessage": "URL Open error",
    "moreInformation": "Could not connect to endpoint",
    "errorMessage": "500 URL Open error:\"Could not connect to endpoint\""
  }
}
```

Set **`wait=false`** to fire-and-forget (HTTP `202 ACCEPTED`) — same behaviour as lifecycle endpoints.

### Option B — Trigger via lifecycle / operator endpoints (async)

Driving an order lifecycle sends the corresponding webhooks in the background. Example — spontaneous stock correction:

```bash
BASE=http://localhost:8084/kisoft/oneapi/v1

curl -X POST "$BASE/stock/operator/correct?wait=true" \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer test' \
  -d '{
    "clientNumber": "DEFAULT",
    "articleNumber": "A1",
    "packSize": "EU",
    "countedQuantity": 10,
    "reason": "TEST",
    "stationName": "ST01"
  }'
```

Without `wait=true`, watch the **server log** for:

```
Sent StockCorrected to https://apitest-awe.volvo.com/.../stockCorrected
```

or on failure:

```
Failed to send StockCorrected to ...: 403 Forbidden: ...
```

The [Walkthrough (curl)](#walkthrough-curl) goods-out example triggers `PostGoodsOutOrderReply` at each operator step the same way.

### Option C — Relay a custom payload (Swagger **Webhooks (outgoing)**)

To test a specific JSON body without running a full lifecycle:

1. Open **Webhooks (outgoing)** → e.g. `POST /oneapi/v1/_webhooks/inboundDeliveryReply`.
2. Fill in the request body (schemas match the HIS Appendix).
3. Execute with `wait=true`.

The mock forwards the body to `{reply-callback-url}/oneapi/v1/_webhooks/{messageName}` with IBM + Entra credentials applied server-side.

### Quick reference

| Goal | Endpoint | `wait` | Where to see the result |
|------|----------|--------|-------------------------|
| Test one webhook payload | `POST /_webhooks/{messageName}` | `true` | `callback` field in HTTP response |
| Spontaneous stock correction | `POST /stock/operator/correct` | `true` | `callback` field in HTTP response |
| Full goods-in / goods-out flow | Operator + HOST endpoints | `false` (default) | Server log (`Sent …` / `Failed …`) |
| Fire-and-forget relay | `POST /_webhooks/{messageName}` | `false` | HTTP `202`; server log |

### Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| No `callback` in response | `wait=false`, or callbacks disabled (`reply-callback-url` blank) |
| `callback.delivered: false`, HTTP 403 | IBM client not registered on the APIC plan — contact Volvo/IBM |
| `callback.delivered: false`, HTTP 401 | Entra OAuth misconfigured; check `knapp.mock.webhook-oauth-*` in `application.yml` |
| Swagger `NetworkError` | Browser cannot reach the server (CORS/network); try localhost or curl |
| Startup: `OAuth … FAILED` | Invalid webhook OAuth client secret in `application.yml` |

---

## Walkthrough (curl)

A goods-out happy path against a local `dev` instance (base = `http://localhost:8084/kisoft`, no token needed):

```bash
BASE=http://localhost:8084/kisoft/oneapi/v1

# 1. Article master data
curl -X PUT "$BASE/packUnit" -H 'Content-Type: application/json' \
  -d '[{"article":{"clientNumber":"DEFAULT","articleNumber":"A1","articleName":"Widget"},"packSize":"EU"}]'

# 2. (Stock would normally arrive via goods-in; assume A1/EU is in the ASRS)

# 3. Create a goods-out order  -> PostGoodsOutOrderReply(NEW) is sent to your callback URL
curl -X POST "$BASE/goodsOutOrder" -H 'Content-Type: application/json' \
  -d '{"clientNumber":"DEFAULT","orderNumber":"GO-1","sheetNumber":"1","loadCarrier":"FULL",
       "goodsOutOrderLines":[{"lineReference":"L1","requestedQuantity":2,"articleNumber":"A1","packSize":"EU"}]}'

# 4. Drive the lifecycle (each step sends a PostGoodsOutOrderReply)
REF='{"clientNumber":"DEFAULT","orderNumber":"GO-1","sheetNumber":"1"}'
curl -X POST "$BASE/goodsOutOrder/operator/start"      -H 'Content-Type: application/json' -d "$REF"   # -> STARTED
curl -X POST "$BASE/goodsOutOrder/operator/pick"       -H 'Content-Type: application/json' \
  -d '{"clientNumber":"DEFAULT","orderNumber":"GO-1","sheetNumber":"1","lines":[{"lineReference":"L1","pickedQuantity":2}]}'  # -> PROCESSED
curl -X POST "$BASE/goodsOutOrder/operator/finalCheck" -H 'Content-Type: application/json' -d "$REF"   # -> FINISHED
```

---

## Swagger UI & OpenAPI

- **Swagger UI:** `{base}/swagger-ui.html`
- **OpenAPI spec (JSON, OpenAPI 3.0):** `{base}/v3/api-docs`

Tags: **MasterData-Article**, **Goods-In** & **Goods-In Operator (mock)**, **Goods-Out** & **Goods-Out Operator (mock)**, **Inventory** & **Inventory Operator (mock)**, **Warehouse-Internal (mock)**, **Stock Operator (mock)**, **Stock Reports**, **Webhooks (outgoing)**. Use **Webhooks (outgoing)** with `wait=true` to relay a payload to IBM APIC and see the APIC response in the reply (see [Testing webhooks](#testing-webhooks)).

---

## Status & error codes

`processingStatus`: `NEW` → `STARTED` → `PROCESSED` → `FINISHED` (or `CANCELLED`).

Goods-out line `processingResult` (on reply webhooks during picking): `UNTOUCHED`, `PROCESSED`, `QUANTITY_ERROR`, `OUT_OF_STOCK` (runtime stock depletion).

| Code | Meaning |
|------|---------|
| `E-AKO-GENR-0001` / `E-AKO-GENR-0002` | General error / format error (e.g. batch > 10 000) |
| `E-AKO-MAST-0001` | Unknown article / pack size |
| `E-AKO-MAST-0006` | Duplicate article+packSize in the same batch |
| `E-AKO-MAST-0007` | Duplicate article within one inbound delivery |
| `E-AKO-MOVM-0002` | Order/delivery/request already active |
| `E-AKO-MOVM-0003` | Order/delivery/request not found |
| `E-AKO-MOVM-0004` | Wrong status for the requested operator transition |
| `E-AKO-MOVM-0005` | Wrong process status (e.g. patch on an active delivery → 409) |
| `E-AKO-MOVM-0006` / `-0008` / `-0009` | Goods-in: qty exceeds open / compartment not empty / mixed SKU in compartment |
| `E-AKO-STOC-0001` | Not enough stock (goods-out intake) |
| `E-AKO-STOC-0002` | Cannot delete part: ASRS still holds inventory |

---

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8084` | HTTP port |
| `server.servlet.context-path` | `/kisoft` | Base path; the public URL is `https://wispelberg.eu/kisoft` |
| `knapp.mock.bypass-auth` | `false` | `true` (set by the `dev` profile) skips the Bearer-token check |
| `knapp.mock.ui-auth-enabled` | `true` | HTTP Basic Auth on homepage and Swagger UI; `false` in `dev`/`test` |
| `knapp.mock.ui-username` | `knapp` | UI login username |
| `knapp.mock.ui-password` | *(env: `MOCK_UI_PASSWORD`)* | UI login password — **required** when UI auth is enabled |
| `knapp.mock.max-records` | `1000` | Max pack-unit master-data records |
| `knapp.mock.reply-callback-enabled` | `true` | Master switch; set `false` to disable all outgoing webhooks |
| `knapp.mock.reply-callback-url` | `https://apitest-awe.volvo.com/vgcd/external/plwms5d.srv.volvo.com/wms` | IBM APIC base URL |
| `knapp.mock.reply-callback-path-prefix` | `oneapi/v1/_webhooks` | Path between base and message name |
| `knapp.mock.webhook-ibm-client-id` | *(in `application.yml`)* | `X-IBM-Client-Id` on each webhook POST |
| `knapp.mock.webhook-ibm-client-secret` | *(in `application.yml`)* | `X-IBM-Client-Secret` on each webhook POST |
| `knapp.mock.webhook-oauth-tenant-id` | *(in `application.yml`)* | Entra tenant for webhook Bearer token |
| `knapp.mock.webhook-oauth-client-id` | *(in `application.yml`)* | Entra app client ID (client_credentials) |
| `knapp.mock.webhook-oauth-client-secret` | *(in `application.yml`)* | Entra app client secret for webhook OAuth |
| `knapp.mock.webhook-oauth-scope` | *(in `application.yml`)* | OAuth scope for the webhook access token |
| `knapp.mock.storage-order-reply-enabled` | `false` | When `true`, each IB-02 load-unit receipt also emits optional `PostStorageOrderReply(STARTED/FINISHED)` |
| `knapp.mock.inbound-auto-stock` | `true` | When `true`, `PostInboundDelivery` books each line's `expectedQuantity` into ASRS (keys: `articleNumber` + `packSize`, same as goods-out / `inventoryRequestLine`) so `PostGoodsOutOrder` no longer fails with `E-AKO-STOC-0001` before operator load-unit. Set `false` for strict IB-02 (stock only after load-unit). |

Override at startup, e.g.:

```bash
java -jar knapp-kisoft-mock-4.0.3.jar \
  --knapp.mock.ui-password=<strong-password> \
  --knapp.mock.max-records=500
```

---

## ASRS use cases — implementation status (`asrs-specs`)

The behaviour of this mock is driven by the companion **`asrs-specs`** specification repository (the agreed WMS ↔ WCS design record; the spec repo is authoritative, this mock is a non-authoritative WCS stand-in for early wiring). Each use case there (`usecases/`) has a matching feature spec (`specs/`). This mock implements the **KiSoft / WCS side** of those use cases — i.e. the requests it must accept and the reply/event messages it must send.

**Legend:** ✅ implemented · ◑ partial (core implemented; some sub-flows out of scope or simplified) · ✖ out of scope (not used in this project per the HIS Appendix) · ➖ host-side only (no KiSoft messages).

| Use case | Spec | Status | What the mock provides |
|----------|------|:------:|------------------------|
| **MA-01** — Part master data | `001-part-master-data-asrs` | ✅ | `PutPackUnit` / `DeletePackUnit` / update session; 10 000-item batch limit (`E-AKO-GENR-0002`); idempotent upserts; part delete blocked while ASRS holds stock (`E-AKO-STOC-0002`). |
| **IB-01** — Prepare for ASRS decanting | `002-prepare-asrs-decanning` | ➖ | Receiving-side preparation (ASN, matching, sortation, TGU) — owned by the WMS; sends no commands to the ASRS, so nothing for the mock. |
| **IB-02** — Decanting into ASRS | `003-decant-into-asrs` | ✅ | `PostInboundDelivery` → `PatchInboundDelivery` (while `NEW`) / `DeleteInboundDelivery` (while `NEW`, `CANCELLED` reply) → operator `start` → per-load-unit `PostStockReceived` (stored & pickable) → `PostInboundDeliveryReply(FINISHED)`. Optional `PostStorageOrderReply` per load unit when `storage-order-reply-enabled=true`. Guards: 409 on active patch/delete (`E-AKO-MOVM-0005`), qty > open (`-0006`), no topping-up (`-0008`), mixed SKU per compartment (`-0009`). |
| **OB-01** — ASRS picking (pick then pack) | `005-asrs-pick-then-pack` | ✅ | `PostGoodsOutOrder` / `PatchGoodsOutOrder`; intake validation → HTTP 400 (`E-AKO-MAST-0001`, `E-AKO-STOC-0001`, `E-AKO-GENR-0001`/`0002`); accepted orders → `PostGoodsOutOrderReply(NEW)` with `UNTOUCHED`; operator `start`/`pick`/`finalCheck` → `STARTED`/`PROCESSED`/`FINISHED`; ASRS decrement, UUID per picked line, short pick → `PostStockCorrected`+`QUANTITY_ERROR`, damage → `PostStockLockChanged`. Multiphase picking (GS §5.2.2) excluded. |
| **IN-01** — Cycle count | `006-asrs-cycle-count` | ✅ | `PostInventoryRequest` / `DeleteInventoryRequest`; duplicate Article+CoO while prior request is `NEW` → 409 (`E-AKO-MOVM-0005`); unknown article → 400 (`E-AKO-MAST-0001`); operator `count` → one net-delta `PostStockCorrected` on deviation, `PostInventoryRequestReply(FINISHED)`, `PostStockLockChanged` (unlock). |
| **IN-02** — Stock adjustments | `007-asrs-stock-adjustments` | ✅ | Spontaneous correction via `POST /stock/operator/correct`; inventory-linked and spontaneous paths emit `PostStockCorrected` with `eventId`. Also emitted from short picks, repacking and retrieval-to-conventional. |
| **IN-03** — Stock alignment | `008-asrs-stock-alignment` | ✅ | `PostRequestInventoryReport` → async `PostInventoryReport` built from the current ASRS stock snapshot (filterable by client/article/pack size). |
| **IN-04** — Stock properties changed | `009-asrs-stock-properties` | ✖ | `PostStockChanged` / `PostChangeLoadUnitRequest` are out of scope for this project (KIS-009), so not exposed. |
| **IN-05** — Stock lock change | `010-asrs-stock-lock` | ◑ | Outbound `PostStockLockChanged` implemented (lock on damaged source during picking; unlock after inventory count). The inbound *request to lock* (`PostStockLockRequest`) is out of scope (KIS-010). |
| **IN-06** — Stock move (conventional ↔ ASRS) | `011-asrs-stock-move` | ◑ | Outbound move feedback `PostLoadUnitMoved` implemented via targeted retrieval (`/loadUnit/retrieve`), plus `PostStockCorrected` when stock leaves to the conventional warehouse; re-storing uses the goods-in flow. The inbound `relocationRequest` is out of scope (`PostStockMoved` superseded by `PostLoadUnitMoved`, KIS-001/002). |
| **IN-07** — Partial stock block | `012-partial-stock-block` | ◑ | Building blocks present (`/loadUnit/retrieve` + `PostStockLockChanged`); no dedicated partial-block workflow yet. |

The implemented goods-in (GS §5.1), goods-out (§5.2, excl. §5.2.2), inventory (§5.3.5) and warehouse-internal (§5.3.3–§5.3.4) flows are described above under [Order lifecycles](#order-lifecycles--mock-operator-endpoints) and [Reply webhooks](#reply-webhooks-kisoft-one--host). Corresponding test plans live in [`testplans/`](testplans/).
