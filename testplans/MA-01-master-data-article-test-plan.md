# Test plan – MA-01 Part Master Data (KiSoft side)

**Source spec**: `asrs-specs/usecases/MA-01-part-master-data.md` and `asrs-specs/specs/001-part-master-data-asrs/spec.md`.

**Scope**: Only the **KiSoft** behaviour of the part master data process. WMS-side rule
engine, eligibility derivation and SIM cutover are out of scope.

**System under test**: `knapp-kisoft-mock` v2.1.1, profile `dev` or `test`.

**API base path**: `/kisoft/oneapi/v1` (Swagger UI: `/kisoft/swagger-ui/index.html`).

---

## 1. Test environment

| Item | Value |
|------|-------|
| Java | 17+ |
| Datastore | H2 in-memory via Spring Data JPA (Liquibase migrations) |
| Authentication | OAuth2 Bearer (`bypass-auth=true` in `dev`/`test` profile) |
| Outgoing webhook | Set `knapp.mock.reply-callback-url` to receive PostStockReceived (used in MA-01 E1 setup) |

### Test data conventions

| Field | Convention |
|-------|------------|
| `clientNumber` | `MA01`, `BATCH`, `STOCK01` (per scenario) |
| `articleNumber` | `ART-<scenario-tag>` |
| `packSize` | `EU` (mandatory; default `1` in WMS, but here we use a discriminator) |

---

## 2. Mapping of asrs-specs requirements to KiSoft mock features

| Spec reference | KiSoft endpoint | Behaviour under test |
|----------------|-----------------|----------------------|
| FR-001 (publish create/update/delete) | `PUT /packUnit`, `DELETE /packUnit` | CRUD persistency via Liquibase-backed H2 |
| FR-003 / User Story 3 (10 000-record batch limit) | `PUT /packUnit` | Reject oversize batch with `E-AKO-GENR-0002` |
| FR-004 (per-record diagnostics) | `PUT /packUnit` | 207 Multi-Status with `E-AKO-MAST-0006` per duplicate row |
| FR-005 / E1 / User Story 4 (delete blocked while ASRS holds inventory) | `DELETE /packUnit` | 207 Multi-Status with `E-AKO-STOC-0002` per blocked key |
| FR-009 (idempotent retry of same publish) | `PUT /packUnit` | Repeated PUT with same key updates the same record |
| Update session (SET / CLEANUP) | `POST /packUnit/updateSession` | Pack units not seen since last `SET` are removed on `CLEANUP` |

---

## 3. Test cases

### 3.1 TP-MA01-PUBLISH — Happy path publish + retry idempotency

**Refs**: FR-001, FR-009.

**Pre**: Empty store. `POST /packUnit/updateSession` body `{"clientNumber":"MA01","transmissionTag":"SET"}` → 200.

**Steps**:

1. `PUT /packUnit` with one `PackUnitFull` for `(MA01, ART-001, EU)`.
2. `PUT /packUnit` with the same body again.
3. `GET /packUnit`.

**Expected**:

- Step 1: `200 OK`, response `OneApiOkResponse(http=200, status=OK)`.
- Step 2: `200 OK` again (idempotent upsert).
- Step 3: list contains exactly one entry for `(MA01, ART-001, EU)`.

### 3.2 TP-MA01-BATCH-LIMIT — Oversize batch rejected

**Refs**: FR-003, asrs-specs User Story 3.

**Steps**: `PUT /packUnit` with **10 001** distinct PackUnitFull entries.

**Expected**:

- HTTP `400`.
- Body: `OneApiErrorResponse` with `codes=["E-AKO-GENR-0002"]` and a message indicating the batch exceeds the maximum.

### 3.3 TP-MA01-DUPLICATE-IN-BATCH — Per-row diagnostics on duplicates

**Refs**: FR-004, E3.

**Steps**: `PUT /packUnit` with two rows that share `(client, article, packSize)`.

**Expected**:

- HTTP `207 Multi-Status`.
- Body is an array; the duplicate row is represented as `OneApiErrorResponse` with `codes=["E-AKO-MAST-0006"]`.
- The first occurrence is persisted; the second is not.

### 3.4 TP-MA01-DELETE-NO-STOCK — Delete allowed when no ASRS inventory

**Refs**: FR-005 (negative path).

**Pre**: PackUnit `(MA01, ART-NOSTOCK, EU)` exists. No inbound has been confirmed.

**Steps**: `DELETE /packUnit` body `[{"clientNumber":"MA01","articleNumber":"ART-NOSTOCK","packSize":"EU"}]`.

**Expected**: HTTP `200`, message "Pack units deleted". Subsequent `GET /packUnit` no longer returns the entry.

### 3.5 TP-MA01-E1-DELETE-WITH-STOCK — Delete blocked while inventory exists

**Refs**: E1, FR-005, User Story 4, SC-002.

**Pre-flow**:

1. `PUT /packUnit` for `(MA01, ART-E1, EU)`.
2. `POST /inboundDelivery` for delivery `IB-MA01-1` with one line `(ART-E1, EU, qty=5)` → `200`.
3. `POST /inboundDelivery/operator/start` for `IB-MA01-1` → `200`.
4. `POST /inboundDelivery/operator/loadUnit` with `quantity=5`, fresh tote/compartment → `200` (auto-finishes; PostStockReceived emitted; ASRS stock now 5).

**Steps**: `DELETE /packUnit` body `[{"clientNumber":"MA01","articleNumber":"ART-E1","packSize":"EU"}]`.

**Expected**:

- HTTP `207 Multi-Status`.
- First element: `OneApiErrorResponse` with `codes=["E-AKO-STOC-0002"]`, articleNumber `ART-E1`, message referencing MA-01 E1.
- The pack unit is **still present** afterwards (`GET /packUnit` returns it).

### 3.6 TP-MA01-DELETE-BULK-WITH-STOCK — Bulk delete still honours guard

**Refs**: E1.

**Pre**: Two pack units, one with stock, one without.

**Steps**: `DELETE /packUnit` with **no body** (bulk delete).

**Expected**: HTTP `207`. Pack unit without stock is removed; pack unit with stock remains and surfaces `E-AKO-STOC-0002`.

### 3.7 TP-MA01-SESSION-CLEANUP — Cleanup removes unseen pack units

**Refs**: A2 / A3.

**Steps**:

1. `POST /packUnit/updateSession` `SET` for `MA01`.
2. `PUT /packUnit` adds `(MA01, ART-X, EU)` and `(MA01, ART-Y, EU)`.
3. New session: `POST /packUnit/updateSession` `SET` for `MA01`.
4. `PUT /packUnit` adds only `(MA01, ART-X, EU)`.
5. `POST /packUnit/updateSession` `CLEANUP` for `MA01`.

**Expected**: After cleanup `GET /packUnit` returns only `(MA01, ART-X, EU)`. `(MA01, ART-Y, EU)` was not seen during the second session and is removed.

---

## 4. Automation

The following integration tests cover this plan:

| Test class | Method | Plan |
|------------|--------|------|
| `AsrsKiSoftIntegrationTest` | `ma01_putPackUnit_oversizeBatch_returns400` | TP-MA01-BATCH-LIMIT |
| `AsrsKiSoftIntegrationTest` | `ma01_e1_deletePackUnit_blockedWhenAsrsStockExists` | TP-MA01-E1-DELETE-WITH-STOCK |
| `ApiIntegrationTest` | `packUnit_put_valid_returns200`, `packUnit_get_returns200` | TP-MA01-PUBLISH |
| `ApiIntegrationTest` | `packUnit_put_duplicateArticlePackSize_returns207` | TP-MA01-DUPLICATE-IN-BATCH |
| `ApiIntegrationTest` | `packUnit_delete_existingRef_returns200` | TP-MA01-DELETE-NO-STOCK |
| `ApiIntegrationTest` | `packUnit_updateSession_SET_returns200`, `packUnit_updateSession_CLEANUP_returns200` | TP-MA01-SESSION-CLEANUP |

Run all: `mvn test`. Run only this plan's automation:

```bash
mvn test -Dtest=AsrsKiSoftIntegrationTest#ma01_*
```

---

## 5. Manual verification checklist

| # | Action | Expected |
|---|--------|----------|
| 1 | Open Swagger UI, expand **MasterData-Article** tag | Operations `PostPackUnitUpdateSession`, `PutPackUnits`, `DeletePackUnits` are listed |
| 1b | Open Swagger UI, expand **Mock OData Read (NOT KiSoft API)** tag | `GetPackUnits` is listed (mock-only; not KiSoft API) |
| 2 | Inspect `DeletePackUnits` documentation | Lists `E-AKO-STOC-0002` and references MA-01 E1 |
| 3 | Inspect `PutPackUnits` documentation | Mentions the `MAX_BATCH_SIZE` value 10 000 and `E-AKO-GENR-0002` |
| 4 | Set `knapp.mock.reply-callback-url` to a webhook capture, run TP-MA01-E1 pre-flow | Webhook receives one `PostStockReceived` payload referencing `(MA01, ART-E1, EU, qty=5)` |

---

## 6. Open items / out of scope

- **WMS-side eligibility/selection rule engine** is not modelled in KiSoft.
- **Publication latency (FR-005, 60 minutes SLA)** is not enforced; the mock applies updates synchronously.
- **`isLotRequired` → InboundOrder.lotNumber** runtime validation will be covered as part of IB-02 lot/serial enforcement when the host API contract is stabilised.
