# Test plan – IB-02 Decanting into ASRS (KiSoft side)

**Source spec**: `asrs-specs/usecases/IB-02-decanting-into-asrs.md` (Page7 sequence
`Processing Inbound Delivery`) and `asrs-specs/specs/003-decant-into-asrs/spec.md`.

**Scope**: Only the **KiSoft / WCS** behaviour during the inbound delivery
lifecycle. WMS business intent and inventory reconciliation logic are out of
scope; we assume the host (WMS) integrator follows the documented consumption
rules.

**System under test**: `knapp-kisoft-mock` v2.1.1, profile `dev` or `test`.

**API base path**: `/kisoft/oneapi/v1` (Swagger UI: `/kisoft/swagger-ui/index.html`).

---

## 1. KiSoft endpoints under test

### Goods-In (host-driven)

| Operation ID | Method + path | Use |
|--------------|---------------|-----|
| `PostInboundDelivery` | `POST /inboundDelivery` | WMS creates an ASRS putaway intent |
| `PatchInboundDelivery` | `PATCH /inboundDelivery` | WMS updates header/lines while delivery is `NEW` |
| `DeleteInboundDelivery` | `DELETE /inboundDelivery` | WMS cancels delivery while still `NEW` |

### Goods-In Operator (mock simulation of WCS UI)

| Operation ID | Method + path | Use |
|--------------|---------------|-----|
| `OperatorStartInboundDelivery` | `POST /inboundDelivery/operator/start` | Simulate operator starting processing → status `STARTED`, emits `PostInboundDeliveryReply(STARTED)` |
| `OperatorReceiveLoadUnit` | `POST /inboundDelivery/operator/loadUnit` | Decant + inject one goods-in load unit; emits `PostStockReceived`; auto-finishes when the last line is fully received |
| `OperatorFinishInboundDelivery` | `POST /inboundDelivery/operator/finish` | Force `FINISHED` for short receipts (EF-05); emits `PostInboundDeliveryReply(FINISHED)` |

### Outgoing webhooks (asynchronous)

- `PostInboundDeliveryReply` with status `NEW`, `STARTED`, `FINISHED` → `${reply-callback-url}/inboundDeliveryReply`.
- `PostStockReceived` per goods-in load unit → `${reply-callback-url}/stockReceived`.

---

## 2. State / status model

| State | Set when | Allowed transitions |
|-------|----------|---------------------|
| `NEW` | After successful `PostInboundDelivery` | → `STARTED` (operator start) |
| `STARTED` | After `OperatorStartInboundDelivery` | → `FINISHED` (auto-finish or operator finish) |
| `FINISHED` | All lines received or operator finishes | terminal |

Patch / Delete are allowed **only** in `NEW`. Any other status returns `409` with
`E-AKO-MOVM-0005` (IB-02 EF-K409, FR-008).

---

## 3. Mapping of asrs-specs requirements to KiSoft mock features

| Spec ref | KiSoft endpoint | Behaviour under test |
|----------|-----------------|----------------------|
| FR-001 (create inbound) | `PostInboundDelivery` | 200 + `PostInboundDeliveryReply(NEW)` |
| Business rule "one part per inbound delivery" | `PostInboundDelivery` | Reject duplicate `articleNumber` lines with `E-AKO-MAST-0007` |
| Master-data prerequisite (MF-1 + MA-01 link) | `PostInboundDelivery` | Reject unknown `(article, packSize)` with `E-AKO-MAST-0001` |
| FR-008 / EF-K409 | `PatchInboundDelivery`, `DeleteInboundDelivery` | Return `409` with `E-AKO-MOVM-0005` when status ≠ NEW |
| FR-003 / MF-3 | `OperatorStartInboundDelivery` | Status flips `NEW`→`STARTED`; reply `STARTED` emitted |
| EF-02 (A2) putaway not found | `OperatorStartInboundDelivery` | `400` + `E-AKO-MOVM-0003` |
| FR-004 (qty validation) / EF-04 (A4) | `OperatorReceiveLoadUnit` | Reject qty > open with `E-AKO-MOVM-0006` |
| FR-005 / EF-06 (A6) | `OperatorReceiveLoadUnit` | Reject if `(loadUnitCode, compartment)` is not empty (`E-AKO-MOVM-0008`) |
| EF-14 (A14) wrong/mixed SKU | `OperatorReceiveLoadUnit` | Reject if compartment already holds another article (`E-AKO-MOVM-0009`) |
| FR-006 (PostStockReceived) | `OperatorReceiveLoadUnit` | Outgoing webhook payload sent |
| FR-007 (FINISHED) | Auto-finish or `OperatorFinishInboundDelivery` | Outgoing webhook payload sent with status `FINISHED` |
| EF-05 (A5) short receipt | `OperatorFinishInboundDelivery` | Operator can close partially received delivery |

---

## 4. Test cases

### 4.1 TP-IB02-HAPPY — Single-tote happy path

**Refs**: User Story 1, AC-01..AC-03.

**Steps**:

1. `PUT /packUnit` for `(IB02, ART-OK, EU)`.
2. `POST /inboundDelivery` `IB-OK`, one line `(ART-OK, EU, qty=3)`.
3. `POST /inboundDelivery/operator/start` for `IB-OK`.
4. `POST /inboundDelivery/operator/loadUnit` qty=3, fresh tote.
5. `PATCH /inboundDelivery` for `IB-OK` with priority change.

**Expected**:

- 1–3: 200.
- 4: 200; message contains `FINISHED` (auto-finish triggered because line is fully received).
- 5: `409` with `E-AKO-MOVM-0005` (delivery is now `FINISHED`, not `NEW`).
- Webhook log: `PostInboundDeliveryReply(NEW)`, `PostInboundDeliveryReply(STARTED)`, `PostStockReceived` ×1, `PostInboundDeliveryReply(FINISHED)`.

### 4.2 TP-IB02-MULTI-TOTE — Multi-tote / partial confirmations

**Refs**: User Story 2, ALT-01, AC-03.

**Steps**:

1. Create delivery `IB-MULTI` with one line qty=4.
2. Start.
3. Decant qty=2 into `TOTE-A/C1`.
4. Decant qty=2 into `TOTE-B/C1`.

**Expected**:

- Two `PostStockReceived` webhooks.
- After step 4 the delivery is `FINISHED` and one `PostInboundDeliveryReply(FINISHED)` is emitted.
- Trying to decant a third unit returns `400` `E-AKO-MOVM-0004` (wrong status, no longer STARTED).

### 4.3 TP-IB02-K409-PATCH — EF-K409 patch on active delivery

**Refs**: FR-008, EF-K409, AC-05.

**Steps**:

1. Create delivery `IB-K409`, start it.
2. `PATCH /inboundDelivery` for `IB-K409` with priority=5.

**Expected**:

- 2: HTTP `409`, body `OneApiErrorResponse(codes=["E-AKO-MOVM-0005"])`, message references "active".

### 4.4 TP-IB02-K409-DELETE — EF-K409 delete on active delivery

**Refs**: FR-008, EF-K409.

**Steps**: As 4.3, but `DELETE /inboundDelivery` instead of patch.

**Expected**: HTTP `409`, body `OneApiErrorResponse(codes=["E-AKO-MOVM-0005"])`.

### 4.5 TP-IB02-DUP-ARTICLE — KiSoft rejects duplicate article in lines

**Refs**: IB-02 business rule "no two lines with the same part on one inbound".

**Steps**: `POST /inboundDelivery` with two lines that share `articleNumber=ART-DUP`.

**Expected**: HTTP `400`, body `OneApiErrorResponse(codes=["E-AKO-MAST-0007"])`. Delivery is **not** persisted.

### 4.6 TP-IB02-UNKNOWN-ARTICLE — Master-data prerequisite

**Refs**: MA-01 ↔ IB-02 dependency, EF-03 (A3).

**Steps**: `POST /inboundDelivery` referencing an unknown `(article, packSize)`.

**Expected**: HTTP `400`, body `OneApiErrorResponse(codes=["E-AKO-MAST-0001"])`.

### 4.7 TP-IB02-EF02-UNKNOWN-IBN — Operator start on unknown delivery

**Refs**: EF-02 (A2), AC-01.

**Steps**: `POST /inboundDelivery/operator/start` with `inboundDeliveryNumber=DOES-NOT-EXIST`.

**Expected**: HTTP `400`, `OneApiErrorResponse(codes=["E-AKO-MOVM-0003"])`.

### 4.8 TP-IB02-EF04-QTY-EXCEEDS — qty greater than open quantity

**Refs**: EF-04 (A4), FR-004, AC-04.

**Steps**:

1. Create delivery `IB-EF04` with qty=2, start it.
2. `POST /inboundDelivery/operator/loadUnit` qty=99.

**Expected**: HTTP `400`, `OneApiErrorResponse(codes=["E-AKO-MOVM-0006"])`. ASRS stock unchanged. No webhook emitted.

### 4.9 TP-IB02-EF06-COMPARTMENT-NOT-EMPTY — no topping up

**Refs**: EF-06 (A6), FR-005, AC-08.

**Steps**:

1. Create delivery, start it.
2. Decant qty=2 into `TOTE-EF06/C1`.
3. Decant qty=1 into the same `TOTE-EF06/C1` again.

**Expected**:

- 2: 200.
- 3: HTTP `400`, `OneApiErrorResponse(codes=["E-AKO-MOVM-0008"])`.

### 4.10 TP-IB02-EF14-MIXED-SKU — wrong SKU into already-occupied compartment

**Refs**: EF-14 (A14).

**Steps**:

1. Create delivery for `ART-A`. Start it. Decant qty=1 into `TOTE-MIX/C1` → 200.
2. Create a second delivery for `ART-B`. Start it. Decant qty=1 into the **same** `TOTE-MIX/C1`.

**Expected**: 1: 200; 2 (second decant): HTTP `400`, `OneApiErrorResponse(codes=["E-AKO-MOVM-0009"])`.

### 4.11 TP-IB02-EF05-SHORT-FINISH — short receipt closed by operator

**Refs**: EF-05 (A5).

**Steps**:

1. Create delivery qty=5, start it.
2. Decant qty=2 into a fresh tote.
3. `POST /inboundDelivery/operator/finish`.

**Expected**:

- 2: 200, 1× `PostStockReceived`.
- 3: 200, 1× `PostInboundDeliveryReply(FINISHED)`. Subsequent `OperatorReceiveLoadUnit` returns `400 E-AKO-MOVM-0004`.

### 4.12 TP-IB02-LINE-NOT-FOUND — operator references non-existent line

**Refs**: defensive check.

**Steps**: After start, decant with `lineReference=DOES-NOT-EXIST`.

**Expected**: HTTP `400`, `OneApiErrorResponse(codes=["E-AKO-MOVM-0007"])`.

---

## 5. Automation

| Test class | Method | Plan |
|------------|--------|------|
| `AsrsKiSoftIntegrationTest` | `ib02_post_duplicateArticleInLines_returns400` | TP-IB02-DUP-ARTICLE |
| `AsrsKiSoftIntegrationTest` | `ib02_post_unknownArticle_returnsE_AKO_MAST_0001` | TP-IB02-UNKNOWN-ARTICLE |
| `AsrsKiSoftIntegrationTest` | `ib02_efK409_patchActiveInbound_returns409` | TP-IB02-K409-PATCH |
| `AsrsKiSoftIntegrationTest` | `ib02_efK409_deleteActiveInbound_returns409` | TP-IB02-K409-DELETE |
| `AsrsKiSoftIntegrationTest` | `ib02_delete_whenNew_cancelsAndRemoves` | TP-IB02-DELETE-NEW |
| `AsrsKiSoftIntegrationTest` | `ib02_operator_start_unknownDelivery_returns400` | TP-IB02-EF02-UNKNOWN-IBN |
| `AsrsKiSoftIntegrationTest` | `ib02_loadUnit_qtyExceedsOpen_returnsE_AKO_MOVM_0006` | TP-IB02-EF04-QTY-EXCEEDS |
| `AsrsKiSoftIntegrationTest` | `ib02_loadUnit_compartmentNotEmpty_returnsE_AKO_MOVM_0008` | TP-IB02-EF06-COMPARTMENT-NOT-EMPTY |
| `AsrsKiSoftIntegrationTest` | `ib02_happyPath_singleTote_completesAndAutoFinishes` | TP-IB02-HAPPY |
| `AsrsKiSoftIntegrationTest` | `ib02_partialReceipt_thenManualFinish_succeeds` | TP-IB02-EF05-SHORT-FINISH |

Run only IB-02 automation:

```bash
mvn test -Dtest=AsrsKiSoftIntegrationTest#ib02_*
```

Multi-tote, EF-14 and the line-not-found case (TP-IB02-MULTI-TOTE / EF14 / LINE-NOT-FOUND) are
candidate follow-ups for the next test increment.

---

## 6. Manual verification checklist

| # | Action | Expected |
|---|--------|----------|
| 1 | Swagger UI shows two new tags: `Goods-In` and `Goods-In Operator (mock)` | Operations match the table above |
| 2 | `Goods-In Operator (mock)` is documented as mock-only | Tag description mentions "Mock endpoints" |
| 3 | Webhook section lists `inboundDeliveryReply`, `stockReceived`, `storageOrderReply`, `outboundDeliveryReply` | All four payload schemas resolve |
| 4 | Set `knapp.mock.reply-callback-url=https://your-listener` and run TP-IB02-HAPPY | Listener observes the four webhooks in order: NEW, STARTED, StockReceived, FINISHED |
| 5 | Patch a `NEW` delivery (priority change) | 200 OK |
| 6 | Patch a `STARTED` delivery | 409 with `E-AKO-MOVM-0005` |

---

## 7. Out of scope / open items (KiSoft side)

- **Storage-order replies** (FR-013) remain optional; the existing `StorageOrderController` still drives `StorageOrderReply STARTED/FINISHED` for orders pushed via `POST /storageOrder` and is unchanged by this iteration.
- **Weight/height** subsystem (EF-01) is not modelled.
- **Conveyor faults / error stations** (EF-08–EF-11) are not modelled by the mock.
- **Reconciliation views** (FR-INT-005, FR-010, AC-12) are WMS responsibilities; the mock simply emits idempotent webhooks per confirmation.
- **TGU arrival latency NFR (AC-09, AC-10, FR-012)** is monitored at the integration boundary, not by this mock.
