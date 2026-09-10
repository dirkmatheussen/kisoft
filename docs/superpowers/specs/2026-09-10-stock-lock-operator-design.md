# Operator stock lock / unlock (PostStockLockChanged) — design

Date: 2026-09-10
Status: approved in chat, pending spec review

## Goal

Add a mock-only operator endpoint that sets or removes stock lock reasons on an ASRS stock row and
emits the `PostStockLockChanged` webhook (HIS Appendix §8.1.3) to the HOST. This makes the
IN-05 "stock lock change" flow drivable from Swagger without a goods-out damage pick or inventory count.

## Non-goals

- No change to the ASRS stock unique key (`client_number`, `article_number`, `pack_size`). Treating
  `reservationCode` as part of the key across inbound / goods-out / import is a separate change.
- No inbound `PostStockLockRequest` (out of scope for Tacoma, KIS-010).
- No quantity change.

## Endpoint

`POST /oneapi/v1/stock/operator/lock?wait=true` in the existing `StockOperatorController`
(same `wait` semantics as `/correct`: `wait=true` delivers the callback synchronously and returns the
APIC result via `WebhookWaitResponse`; `wait=false` dispatches async and returns `OneApiOkResponse`).

### Request body — `StockLockOperatorRequest` (new DTO, `api/dto`)

| Field | Type | Rule |
|-------|------|------|
| `action` | `LOCK` \| `UNLOCK` | required |
| `clientNumber` | string | required |
| `articleNumber` | string | required |
| `packSize` | integer | required (stored via `PackSizeKeys.toKey`) |
| `reservationCode` | string | required — Country of Origin; must match the stored row |
| `stockLockReasons` | string[] | values must be in the Tacoma enum; required non-empty for `LOCK`; optional for `UNLOCK` (empty/null = remove all) |
| `stationName` | string | optional, copied to webhook |
| `reason` | string | optional, copied to webhook; default `OPERATOR_LOCK` / `OPERATOR_UNLOCK` |

Valid `stockLockReasons` (OpenAPI `StockLockReason`, HIS Appendix §4.2.3):
`DEFAULT`, `EXPIRED`, `HOST`, `LOCATION_LOCKED`, `LOCKED_FOR_VISION_CHECK`, `LOST`, `QS_REQ`,
`SRS_SYSTEM_BROKEN`, `SUBSYSTEM_LOCKED`, `TIME_TO_EXPIRE`. Defined once as a Java enum/constant set
(`StockLockReasons`) in `service` and referenced by the DTO validation and Swagger schema.

## Behaviour

1. Look up the ASRS row by `(clientNumber, articleNumber, packSizeKey)`.
2. Compare `reservationCode` (trimmed; null/blank treated as empty). No row or mismatch → **404**
   with `lineCode` `E-AKO-STOC-0003` (mock code: stock not found for key + country of origin).
3. Validate reasons: any value outside the enum → **400** `E-AKO-GENR-0002`. `LOCK` with
   empty/null list → **400** `E-AKO-GENR-0002`.
4. Apply:
   - `LOCK`: union of existing reasons and requested reasons (no duplicates, keep order).
     `added` = reasons not previously present.
   - `UNLOCK`: remove requested reasons; empty/null → remove all. `removed` = reasons actually present
     and removed.
   Persist the resulting list as JSON on `AsrsStockEntity.stockLockReasonsJson` (null when empty).
5. Emit `PostStockLockChanged` even when `added`/`removed` is empty (no-op still reports current state):
   - `eventId` = random UUID
   - `stockLockRequestReference` = null
   - `processedQuantity` = current row quantity
   - `addedStockLocks` = `added` (LOCK) or null (UNLOCK)
   - `removedStockLocks` = `removed` (UNLOCK) or null (LOCK)
   - `stationName`, `reason`, `userCode=null`, `eventTime=now`
   - `processedStock` = `StockEntry` with `packUnit` (client/article/packSize), quantity, stockType,
     lotNumber, dateMark, serialNumber, `reservationCode`, and the **resulting** `stockLockReasons`.

Service logic lives in `AsrsStockService` as a new method
`LockChange changeLocks(client, article, packSizeKey, reservationCode, action, reasons)` returning the
entity snapshot plus `added`/`removed`, or a typed not-found result. Controller maps results to HTTP.

## Error responses

| Case | HTTP | lineCode |
|------|------|----------|
| row missing / CoO mismatch | 404 | `E-AKO-STOC-0003` |
| invalid reason value, or LOCK with no reasons | 400 | `E-AKO-GENR-0002` |
| missing required fields | 400 | Bean validation (existing handler) |

Body uses the existing `OneApiOkResponse` shape for consistency with other operator endpoints.

## Docs

- README operator table: add the `/stock/operator/lock` row; update IN-05 status note.
- OpenAPI JSON (`openapi/…aligned.json`, `KiSoftopenapi.json`): the mock tag description for
  Stock Operator mentions lock/unlock. Generated springdoc output covers the endpoint itself.

## Tests

`StockOperatorLockTest` (SpringBootTest + MockMvc, `test` profile, callback mocked/disabled):

1. LOCK adds reasons, persists them, `GetInventoryItems` shows them, webhook payload has
   `addedStockLocks` and `processedStock.reservationCode`.
2. LOCK of an already-present reason yields empty `addedStockLocks` and no duplicate.
3. UNLOCK removes specific reason; UNLOCK with empty list removes all.
4. Invalid reason → 400 `E-AKO-GENR-0002`.
5. CoO mismatch and unknown article → 404 `E-AKO-STOC-0003`.
