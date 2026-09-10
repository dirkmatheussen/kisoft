# ASRS reservationCode Key Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ASRS inventory identity `(clientNumber, articleNumber, packSize, reservationCode)` so inbound create-vs-update and all stock ops are CoO-aware.

**Architecture:** Liquibase expands the unique constraint; `ReservationCodes.normalize` maps null/blank → `""`. `AsrsStockService` looks up and mutates by the 4-key. Callers pass CoO from the message (or `""` when absent). Pack-unit delete guard keeps a 3-key “any CoO still has qty” check.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Spring Data JPA, Liquibase, H2, JUnit 5, Mockito, AssertJ, MockMvc. Build: `mvn -q test -Dtest=...`.

Spec: `docs/superpowers/specs/2026-09-10-asrs-reservation-code-key-design.md`

## Global Constraints

- Unique key columns (exact): `client_number`, `article_number`, `pack_size`, `reservation_code`
- Constraint name stays `uq_asrs_stock_key` (drop then re-add)
- `reservation_code` becomes `NOT NULL` with default `''`
- Normalize: `null` or blank (after trim) → `""`; otherwise trimmed string — helper `ReservationCodes.normalize(String)`
- Scope: inbound, goods-out, lock operator, import, warehouse-internal, inventory count/correct, OData reads (natural)
- Pack-unit MA-01 guard: still block delete if **any** CoO row for that article+packSize has qty > 0
- Commit only files listed in each task (never `git add -A`)
- Branch work from current `main`

---

## File Structure

| File | Responsibility |
|------|----------------|
| Create `…/service/ReservationCodes.java` | Normalize CoO for keys |
| Modify `…/db/changelog/db.changelog-master.yaml` | Changeset `005-asrs-stock-reservation-key` |
| Modify `…/persistence/AsrsStockEntity.java` | 4-col unique; `reservationCode` NOT NULL default `""`; ctor sets CoO |
| Modify `…/persistence/AsrsStockRepository.java` | 4-key find + exists |
| Modify `…/service/AsrsStockService.java` | All qty APIs take reservationCode |
| Modify inbound / goods-out / warehouse / inventory / import / stock operator / pack-unit callers | Pass CoO |
| Modify `SpontaneousStockCorrection` | Optional `reservationCode` |
| Tests + README | Behaviour + call-site updates |

---

### Task 1: Normalize helper + Liquibase + entity + repository

**Files:**
- Create: `src/main/java/com/knapp/kisoft/mock/service/ReservationCodes.java`
- Modify: `src/main/resources/db/changelog/db.changelog-master.yaml` (append after changeset `004-asrs-stock-attributes`)
- Modify: `src/main/java/com/knapp/kisoft/mock/persistence/AsrsStockEntity.java`
- Modify: `src/main/java/com/knapp/kisoft/mock/persistence/AsrsStockRepository.java`
- Test: `src/test/java/com/knapp/kisoft/mock/service/ReservationCodesTest.java`

**Interfaces:**
- Produces: `static String ReservationCodes.normalize(String value)` → `""` for null/blank, else `trim()`
- Produces: `AsrsStockRepository.findByClientNumberAndArticleNumberAndPackSizeAndReservationCode(String, String, String, String)`
- Produces: `AsrsStockRepository.existsByClientNumberAndArticleNumberAndPackSizeAndReservationCodeAndQuantityGreaterThan(...)`
- Keeps: `findByClientNumberAndArticleNumber`, and 3-key `existsByClientNumberAndArticleNumberAndPackSizeAndQuantityGreaterThan` (MA-01)
- Entity: `@Column(name = "reservation_code", nullable = false, length = 64)` with `columnDefinition` not required; Java field default `""`; ctor `AsrsStockEntity(client, article, packSize, reservationCode, quantity)` — **replace** the old 4-arg ctor (update all `new AsrsStockEntity(...)` in the same commit / follow-up Task 2)
- UniqueConstraint columnNames: `client_number, article_number, pack_size, reservation_code`

- [ ] **Step 1: Write the failing test**

```java
package com.knapp.kisoft.mock.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationCodesTest {

    @Test
    void normalize_mapsNullAndBlankToEmptyString() {
        assertThat(ReservationCodes.normalize(null)).isEqualTo("");
        assertThat(ReservationCodes.normalize("")).isEqualTo("");
        assertThat(ReservationCodes.normalize("  ")).isEqualTo("");
        assertThat(ReservationCodes.normalize(" PL ")).isEqualTo("PL");
        assertThat(ReservationCodes.normalize("BE")).isEqualTo("BE");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/Dirk/Documents/Cursur-Projects/knappMock && mvn -q test -Dtest=ReservationCodesTest 2>&1 | tail -20`
Expected: compilation failure — `ReservationCodes` does not exist.

- [ ] **Step 3: Create `ReservationCodes`**

```java
package com.knapp.kisoft.mock.service;

/**
 * Normalizes reservationCode (Country of Origin) for use as part of the ASRS stock key.
 * Null/blank → empty string so the DB unique constraint is stable under H2.
 */
public final class ReservationCodes {

    private ReservationCodes() {}

    public static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
```

- [ ] **Step 4: Append Liquibase changeset** at the end of `db.changelog-master.yaml`:

```yaml
  - changeSet:
      id: 005-asrs-stock-reservation-key
      author: cursor
      changes:
        - sql:
            sql: UPDATE asrs_stock SET reservation_code = '' WHERE reservation_code IS NULL
        - addNotNullConstraint:
            tableName: asrs_stock
            columnName: reservation_code
            columnDataType: varchar(64)
            defaultNullValue: ''
        - addDefaultValue:
            tableName: asrs_stock
            columnName: reservation_code
            defaultValue: ''
        - dropUniqueConstraint:
            tableName: asrs_stock
            constraintName: uq_asrs_stock_key
        - addUniqueConstraint:
            tableName: asrs_stock
            columnNames: client_number, article_number, pack_size, reservation_code
            constraintName: uq_asrs_stock_key
```

If H2/Liquibase rejects `addNotNullConstraint` + `defaultNullValue` together, use equivalent `sql` changes for that dialect (still one changeset id `005-asrs-stock-reservation-key`).

- [ ] **Step 5: Update `AsrsStockEntity`**

- UniqueConstraint → include `reservation_code`
- `reservationCode` column: `nullable = false`, initialize field to `""`
- Replace constructor with:

```java
public AsrsStockEntity(String clientNumber, String articleNumber, String packSize,
                       String reservationCode, int quantity) {
    this.clientNumber = clientNumber;
    this.articleNumber = articleNumber;
    this.packSize = packSize;
    this.reservationCode = ReservationCodes.normalize(reservationCode);
    this.quantity = quantity;
}
```

Import `com.knapp.kisoft.mock.service.ReservationCodes`. Temporarily fix compile errors in `AsrsStockService` / import / tests by passing `""` as the 4th ctor arg where needed so the module compiles (full API change is Task 2). Prefer fixing only direct `new AsrsStockEntity(...)` call sites that fail compilation.

- [ ] **Step 6: Update `AsrsStockRepository`**

```java
Optional<AsrsStockEntity> findByClientNumberAndArticleNumberAndPackSizeAndReservationCode(
        String clientNumber, String articleNumber, String packSize, String reservationCode);

boolean existsByClientNumberAndArticleNumberAndPackSizeAndReservationCodeAndQuantityGreaterThan(
        String clientNumber, String articleNumber, String packSize, String reservationCode, int qty);
```

Keep the existing 3-key `exists…QuantityGreaterThan` and `findByClientNumberAndArticleNumberAndPackSize` until Task 2 removes unused methods (or leave 3-key find unused until deleted in Task 2).

- [ ] **Step 7: Run ReservationCodesTest + a smoke Spring context test**

Run: `mvn -q test -Dtest=ReservationCodesTest,InventoryImportServiceTest 2>&1 | tail -30`
Expected: `ReservationCodesTest` green. `InventoryImportServiceTest` may still compile if ctor updated; if Liquibase fails, fix changeset until context starts.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/knapp/kisoft/mock/service/ReservationCodes.java \
        src/test/java/com/knapp/kisoft/mock/service/ReservationCodesTest.java \
        src/main/resources/db/changelog/db.changelog-master.yaml \
        src/main/java/com/knapp/kisoft/mock/persistence/AsrsStockEntity.java \
        src/main/java/com/knapp/kisoft/mock/persistence/AsrsStockRepository.java
# plus any minimal ctor call-site fixes required to compile
git commit -m "feat(stock): ASRS unique key includes reservation_code (schema + normalize)"
```

---

### Task 2: `AsrsStockService` 4-key quantity APIs

**Files:**
- Modify: `src/main/java/com/knapp/kisoft/mock/service/AsrsStockService.java`
- Modify: `src/test/java/com/knapp/kisoft/mock/service/AsrsStockLockTest.java` (repo stubs → 4-key find)
- Create: `src/test/java/com/knapp/kisoft/mock/service/AsrsStockReservationKeyTest.java` (Mockito or `@SpringBootTest` — prefer SpringBootTest + real H2 for unique constraint)

**Interfaces:**
- Consumes: `ReservationCodes.normalize`, 4-key repository methods, new entity ctor
- Produces (exact signatures):

```java
void addStock(String clientNumber, String articleNumber, String packSize, int delta);
// → addStock(..., ReservationCodes.normalize(null), delta, null) i.e. CoO ""

void addStock(String clientNumber, String articleNumber, String packSize, int delta,
              AsrsStockAttributes attributes);
// → CoO = normalize(attributes != null ? attributes.reservationCode() : null)

void addStock(String clientNumber, String articleNumber, String packSize, String reservationCode,
              int delta, AsrsStockAttributes attributes);

int removeStock(String clientNumber, String articleNumber, String packSize, String reservationCode, int qty);

int setQuantity(String clientNumber, String articleNumber, String packSize, String reservationCode, int counted);

int getQuantity(String clientNumber, String articleNumber, String packSize, String reservationCode);

boolean hasStock(String clientNumber, String articleNumber, String packSize);
// MA-01: any CoO with qty > 0 (existing 3-key exists method)

boolean hasStock(String clientNumber, String articleNumber, String packSize, String reservationCode);
// specific 4-key

int availableForArticle(String clientNumber, String articleNumber); // all CoOs (unchanged)

int availableForArticle(String clientNumber, String articleNumber, String reservationCode);
// sum pack sizes for one CoO

Optional<LockChange> changeLocks(...); // findBy...AndReservationCode(normalize(reservationCode)); no post-check
```

- `applyAttributes`: **do not** call `setReservationCode` (key is fixed at create). Other fields unchanged; locks only if `stockLockReasons != null`.
- When creating a new entity in `addStock`/`setQuantity`, set reservation via ctor; then apply attributes without overwriting CoO.

- [ ] **Step 1: Write failing SpringBootTest for create vs update by CoO**

```java
package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.persistence.AsrsStockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AsrsStockReservationKeyTest {

    @Autowired AsrsStockService asrsStock;
    @Autowired AsrsStockRepository repo;

    @Test
    void sameArticlePackDifferentCoo_createsTwoRows() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "PL", 6, null);
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "SE", 4, null);

        assertThat(repo.findAll()).hasSize(2);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "PL")).isEqualTo(6);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "SE")).isEqualTo(4);
    }

    @Test
    void sameArticlePackSameCoo_incrementsQuantity() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "PL", 6,
                new AsrsStockAttributes(null, null, null, null, "PL", null));
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "PL", 3,
                new AsrsStockAttributes(null, null, null, null, "PL", null));

        assertThat(repo.findAll()).hasSize(1);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "PL")).isEqualTo(9);
    }

    @Test
    void blankAndNullReservationCode_shareTheEmptyKey() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", 5, null);
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "  ", 2, null);
        assertThat(repo.findAll()).hasSize(1);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "")).isEqualTo(7);
    }

    @Test
    void removeStock_onlyAffectsMatchingCoo() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "PL", 6, null);
        asrsStock.addStock("VPNA-TAC", "ART-1", "1", "SE", 6, null);
        assertThat(asrsStock.removeStock("VPNA-TAC", "ART-1", "1", "PL", 4)).isEqualTo(4);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "PL")).isEqualTo(2);
        assertThat(asrsStock.getQuantity("VPNA-TAC", "ART-1", "1", "SE")).isEqualTo(6);
    }
}
```

- [ ] **Step 2: Run to verify failure / wrong behaviour**

Run: `mvn -q test -Dtest=AsrsStockReservationKeyTest 2>&1 | tail -40`
Expected: fail compile (missing overloads) or fail assertions (still 3-key).

- [ ] **Step 3: Implement service API** as in Interfaces. Core of `addStock` with explicit CoO:

```java
@Transactional
public void addStock(String clientNumber, String articleNumber, String packSize, String reservationCode,
                     int delta, AsrsStockAttributes attributes) {
    if (delta <= 0) return;
    String coo = ReservationCodes.normalize(reservationCode);
    AsrsStockEntity entity = repo
            .findByClientNumberAndArticleNumberAndPackSizeAndReservationCode(
                    clientNumber, articleNumber, packSize, coo)
            .orElseGet(() -> new AsrsStockEntity(clientNumber, articleNumber, packSize, coo, 0));
    entity.setQuantity(entity.getQuantity() + delta);
    if (attributes != null) {
        applyAttributes(entity, attributes);
    }
    repo.save(entity);
}
```

Wire convenience overloads; update `removeStock`/`setQuantity`/`getQuantity`/`changeLocks` similarly. Remove private `normalize` from `AsrsStockService` — use `ReservationCodes`. Update class javadoc to say 4-key.

- [ ] **Step 4: Fix `AsrsStockLockTest` stubs** to mock
`findByClientNumberAndArticleNumberAndPackSizeAndReservationCode(..., "PL")` (and `""` for blank test). Entity rows must be constructed with matching CoO. Update `addStock` call to the new signature.

- [ ] **Step 5: Run focused tests**

Run: `mvn -q test -Dtest=AsrsStockReservationKeyTest,AsrsStockLockTest,ReservationCodesTest 2>&1 | tail -30`
Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/knapp/kisoft/mock/service/AsrsStockService.java \
        src/test/java/com/knapp/kisoft/mock/service/AsrsStockReservationKeyTest.java \
        src/test/java/com/knapp/kisoft/mock/service/AsrsStockLockTest.java
git commit -m "feat(stock): AsrsStockService quantity APIs use reservationCode key"
```

---

### Task 3: Wire callers (inbound, goods-out, warehouse, inventory, import, operators)

**Files:**
- Modify: `InboundDeliveryLifecycleService.java` — `addStock`/`removeStock`/`releaseExpectedStock`/`correctAutoStockShortfall` pass line CoO (for shortfall/release: resolve CoO from matching `InboundDeliveryLine` by `lineReference`)
- Modify: `GoodsOutOrderLifecycleService.java` — `availableStock` / `removeStock` use `line.reservationCode()`; when packSize absent use `availableForArticle(client, article, normalize(line.reservationCode()))`
- Modify: `WarehouseInternalController.java` — pass `""` (or add optional CoO on request DTOs if already present; otherwise `""`)
- Modify: `InventoryRequestLifecycleService.java` — `setQuantity` with CoO from original request line matching `lineReference` (`request.inventoryRequestLines()` / equivalent), fallback `""`
- Modify: `InventoryImportService.java` — ctor with `ReservationCodes.normalize(item.reservationCode())`; stop relying on post-set alone for key
- Modify: `StockOperatorController.java` — `setQuantity`/`correct` use `request.reservationCode()` after DTO change
- Modify: `SpontaneousStockCorrection.java` — add optional `String reservationCode`
- Modify: `PackUnitController.java` — keep `hasStock(client, article, packSize)` 3-key MA-01
- Modify: any other compile breakers from signature change

**Interfaces:**
- Consumes Task 2 signatures exactly.
- Inbound helper (private in lifecycle):

```java
private static String cooOf(InboundDelivery delivery, String lineReference) {
    if (delivery.inboundDeliveryLines() == null) return "";
    return delivery.inboundDeliveryLines().stream()
            .filter(l -> lineReference.equals(l.lineReference()))
            .map(l -> ReservationCodes.normalize(l.reservationCode()))
            .findFirst()
            .orElse("");
}
```

- [ ] **Step 1: Compile the tree**

Run: `mvn -q -DskipTests compile test-compile 2>&1 | tail -40`
Expected: list of broken call sites. Fix each.

- [ ] **Step 2: Inbound create vs update integration test** (new or extend existing chapter test)

```java
@Test
void inboundAutoStock_differentReservationCodes_createSeparateInventoryRows() {
    // enable inbound-auto-stock for this test via @TestPropertySource if needed
    // Post two inbound deliveries (or one with two lines) same article/packSize, CoO PL vs SE
    // GET /inventoryItem → two value entries with quantities matching each line
}
```

Put in `src/test/java/com/knapp/kisoft/mock/api/InboundReservationKeyTest.java` (or extend `Chapter5…` if that suite already boots inbound). Use `TestFixtures.RESERVATION_CODE` (`BE`) where fixtures already assume one CoO.

- [ ] **Step 3: Update `GoodsOutStockAccountingTest` Mockito**

```java
when(asrsStock.getQuantity("OB", "ART-1", "1", "BE")).thenReturn(5);
verify(asrsStock).removeStock("OB", "ART-1", "1", "BE", 5);
// line() already uses reservationCode "BE"
```

Update `never()` matchers to include the extra String arg (`anyString()` × 4 + `anyInt()`).

- [ ] **Step 4: Update remaining tests** that call `addStock`/`getQuantity`/`removeStock`/`setQuantity`:
  - Prefer passing `TestFixtures.RESERVATION_CODE` (`"BE"`) when the goods-out fixture uses `BE`
  - Prefer `"PL"` when inventory import fixtures use `PL`
  - Prefer `""` when the test never set a CoO before
  - `StockOperatorLockControllerTest`: `addStock(..., "PL", 6, attributes)` and repo find 4-key
  - `InventoryImportServiceTest`: `getQuantity(..., "PL")` (import payload uses reservationCode PL)
  - `Chapter5GoodsOutInventoryTest` / `ODataReadIntegrationTest` / `ApiIntegrationTest`: seed stock with the CoO the order lines will request

- [ ] **Step 5: Full suite**

Run: `mvn -q clean test 2>&1 | grep -E "Tests run: [0-9]+, Failures|BUILD" | tail -5`
Expected: `Failures: 0`, `BUILD SUCCESS` (test count ≥ 88 + new tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java src/test/java
# only paths you changed — verify with git status / git diff --cached --stat
git commit -m "feat(stock): pass reservationCode through inbound, goods-out, and other stock callers"
```

---

### Task 4: Docs + README alignment

**Files:**
- Modify: `README.md` — note ASRS unique key includes `reservationCode`; inbound auto-stock create vs update; goods-out deducts per CoO; update operator lock wording from “match check” to “unique key”
- No OpenAPI Product path change required (mock behaviour only)

- [ ] **Step 1: README edits** — near ASRS / inbound / stock sections, add one short paragraph:

```markdown
ASRS inventory rows are unique on `(clientNumber, articleNumber, packSize, reservationCode)`.
Blank/missing Country of Origin is stored as an empty string. Inbound booking creates a new row
when CoO differs and increments quantity when the full key matches. Goods-out availability and
pick deduction use the order line's `reservationCode`.
```

Update the `/stock/operator/lock` row if it still says CoO is only a match check.

- [ ] **Step 2: Verify** `rg -n "reservationCode|Country of Origin|ASRS inventory" README.md | head -20`

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: ASRS inventory unique key includes reservationCode"
```

---

## Self-review

- Spec coverage: Liquibase 4-key (T1), normalize `""` (T1), service APIs (T2), inbound create/update (T2+T3), goods-out per CoO (T3), lock 4-key find (T2), import (T3), warehouse `""` (T3), MA-01 any-CoO guard (T2 `hasStock` 3-key), tests (T2+T3), docs (T4).
- Type consistency: all qty methods take `reservationCode` after packSize; convenience overloads default to `""` / attributes CoO.
- No placeholders; `InventoryCountLine` CoO resolved from parent request lines, not invented.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-10-asrs-reservation-code-key.md`. Two execution options:

**1. Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
**2. Inline Execution** — execute in this session with checkpoints  

Which approach?
