# Operator Stock Lock / Unlock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `POST /oneapi/v1/stock/operator/lock` that adds/removes stock lock reasons on an ASRS stock row (matched on client, article, packSize and reservationCode) and emits `PostStockLockChanged`.

**Architecture:** A small pure helper (`StockLockReasons`) holds the Tacoma enum values. `AsrsStockService.changeLocks(...)` does the lookup + CoO match + list merge and persists the JSON column. `StockOperatorController` gets a second endpoint next to `/correct` that validates, calls the service, builds the `StockLockChanged` payload and delivers it through `ReplyCallbackService` (sync when `wait=true`, async otherwise).

**Tech Stack:** Java 21, Spring Boot 3.2.5, Spring Data JPA (H2), Jackson, springdoc, JUnit 5, Mockito, AssertJ, MockMvc. Build with Maven (`mvn -q test -Dtest=...`).

Spec: `docs/superpowers/specs/2026-09-10-stock-lock-operator-design.md`

## Global Constraints

- Do **not** change the `asrs_stock` unique key or any Liquibase changeset.
- Quantity is never modified by this endpoint.
- Valid reasons (exact strings): `DEFAULT`, `EXPIRED`, `HOST`, `LOCATION_LOCKED`, `LOCKED_FOR_VISION_CHECK`, `LOST`, `QS_REQ`, `SRS_SYSTEM_BROKEN`, `SUBSYSTEM_LOCKED`, `TIME_TO_EXPIRE`.
- Error codes: not found / CoO mismatch → HTTP 404 `E-AKO-STOC-0003`; invalid reason or LOCK without reasons → HTTP 400 `E-AKO-GENR-0002`.
- Webhook is sent even when nothing effectively changed.
- Base path in tests: context path `/kisoft`, API `/kisoft/oneapi/v1`.
- Commit only the files listed in each task (the working tree has unrelated uncommitted changes — never `git add -A`).

---

## File Structure

| File | Responsibility |
|------|----------------|
| Create `src/main/java/com/knapp/kisoft/mock/service/StockLockReasons.java` | Constant set of valid reason codes + `invalid(list)` helper |
| Create `src/main/java/com/knapp/kisoft/mock/api/dto/StockLockOperatorRequest.java` | Request DTO (`action`, key fields, `reservationCode`, `stockLockReasons`, `stationName`, `reason`) |
| Modify `src/main/java/com/knapp/kisoft/mock/service/AsrsStockService.java` | `LockAction`, `LockChange`, `changeLocks(...)` |
| Modify `src/main/java/com/knapp/kisoft/mock/api/StockOperatorController.java` | `POST /lock` endpoint |
| Create `src/test/java/com/knapp/kisoft/mock/service/AsrsStockLockTest.java` | Service unit tests (Mockito repo) |
| Create `src/test/java/com/knapp/kisoft/mock/api/StockOperatorLockControllerTest.java` | MockMvc tests incl. webhook payload capture |
| Modify `README.md`, `openapi/api-docs-v4.0.0-KiSoft-2.12.2-aligned.json`, `KiSoftopenapi.json` | Docs / tag description |

---

### Task 1: `StockLockReasons` helper + service `changeLocks`

**Files:**
- Create: `src/main/java/com/knapp/kisoft/mock/service/StockLockReasons.java`
- Modify: `src/main/java/com/knapp/kisoft/mock/service/AsrsStockService.java` (add nested types + method; keep everything else)
- Test: `src/test/java/com/knapp/kisoft/mock/service/AsrsStockLockTest.java`

**Interfaces:**
- Produces:
  - `StockLockReasons.ALL : List<String>` (ordered as in Global Constraints)
  - `static List<String> StockLockReasons.invalid(List<String> reasons)` → the entries not in `ALL` (empty list when all valid or input null)
  - `enum AsrsStockService.LockAction { LOCK, UNLOCK }`
  - `record AsrsStockService.LockChange(AsrsStockEntity entity, List<String> resultingReasons, List<String> added, List<String> removed)` — `resultingReasons` is `null` when the row has no locks left (matches `readLockReasons` convention); `added`/`removed` are never null (may be empty)
  - `Optional<LockChange> AsrsStockService.changeLocks(String clientNumber, String articleNumber, String packSizeKey, String reservationCode, LockAction action, List<String> reasons)` — `Optional.empty()` when no row or CoO mismatch; `reasons` may be null/empty (for UNLOCK = remove all)

- [ ] **Step 1: Write the failing tests**

```java
package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.persistence.AsrsStockEntity;
import com.knapp.kisoft.mock.persistence.AsrsStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.knapp.kisoft.mock.service.AsrsStockService.LockAction.LOCK;
import static com.knapp.kisoft.mock.service.AsrsStockService.LockAction.UNLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsrsStockLockTest {

    @Mock AsrsStockRepository repo;
    AsrsStockService service;
    AsrsStockEntity row;

    @BeforeEach
    void setUp() {
        service = new AsrsStockService(repo);
        row = new AsrsStockEntity("VPNA-TAC", "VO 25133699", "1", 6);
        row.setReservationCode("PL");
    }

    @Test
    void invalidReasons_reportsUnknownCodesOnly() {
        assertThat(StockLockReasons.invalid(List.of("QS_REQ", "BOGUS", "HOST"))).containsExactly("BOGUS");
        assertThat(StockLockReasons.invalid(null)).isEmpty();
        assertThat(StockLockReasons.ALL).hasSize(10).contains("LOCKED_FOR_VISION_CHECK", "TIME_TO_EXPIRE");
    }

    @Test
    void lock_addsNewReasonsWithoutDuplicates() {
        row.setStockLockReasonsJson("[\"HOST\"]");
        when(repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "VO 25133699", "1"))
                .thenReturn(Optional.of(row));

        var change = service.changeLocks("VPNA-TAC", "VO 25133699", "1", "PL", LOCK, List.of("HOST", "QS_REQ")).orElseThrow();

        assertThat(change.added()).containsExactly("QS_REQ");
        assertThat(change.removed()).isEmpty();
        assertThat(change.resultingReasons()).containsExactly("HOST", "QS_REQ");
        assertThat(row.getStockLockReasonsJson()).isEqualTo("[\"HOST\",\"QS_REQ\"]");
        verify(repo).save(row);
    }

    @Test
    void unlock_removesGivenReasons_andEmptyListRemovesAll() {
        row.setStockLockReasonsJson("[\"HOST\",\"QS_REQ\"]");
        when(repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "VO 25133699", "1"))
                .thenReturn(Optional.of(row));

        var first = service.changeLocks("VPNA-TAC", "VO 25133699", "1", "PL", UNLOCK, List.of("QS_REQ", "LOST")).orElseThrow();
        assertThat(first.removed()).containsExactly("QS_REQ");
        assertThat(first.resultingReasons()).containsExactly("HOST");

        var second = service.changeLocks("VPNA-TAC", "VO 25133699", "1", "PL", UNLOCK, null).orElseThrow();
        assertThat(second.removed()).containsExactly("HOST");
        assertThat(second.resultingReasons()).isNull();
        assertThat(row.getStockLockReasonsJson()).isNull();
    }

    @Test
    void changeLocks_isEmptyWhenReservationCodeDiffersOrRowMissing() {
        when(repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "VO 25133699", "1"))
                .thenReturn(Optional.of(row));
        assertThat(service.changeLocks("VPNA-TAC", "VO 25133699", "1", "SE", LOCK, List.of("HOST"))).isEmpty();

        when(repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "UNKNOWN", "1"))
                .thenReturn(Optional.empty());
        assertThat(service.changeLocks("VPNA-TAC", "UNKNOWN", "1", "PL", LOCK, List.of("HOST"))).isEmpty();
        verify(repo, never()).save(any());
    }

    @Test
    void changeLocks_treatsBlankAndNullReservationCodeAsEqual() {
        row.setReservationCode(null);
        when(repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "VO 25133699", "1"))
                .thenReturn(Optional.of(row));
        assertThat(service.changeLocks("VPNA-TAC", "VO 25133699", "1", "  ", LOCK, List.of("HOST"))).isPresent();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/Dirk/Documents/Cursur-Projects/knappMock && mvn -q test -Dtest=AsrsStockLockTest 2>&1 | tail -20`
Expected: compilation error — `StockLockReasons` / `LockAction` / `changeLocks` do not exist.

- [ ] **Step 3: Create `StockLockReasons`**

```java
package com.knapp.kisoft.mock.service;

import java.util.List;

/**
 * stockLockReasons values configured for VOLVO TRUCKS Tacoma (HIS Appendix §4.2.3,
 * OpenAPI schema {@code StockLockReason}).
 */
public final class StockLockReasons {

    public static final List<String> ALL = List.of(
            "DEFAULT",
            "EXPIRED",
            "HOST",
            "LOCATION_LOCKED",
            "LOCKED_FOR_VISION_CHECK",
            "LOST",
            "QS_REQ",
            "SRS_SYSTEM_BROKEN",
            "SUBSYSTEM_LOCKED",
            "TIME_TO_EXPIRE");

    private StockLockReasons() {}

    /** Entries of {@code reasons} that are not valid Tacoma lock reasons (empty when all valid or input null). */
    public static List<String> invalid(List<String> reasons) {
        if (reasons == null) return List.of();
        return reasons.stream().filter(r -> r == null || !ALL.contains(r)).toList();
    }
}
```

- [ ] **Step 4: Add `LockAction`, `LockChange`, `changeLocks` to `AsrsStockService`**

Add these imports at the top of `AsrsStockService.java`:

```java
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
```

Add inside the class, after `listAll()` and before `readLockReasons(...)`:

```java
    public enum LockAction { LOCK, UNLOCK }

    /**
     * Outcome of {@link #changeLocks}. {@code resultingReasons} is null when the row has no locks left;
     * {@code added} / {@code removed} list the reasons that effectively changed (never null).
     */
    public record LockChange(AsrsStockEntity entity, List<String> resultingReasons,
                             List<String> added, List<String> removed) {}

    /**
     * Add or remove lock reasons on the ASRS row matching (client, article, packSize) whose stored
     * reservationCode (Country of Origin) equals {@code reservationCode} (blank == null).
     * UNLOCK with a null/empty list removes all locks. Quantity is untouched.
     *
     * @return empty when no row matches the key or the reservation code differs
     */
    @Transactional
    public Optional<LockChange> changeLocks(String clientNumber, String articleNumber, String packSizeKey,
                                            String reservationCode, LockAction action, List<String> reasons) {
        AsrsStockEntity entity = repo
                .findByClientNumberAndArticleNumberAndPackSize(clientNumber, articleNumber, packSizeKey)
                .orElse(null);
        if (entity == null || !Objects.equals(normalize(entity.getReservationCode()), normalize(reservationCode))) {
            return Optional.empty();
        }
        List<String> current = readLockReasons(entity);
        List<String> result = current == null ? new ArrayList<>() : new ArrayList<>(current);
        List<String> requested = reasons == null ? List.of() : reasons;
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        if (action == LockAction.LOCK) {
            for (String r : requested) {
                if (!result.contains(r)) {
                    result.add(r);
                    added.add(r);
                }
            }
        } else if (requested.isEmpty()) {
            removed.addAll(result);
            result.clear();
        } else {
            for (String r : requested) {
                if (result.remove(r)) {
                    removed.add(r);
                }
            }
        }
        writeLockReasons(entity, result);
        repo.save(entity);
        return Optional.of(new LockChange(entity, result.isEmpty() ? null : List.copyOf(result),
                List.copyOf(added), List.copyOf(removed)));
    }

    private static String normalize(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void writeLockReasons(AsrsStockEntity entity, List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            entity.setStockLockReasonsJson(null);
            return;
        }
        try {
            entity.setStockLockReasonsJson(LOCK_REASONS_MAPPER.writeValueAsString(reasons));
        } catch (Exception e) {
            entity.setStockLockReasonsJson(null);
        }
    }
```

Then simplify the existing `applyAttributes` to reuse the new writer (replace its `if/else` on `stockLockReasons` with one line):

```java
    private static void applyAttributes(AsrsStockEntity entity, AsrsStockAttributes attributes) {
        entity.setStockType(attributes.stockType());
        entity.setLotNumber(attributes.lotNumber());
        entity.setDateMark(attributes.dateMark());
        entity.setSerialNumber(attributes.serialNumber());
        entity.setReservationCode(attributes.reservationCode());
        writeLockReasons(entity, attributes.stockLockReasons());
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd /Users/Dirk/Documents/Cursur-Projects/knappMock && mvn -q test -Dtest=AsrsStockLockTest 2>&1 | tail -20`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
cd /Users/Dirk/Documents/Cursur-Projects/knappMock
git add src/main/java/com/knapp/kisoft/mock/service/StockLockReasons.java \
        src/main/java/com/knapp/kisoft/mock/service/AsrsStockService.java \
        src/test/java/com/knapp/kisoft/mock/service/AsrsStockLockTest.java
git commit -m "feat(stock): add lock reason constants and AsrsStockService.changeLocks"
```

---

### Task 2: Request DTO + `POST /stock/operator/lock` endpoint

**Files:**
- Create: `src/main/java/com/knapp/kisoft/mock/api/dto/StockLockOperatorRequest.java`
- Modify: `src/main/java/com/knapp/kisoft/mock/api/StockOperatorController.java`
- Test: `src/test/java/com/knapp/kisoft/mock/api/StockOperatorLockControllerTest.java`

**Interfaces:**
- Consumes (Task 1): `StockLockReasons.ALL`, `StockLockReasons.invalid(List<String>)`, `AsrsStockService.LockAction`, `AsrsStockService.LockChange`, `AsrsStockService.changeLocks(...)`.
- Consumes (existing): `ReplyCallbackService.deliverSync(String path, Object payload, String messageName)`, `ReplyCallbackService.sendStockLockChanged(StockLockChanged)`, `WebhookWaitResponse.of(...)`, `OneApiOkResponse`, `OneApiErrorResponse`, `StockEntry`, `PackUnitKeyRef`, `StockLockChanged`, `PackSizeKeys.toKey`.
- Produces: HTTP endpoint `POST /oneapi/v1/stock/operator/lock?wait=` with body `StockLockOperatorRequest`.

- [ ] **Step 1: Write the failing MockMvc tests**

```java
package com.knapp.kisoft.mock.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knapp.kisoft.mock.api.dto.StockLockChanged;
import com.knapp.kisoft.mock.persistence.AsrsStockRepository;
import com.knapp.kisoft.mock.service.AsrsStockAttributes;
import com.knapp.kisoft.mock.service.AsrsStockService;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StockOperatorLockControllerTest {

    private static final String CTX = "/kisoft";
    private static final String API = CTX + "/oneapi/v1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AsrsStockService asrsStock;
    @Autowired AsrsStockRepository repo;
    @MockBean ReplyCallbackService callbacks;

    @BeforeEach
    void seed() {
        repo.deleteAll();
        asrsStock.addStock("VPNA-TAC", "VO 25133699", "1", 6,
                new AsrsStockAttributes(null, null, null, null, "PL", null));
    }

    private Map<String, Object> body(String action, List<String> reasons) {
        Map<String, Object> m = new HashMap<>();
        m.put("action", action);
        m.put("clientNumber", "VPNA-TAC");
        m.put("articleNumber", "VO 25133699");
        m.put("packSize", 1);
        m.put("reservationCode", "PL");
        m.put("stockLockReasons", reasons);
        m.put("stationName", "MOCK-STATION");
        return m;
    }

    @Test
    void lock_persistsReasons_andEmitsStockLockChanged() throws Exception {
        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("LOCK", List.of("QS_REQ")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        ArgumentCaptor<StockLockChanged> captor = ArgumentCaptor.forClass(StockLockChanged.class);
        verify(callbacks).sendStockLockChanged(captor.capture());
        StockLockChanged event = captor.getValue();
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.stockLockRequestReference()).isNull();
        assertThat(event.processedQuantity()).isEqualTo(6);
        assertThat(event.addedStockLocks()).containsExactly("QS_REQ");
        assertThat(event.removedStockLocks()).isNull();
        assertThat(event.stationName()).isEqualTo("MOCK-STATION");
        assertThat(event.reason()).isEqualTo("OPERATOR_LOCK");
        assertThat(event.processedStock().packUnit().articleNumber()).isEqualTo("VO 25133699");
        assertThat(event.processedStock().packUnit().packSize()).isEqualTo(1);
        assertThat(event.processedStock().quantity()).isEqualTo(6);
        assertThat(event.processedStock().reservationCode()).isEqualTo("PL");
        assertThat(event.processedStock().stockLockReasons()).containsExactly("QS_REQ");

        mockMvc.perform(get(API + "/inventoryItem").contextPath(CTX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value[0].stockLockReasons[0]").value("QS_REQ"))
                .andExpect(jsonPath("$.value[0].quantity").value(6));
    }

    @Test
    void unlock_removesReasons_emptyListRemovesAll() throws Exception {
        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("LOCK", List.of("HOST", "QS_REQ")))))
                .andExpect(status().isOk());

        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("UNLOCK", List.of()))))
                .andExpect(status().isOk());

        ArgumentCaptor<StockLockChanged> captor = ArgumentCaptor.forClass(StockLockChanged.class);
        verify(callbacks, org.mockito.Mockito.times(2)).sendStockLockChanged(captor.capture());
        StockLockChanged unlock = captor.getAllValues().get(1);
        assertThat(unlock.addedStockLocks()).isNull();
        assertThat(unlock.removedStockLocks()).containsExactly("HOST", "QS_REQ");
        assertThat(unlock.reason()).isEqualTo("OPERATOR_UNLOCK");
        assertThat(unlock.processedStock().stockLockReasons()).isNull();
        assertThat(AsrsStockService.readLockReasons(
                repo.findByClientNumberAndArticleNumberAndPackSize("VPNA-TAC", "VO 25133699", "1").orElseThrow()))
                .isNull();
    }

    @Test
    void invalidReason_returns400_E_AKO_GENR_0002() throws Exception {
        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("LOCK", List.of("BOGUS")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-GENR-0002"))
                .andExpect(jsonPath("$.message").value(containsString("BOGUS")));

        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("LOCK", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-GENR-0002"));
    }

    @Test
    void reservationCodeMismatch_orUnknownArticle_returns404_E_AKO_STOC_0003() throws Exception {
        Map<String, Object> wrongCoo = body("LOCK", List.of("HOST"));
        wrongCoo.put("reservationCode", "SE");
        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongCoo)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-STOC-0003"))
                .andExpect(jsonPath("$.articleNumber").value("VO 25133699"));

        Map<String, Object> unknown = body("LOCK", List.of("HOST"));
        unknown.put("articleNumber", "UNKNOWN");
        mockMvc.perform(post(API + "/stock/operator/lock?wait=false").contextPath(CTX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknown)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codes[0]").value("E-AKO-STOC-0003"));
    }
}
```

Note: if `GET /inventoryItem` in the `test` profile returns the array under a different key than `$.value`, check `StockReportController` (`GetInventoryItems`) and adjust the JSON path — the OData response shape is the existing one, not something this task changes.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/Dirk/Documents/Cursur-Projects/knappMock && mvn -q test -Dtest=StockOperatorLockControllerTest 2>&1 | tail -20`
Expected: all 4 tests fail with HTTP 404/405 on `/stock/operator/lock` (no mapping yet).

- [ ] **Step 3: Create the request DTO**

```java
package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.knapp.kisoft.mock.service.AsrsStockService.LockAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Mock-only request body for an operator stock lock / unlock (IN-05). The ASRS row is matched on
 * clientNumber + articleNumber + packSize and must carry the given reservationCode (Country of Origin).
 */
@Schema(description = "Operator stock lock/unlock. LOCK adds the given reasons; UNLOCK removes them "
        + "(empty list = remove all). Emits PostStockLockChanged.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockLockOperatorRequest(
        @NotNull @Schema(allowableValues = {"LOCK", "UNLOCK"}) LockAction action,
        @NotBlank String clientNumber,
        @NotBlank String articleNumber,
        @NotNull Integer packSize,
        @NotBlank @Schema(description = "Country of Origin; must equal the stored reservationCode") String reservationCode,
        @Schema(description = "Tacoma lock reasons (HIS Appendix §4.2.3)",
                allowableValues = {"DEFAULT", "EXPIRED", "HOST", "LOCATION_LOCKED", "LOCKED_FOR_VISION_CHECK",
                        "LOST", "QS_REQ", "SRS_SYSTEM_BROKEN", "SUBSYSTEM_LOCKED", "TIME_TO_EXPIRE"})
        List<String> stockLockReasons,
        String stationName,
        @Schema(description = "Free-text reason copied to the webhook; defaults to OPERATOR_LOCK / OPERATOR_UNLOCK")
        String reason
) {}
```

- [ ] **Step 4: Add the endpoint to `StockOperatorController`**

Update the class-level `@Tag` and add imports/method. Replace the existing `@Tag(...)` line with:

```java
@Tag(name = "Stock Operator (mock)", description = "Mock-only spontaneous stock correction (PostStockCorrected without prior "
        + "inventory request) and operator stock lock/unlock (PostStockLockChanged).")
```

Add imports:

```java
import com.knapp.kisoft.mock.api.dto.OneApiErrorResponse;
import com.knapp.kisoft.mock.api.dto.StockLockChanged;
import com.knapp.kisoft.mock.api.dto.StockLockOperatorRequest;
import com.knapp.kisoft.mock.persistence.AsrsStockEntity;
import com.knapp.kisoft.mock.service.AsrsStockService.LockAction;
import com.knapp.kisoft.mock.service.AsrsStockService.LockChange;
import com.knapp.kisoft.mock.service.StockLockReasons;
import java.util.List;
```

Add the method after `correct(...)`:

```java
    static final String CODE_STOCK_NOT_FOUND = "E-AKO-STOC-0003";
    static final String CODE_FORMAT_ERROR = "E-AKO-GENR-0002";

    @Operation(operationId = "OperatorStockLockChange",
            summary = "Operator stock lock / unlock (IN-05)",
            description = "Adds (LOCK) or removes (UNLOCK) stock lock reasons on the ASRS stock row matched by "
                    + "clientNumber + articleNumber + packSize + reservationCode (Country of Origin), then emits "
                    + "PostStockLockChanged with addedStockLocks / removedStockLocks and the resulting processedStock. "
                    + "UNLOCK with an empty list removes all locks. Quantity is unchanged. "
                    + "Valid reasons: " + "DEFAULT, EXPIRED, HOST, LOCATION_LOCKED, LOCKED_FOR_VISION_CHECK, LOST, "
                    + "QS_REQ, SRS_SYSTEM_BROKEN, SUBSYSTEM_LOCKED, TIME_TO_EXPIRE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lock change applied; callback field shows APIC result when wait=true"),
            @ApiResponse(responseCode = "400", description = "codes: E-AKO-GENR-0002 (unknown stockLockReasons, or LOCK without reasons)"),
            @ApiResponse(responseCode = "404", description = "codes: E-AKO-STOC-0003 (no ASRS stock for key + reservationCode)")
    })
    @PostMapping("/lock")
    public ResponseEntity<?> lock(
            @RequestBody @Valid StockLockOperatorRequest request,
            @Parameter(description = "Wait for IBM APIC response and include it in the reply (recommended for Swagger)")
            @RequestParam(defaultValue = "true") boolean wait) {
        List<String> reasons = request.stockLockReasons() == null ? List.of() : request.stockLockReasons();
        List<String> invalid = StockLockReasons.invalid(reasons);
        if (!invalid.isEmpty()) {
            return error(400, request, CODE_FORMAT_ERROR,
                    "Unknown stockLockReasons " + invalid + "; allowed: " + StockLockReasons.ALL);
        }
        if (request.action() == LockAction.LOCK && reasons.isEmpty()) {
            return error(400, request, CODE_FORMAT_ERROR, "LOCK requires at least one stockLockReasons entry");
        }
        String packSizeKey = toKey(request.packSize());
        LockChange change = asrsStock.changeLocks(
                request.clientNumber(), request.articleNumber(), packSizeKey,
                request.reservationCode(), request.action(), reasons).orElse(null);
        if (change == null) {
            return error(404, request, CODE_STOCK_NOT_FOUND,
                    "No ASRS stock for " + request.articleNumber() + " / packSize " + request.packSize()
                            + " with reservationCode " + request.reservationCode());
        }

        AsrsStockEntity row = change.entity();
        boolean isLock = request.action() == LockAction.LOCK;
        StockEntry processedStock = new StockEntry(
                null, null,
                new PackUnitKeyRef(row.getClientNumber(), row.getArticleNumber(), request.packSize()),
                row.getQuantity(), row.getStockType(), row.getLotNumber(), row.getDateMark(), row.getSerialNumber(),
                row.getReservationCode(), change.resultingReasons(), null, null, null);
        StockLockChanged event = new StockLockChanged(
                UUID.randomUUID().toString(),
                null,
                row.getQuantity(),
                isLock ? change.added() : null,
                isLock ? null : change.removed(),
                request.stationName(),
                request.reason() != null ? request.reason() : (isLock ? "OPERATOR_LOCK" : "OPERATOR_UNLOCK"),
                null,
                Instant.now().toString(),
                processedStock);
        String message = (isLock ? "Stock lock added " + change.added() : "Stock lock removed " + change.removed())
                + "; current locks " + (change.resultingReasons() == null ? "[]" : change.resultingReasons());
        if (wait) {
            var delivery = callback.deliverSync("stockLockChanged", event, "StockLockChanged").orElse(null);
            return WebhookWaitResponse.of(message, "StockLockChanged", delivery);
        }
        callback.sendStockLockChanged(event);
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", message));
    }

    private static ResponseEntity<OneApiErrorResponse> error(int status, StockLockOperatorRequest r,
                                                             String code, String message) {
        return ResponseEntity.status(status).body(new OneApiErrorResponse(
                r.clientNumber(), null, null, r.articleNumber(),
                r.packSize() == null ? null : String.valueOf(r.packSize()),
                null, message, List.of(code)));
    }
```

Also change the existing `correct(...)` return type is untouched (`ResponseEntity<OneApiOkResponse>`); only the new method uses `ResponseEntity<?>`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd /Users/Dirk/Documents/Cursur-Projects/knappMock && mvn -q test -Dtest=StockOperatorLockControllerTest 2>&1 | tail -20`
Expected: `Tests run: 4, Failures: 0, Errors: 0`. If the `GET /inventoryItem` assertion fails on the JSON path only, inspect the actual response (`.andDo(print())`) and fix the path — do not change `StockReportController`.

- [ ] **Step 6: Run the full suite**

Run: `cd /Users/Dirk/Documents/Cursur-Projects/knappMock && mvn -q test 2>&1 | grep -E "Tests run:.*Fail|BUILD|ERROR\]" | tail -5`
Expected: `Tests run: 86, Failures: 0, Errors: 0` (77 existing + 5 + 4) and no `BUILD FAILURE`.

- [ ] **Step 7: Commit**

```bash
cd /Users/Dirk/Documents/Cursur-Projects/knappMock
git add src/main/java/com/knapp/kisoft/mock/api/dto/StockLockOperatorRequest.java \
        src/main/java/com/knapp/kisoft/mock/api/StockOperatorController.java \
        src/test/java/com/knapp/kisoft/mock/api/StockOperatorLockControllerTest.java
git commit -m "feat(stock): operator POST /stock/operator/lock emitting PostStockLockChanged"
```

---

### Task 3: Documentation (README + OpenAPI tag text)

**Files:**
- Modify: `README.md` (operator table near line 212, quick reference near line 365, IN-05 row near line 487)
- Modify: `openapi/api-docs-v4.0.0-KiSoft-2.12.2-aligned.json` and `KiSoftopenapi.json` (tag `Stock Operator (mock)` description)

**Interfaces:** none (docs only).

- [ ] **Step 1: README operator table** — insert directly after the `/oneapi/v1/stock/operator/correct` row:

```markdown
| POST | `/oneapi/v1/stock/operator/lock` | §8.1.3 (IN-05) | Operator stock lock / unlock. `action: LOCK` adds `stockLockReasons`, `UNLOCK` removes them (empty list = all). Row matched on client + article + packSize + `reservationCode` (Country of Origin). Emits `PostStockLockChanged`; 404 `E-AKO-STOC-0003` when no match, 400 `E-AKO-GENR-0002` on unknown reason. |
```

- [ ] **Step 2: README quick reference** — insert after the `Spontaneous stock correction` row:

```markdown
| Operator stock lock / unlock | `POST /stock/operator/lock` | `true` | `callback` field in HTTP response |
```

- [ ] **Step 3: README IN-05 row** — replace the existing IN-05 line with:

```markdown
| **IN-05** — Stock lock change | `010-asrs-stock-lock` | ◑ | Outbound `PostStockLockChanged` implemented (lock on damaged source during picking; unlock after inventory count; operator `POST /stock/operator/lock` for explicit LOCK/UNLOCK with Tacoma reason codes). The inbound *request to lock* (`PostStockLockRequest`) is out of scope (KIS-010). |
```

- [ ] **Step 4: README curl example** — after the existing `stock/operator/correct` curl block (around line 323), add:

```bash
# Operator stock lock (IN-05) — adds QS_REQ to the PL stock of the article and emits PostStockLockChanged
curl -X POST "$BASE/stock/operator/lock?wait=true" \
  -H "Content-Type: application/json" \
  -d '{"action":"LOCK","clientNumber":"VPNA-TAC","articleNumber":"VO 25133699","packSize":1,
       "reservationCode":"PL","stockLockReasons":["QS_REQ"],"stationName":"MOCK-STATION"}'
```

- [ ] **Step 5: OpenAPI tag description** — in both JSON files, change the `Stock Operator (mock)` tag description to:

`"Mock-only spontaneous stock correction (PostStockCorrected without prior inventory request) and operator stock lock/unlock (PostStockLockChanged)."`

Run to apply to both files:

```bash
cd /Users/Dirk/Documents/Cursur-Projects/knappMock
python3 - <<'EOF'
import json
new = "Mock-only spontaneous stock correction (PostStockCorrected without prior inventory request) and operator stock lock/unlock (PostStockLockChanged)."
for p in ["openapi/api-docs-v4.0.0-KiSoft-2.12.2-aligned.json", "KiSoftopenapi.json"]:
    d = json.load(open(p))
    for t in d.get("tags", []):
        if t["name"] == "Stock Operator (mock)":
            t["description"] = new
    json.dump(d, open(p, "w"), indent=2, ensure_ascii=False)
    open(p, "a").write("\n")
EOF
git diff --stat -- openapi/api-docs-v4.0.0-KiSoft-2.12.2-aligned.json KiSoftopenapi.json
```

Expected: each file shows a small diff (1 line changed). If the diff is large (reformatting), revert with `git checkout -- <file>` and edit the single description string by hand instead.

- [ ] **Step 6: Verify README renders** — `rg -n "stock/operator/lock" README.md` shows 3 hits (table, quick reference, curl) plus the IN-05 row.

- [ ] **Step 7: Commit**

```bash
cd /Users/Dirk/Documents/Cursur-Projects/knappMock
git add README.md openapi/api-docs-v4.0.0-KiSoft-2.12.2-aligned.json KiSoftopenapi.json
git commit -m "docs: document operator stock lock/unlock endpoint"
```

---

## Self-review

- Spec coverage: endpoint + body (T2), reason validation (T1/T2), CoO matching + 404 (T1/T2), LOCK/UNLOCK semantics incl. empty list (T1), persisted JSON visible via `GetInventoryItems` (T2 test), webhook payload fields incl. `reservationCode` and resulting reasons (T2), `wait` sync/async (T2), error bodies (T2), docs (T3), tests (T1/T2). No-op still emits webhook: covered by design (controller always emits after a present `LockChange`).
- Type consistency: `LockAction`, `LockChange(entity, resultingReasons, added, removed)`, `changeLocks(client, article, packSizeKey, reservationCode, action, reasons)`, `StockLockReasons.ALL/invalid` used identically in T1 and T2.
- Not in scope (intentional): version bump to 4.0.7 — do at release time.
