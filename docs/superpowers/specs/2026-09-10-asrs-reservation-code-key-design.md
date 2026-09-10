# ASRS inventory key includes reservationCode — design

Date: 2026-09-10
Status: approved in chat, pending spec review

## Goal

Treat `reservationCode` (Country of Origin) as part of the ASRS inventory identity so inbound
delivery stock booking creates a **new** inventory row when CoO differs, and **updates** quantity
when the full key matches. Apply the same 4-part key consistently across all stock operations.

## Decisions

| Topic | Choice |
|-------|--------|
| Unique key | `(clientNumber, articleNumber, packSize, reservationCode)` |
| Scope | Everywhere: inbound, goods-out, lock operator, import, warehouse-internal, OData reads |
| Blank / null CoO | Normalize to `""`; column `NOT NULL DEFAULT ''` |
| Approach | Liquibase migration + `AsrsStockService` API change (not Hibernate ddl-auto) |

## Non-goals

- Changing pack-unit master-data keys (still 3-part).
- Changing inventory-request duplicate detection (already CoO-aware).
- Version bump beyond what the release commit needs (do at ship time if desired).

## Schema

New Liquibase changeset `005-asrs-stock-reservation-key`:

1. `UPDATE asrs_stock SET reservation_code = '' WHERE reservation_code IS NULL`
2. Make `reservation_code` `NOT NULL` with default `''`
3. Drop unique constraint `uq_asrs_stock_key`
4. Add unique constraint `uq_asrs_stock_key` on
   `(client_number, article_number, pack_size, reservation_code)`

JPA `@UniqueConstraint` on `AsrsStockEntity` matches. Existing DBs with only one row per 3-key
migrate cleanly (each row keeps its CoO or `''`).

## Normalization

Single helper `ReservationCodes.normalize(String)`:
- `null` or blank (after trim) → `""`
- otherwise → trimmed string

All writers and lookups pass through this helper before persistence or query.

## Service API

`AsrsStockService` methods that today take `(client, article, packSize)` for quantity identity gain
`reservationCode` (already normalized by callers or inside the method):

- `addStock(...)` — find by 4-key; if present increment qty; else create row with that CoO
- `removeStock(...)` — deduct from the matching 4-key row only
- `setQuantity(...)` — absolute set on the matching 4-key row (create if missing)
- `getQuantity(...)` / `hasStock(...)` — 4-key
- `changeLocks(...)` — find by 4-key directly (drop the post-lookup CoO equality check)
- `availableForArticle(client, article)` — sum all CoO rows for that article (kept for packSize-less paths)
- Optional: `availableForArticle(client, article, reservationCode)` — sum pack sizes for one CoO

Repository: add
`findByClientNumberAndArticleNumberAndPackSizeAndReservationCode(...)`.

Attribute writes (`applyAttributes`): still last-write-wins on the **matched** row; do not overwrite
`stockLockReasons` when the attribute list is null (existing behaviour).

## Callers

| Caller | Behaviour |
|--------|-----------|
| Inbound auto-stock / loadUnit receipt | `addStock` with `line.reservationCode()` / receipt CoO → create vs update by 4-key |
| Goods-out intake + pick | Use `GoodsOutOrderLine.reservationCode` (already `@NotBlank`) for available check and `removeStock` |
| Stock lock operator | 4-key lookup; `reservationCode` remains required on the request |
| Inventory import | Persist normalized CoO as part of the row key (uniquify-articles path unchanged) |
| Warehouse-internal | Missing CoO → `""` |
| `GetInventoryItems` | Unchanged shape; naturally returns one OData row per 4-key |

## Errors / edge cases

- Goods-out requesting CoO `PL` when only `SE` stock exists → `E-AKO-STOC-0001` (not enough stock), same as today when qty is zero.
- Lock operator with wrong CoO → still `E-AKO-STOC-0003`.
- Two inbound lines same article/pack, different CoO → two ASRS rows; `GetInventoryItems` shows both.

## Tests

1. Inbound: same article/pack + same CoO twice → one row, qty summed.
2. Inbound: same article/pack + different CoO → two rows.
3. Goods-out: deducts only the matching CoO row; other CoO qty untouched.
4. Lock operator: still works against a 4-key row; mismatch CoO → 404.
5. Liquibase: existing null CoO becomes `''`; unique constraint holds.
6. Update existing unit tests that call `addStock`/`getQuantity`/`removeStock` without CoO to pass `""` or an explicit CoO.

## Docs

- README: ASRS key note; inbound auto-stock and IN-05/operator lock already mention CoO — update to say CoO is part of the unique key.
- Operator lock docs already describe 4-field match; align wording with “unique key includes reservationCode”.
