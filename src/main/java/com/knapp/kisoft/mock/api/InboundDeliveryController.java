package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.config.OpenApiConfig;
import com.knapp.kisoft.mock.api.dto.InboundDelivery;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryLine;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryRead;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryRef;
import com.knapp.kisoft.mock.api.dto.InboundDeliveryReply;
import com.knapp.kisoft.mock.api.dto.ODataCollectionResponse;
import com.knapp.kisoft.mock.api.dto.OneApiErrorResponse;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.api.dto.UpdateInboundDelivery;
import com.knapp.kisoft.mock.persistence.InboundDeliveryEntity;
import com.knapp.kisoft.mock.service.InboundDeliveryLifecycleService;
import com.knapp.kisoft.mock.service.InboundDeliveryStoreService;
import com.knapp.kisoft.mock.service.JsonPayloadMapper;
import com.knapp.kisoft.mock.service.ODataQuerySupport;
import com.knapp.kisoft.mock.service.PackUnitStoreService;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mock KiSoft InboundDelivery API - Goods-In (HIS Appendix §7.1).
 * POST /inboundDelivery, PATCH /inboundDelivery, DELETE /inboundDelivery, GET /inboundDelivery (mock OData read).
 */
@Tag(name = "Goods-In", description = "Inbound delivery (POST, PATCH, DELETE, GET mock read)")
@RestController
@RequestMapping("/oneapi/v1")
public class InboundDeliveryController {

    private final InboundDeliveryStoreService inboundStore;
    private final InboundDeliveryLifecycleService lifecycle;
    private final PackUnitStoreService packUnitStore;
    private final JsonPayloadMapper json;
    private final ReplyCallbackService replyCallbackService;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public InboundDeliveryController(InboundDeliveryStoreService inboundStore,
                                     InboundDeliveryLifecycleService lifecycle,
                                     PackUnitStoreService packUnitStore,
                                     JsonPayloadMapper json,
                                     ReplyCallbackService replyCallbackService) {
        this.inboundStore = inboundStore;
        this.lifecycle = lifecycle;
        this.packUnitStore = packUnitStore;
        this.json = json;
        this.replyCallbackService = replyCallbackService;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OData v4 collection (mock-only; not KiSoft API)")
    })
    @Operation(
            operationId = "GetInboundDeliveries",
            summary = "[MOCK ONLY — not KiSoft API] List inbound deliveries (OData read)",
            description = "**Not part of the KiSoft One Product API** (HIS Appendix §2.3.1: KiSoft exposes no GET requests). "
                    + "Mock-only inspection endpoint. Supports `$filter`, `$top`, `$skip`, `$count`. "
                    + "Filter fields: clientNumber, inboundDeliveryNumber, processingStatus, supplierNumber.",
            tags = { OpenApiConfig.MOCK_ODATA_READ_TAG })
    @GetMapping("/inboundDelivery")
    public ResponseEntity<ODataCollectionResponse<InboundDeliveryRead>> getInboundDeliveries(
            @RequestParam(value = "$filter", required = false) String filter,
            @RequestParam(value = "$top", required = false) String top,
            @RequestParam(value = "$skip", required = false) String skip,
            @Parameter(description = "Include @odata.count when true")
            @RequestParam(value = "$count", required = false) String count) {
        List<InboundDeliveryRead> all = inboundStore.listAllEntities().stream()
                .map(this::toRead)
                .toList();
        ODataCollectionResponse<InboundDeliveryRead> response = ODataQuerySupport.buildPage(
                ODataQuerySupport.metadataContext(contextPath, "InboundDeliveries"),
                all,
                ODataQuerySupport.parseFilter(filter),
                read -> {
                    InboundDelivery d = read.inboundDelivery();
                    return ODataQuerySupport.fields(
                            "clientNumber", ODataQuerySupport.str(d.clientNumber()),
                            "inboundDeliveryNumber", ODataQuerySupport.str(d.inboundDeliveryNumber()),
                            "processingStatus", ODataQuerySupport.str(read.processingStatus()),
                            "supplierNumber", ODataQuerySupport.str(d.supplierNumber()));
                },
                ODataQuerySupport.parseTop(top),
                ODataQuerySupport.parseSkip(skip),
                ODataQuerySupport.parseCount(count));
        return ResponseEntity.ok(response);
    }

    private InboundDeliveryRead toRead(InboundDeliveryEntity entity) {
        return new InboundDeliveryRead(entity.getProcessingStatus(), inboundStore.readPayload(entity));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inbound delivery created successfully"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: " +
                    "E-AKO-MOVM-0002 (Order already active), " +
                    "E-AKO-MAST-0001 (Unknown article), " +
                    "E-AKO-MAST-0007 (Duplicate article in inbound delivery - one part per inbound delivery, IB-02), " +
                    "E-AKO-GENR-0001, E-AKO-GENR-0002")
    })
    @Operation(operationId = "PostInboundDelivery")
    @PostMapping("/inboundDelivery")
    public ResponseEntity<?> postInboundDelivery(
            @RequestBody @Valid InboundDelivery request) {

        if (inboundStore.exists(request.clientNumber(), request.inboundDeliveryNumber())) {
            return ResponseEntity.status(400).body(new OneApiErrorResponse(
                    request.clientNumber(),
                    request.inboundDeliveryNumber(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of("E-AKO-MOVM-0002")
            ));
        }
        if (request.inboundDeliveryLines() != null) {
            // IB-02 rule: KiSoft does not accept two lines with the same part in
            // one inbound delivery. The MA-01 / IB-02 spec also models one PartID
            // per inbound delivery POST.
            Set<String> seenArticles = new HashSet<>();
            for (InboundDeliveryLine line : request.inboundDeliveryLines()) {
                if (!seenArticles.add(line.articleNumber())) {
                    return ResponseEntity.status(400).body(new OneApiErrorResponse(
                            request.clientNumber(),
                            request.inboundDeliveryNumber(),
                            null,
                            line.articleNumber(),
                            line.packSize() != null ? String.valueOf(line.packSize()) : null,
                            null,
                            "Duplicate article in inbound delivery: " + line.articleNumber(),
                            List.of("E-AKO-MAST-0007")
                    ));
                }
                if (!packUnitStore.exists(request.clientNumber(), line.articleNumber(), line.packSize())) {
                    return ResponseEntity.status(400).body(new OneApiErrorResponse(
                            request.clientNumber(),
                            request.inboundDeliveryNumber(),
                            null,
                            line.articleNumber(),
                            line.packSize() != null ? String.valueOf(line.packSize()) : null,
                            null,
                            "Unknown article/packSize: " + line.articleNumber() + "/" + line.packSize(),
                            List.of("E-AKO-MAST-0001")
                    ));
                }
            }
        }
        inboundStore.createNew(request);
        // Book expectedQuantity into ASRS (articleNumber + packSize, same keys as inventoryRequestLine / goods-out).
        lifecycle.bookExpectedStock(request);

        InboundDeliveryReply reply = new InboundDeliveryReply(
                request.clientNumber(),
                request.inboundDeliveryNumber(),
                request.businessCase() != null ? request.businessCase() : "GOODS_IN",
                "HOST",
                "NEW",
                Instant.now().toString(),
                request.inboundDeliveryLines()
        );
        replyCallbackService.sendInboundDeliveryReply(reply);

        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Inbound delivery created successfully"));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inbound delivery updated"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MOVM-0003 (Order not found)"),
            @ApiResponse(responseCode = "409", description = "Conflict - inbound delivery already active. " +
                    "lineCode: E-AKO-MOVM-0005 (Wrong order process status). IB-02 EF-K409: PatchInboundDelivery " +
                    "is not allowed once processing has started.")
    })
    @Operation(operationId = "PatchInboundDelivery")
    @PatchMapping("/inboundDelivery")
    public ResponseEntity<?> patchInboundDelivery(@RequestBody @Valid UpdateInboundDelivery request) {
        var existing = inboundStore.find(request.clientNumber(), request.inboundDeliveryNumber()).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(400).body(new OneApiErrorResponse(
                    request.clientNumber(),
                    request.inboundDeliveryNumber(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of("E-AKO-MOVM-0003")
            ));
        }
        String status = existing.getProcessingStatus();
        // IB-02 EF-K409: once the inbound delivery is active in KiSoft (anything
        // other than NEW) Patch is not allowed and KiSoft must respond with 409.
        if (!"NEW".equals(status)) {
            return ResponseEntity.status(409).body(new OneApiErrorResponse(
                    request.clientNumber(),
                    request.inboundDeliveryNumber(),
                    null,
                    null,
                    null,
                    null,
                    "Inbound delivery is active (status=" + status + "); patch not allowed",
                    List.of("E-AKO-MOVM-0005")
            ));
        }
        InboundDelivery current = json.fromJson(existing.getPayloadJson(), InboundDelivery.class);
        Integer priority = request.priority() != null ? request.priority() : (current.priority() != null ? current.priority() : 1);
        List<InboundDeliveryLine> lines = new ArrayList<>(current.inboundDeliveryLines() != null ? current.inboundDeliveryLines() : List.of());
        if (request.addInboundDeliveryLines() != null) {
            lines.addAll(request.addInboundDeliveryLines());
        }
        if (request.deleteLinesByReference() != null) {
            lines.removeIf(l -> request.deleteLinesByReference().contains(l.lineReference()));
        }
        InboundDelivery updated = new InboundDelivery(
                current.clientNumber(),
                current.inboundDeliveryNumber(),
                current.supplierNumber(),
                priority,
                current.businessCase(),
                request.vasTasks() != null ? request.vasTasks() : current.vasTasks(),
                request.additionalProperties() != null ? request.additionalProperties() : current.additionalProperties(),
                lines.isEmpty() ? null : lines
        );
        inboundStore.update(existing, updated);
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Inbound delivery updated"));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inbound delivery cancelled and deleted"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MOVM-0003 (Order not found)"),
            @ApiResponse(responseCode = "409", description = "Conflict - inbound delivery already active. "
                    + "lineCode: E-AKO-MOVM-0005 (Wrong order process status). IB-02 EF-K409.")
    })
    @Operation(operationId = "DeleteInboundDelivery")
    @DeleteMapping("/inboundDelivery")
    public ResponseEntity<?> deleteInboundDelivery(@RequestBody @Valid InboundDeliveryRef request) {
        var existing = inboundStore.find(request.clientNumber(), request.inboundDeliveryNumber()).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(400).body(new OneApiErrorResponse(
                    request.clientNumber(),
                    request.inboundDeliveryNumber(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of("E-AKO-MOVM-0003")
            ));
        }
        String status = existing.getProcessingStatus();
        if (!"NEW".equals(status)) {
            return ResponseEntity.status(409).body(new OneApiErrorResponse(
                    request.clientNumber(),
                    request.inboundDeliveryNumber(),
                    null,
                    null,
                    null,
                    null,
                    "Inbound delivery is active (status=" + status + "); delete not allowed",
                    List.of("E-AKO-MOVM-0005")
            ));
        }
        InboundDelivery delivery = json.fromJson(existing.getPayloadJson(), InboundDelivery.class);
        lifecycle.releaseExpectedStock(delivery);
        inboundStore.delete(request.clientNumber(), request.inboundDeliveryNumber());

        InboundDeliveryReply reply = new InboundDeliveryReply(
                delivery.clientNumber(),
                delivery.inboundDeliveryNumber(),
                delivery.businessCase() != null ? delivery.businessCase() : "GOODS_IN",
                "KISOFT",
                "CANCELLED",
                Instant.now().toString(),
                delivery.inboundDeliveryLines()
        );
        replyCallbackService.sendInboundDeliveryReply(reply);
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Inbound delivery cancelled"));
    }
}
