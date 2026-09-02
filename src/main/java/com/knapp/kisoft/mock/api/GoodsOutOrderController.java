package com.knapp.kisoft.mock.api;

import com.knapp.kisoft.mock.config.OpenApiConfig;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrder;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderLine;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderRead;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderRef;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderReply;
import com.knapp.kisoft.mock.api.dto.GoodsOutOrderLineErrorResponse;
import com.knapp.kisoft.mock.api.dto.LineCodeError;
import com.knapp.kisoft.mock.api.dto.ODataCollectionResponse;
import com.knapp.kisoft.mock.api.dto.OneApiOkResponse;
import com.knapp.kisoft.mock.api.dto.UpdateGoodsOutOrder;
import com.knapp.kisoft.mock.persistence.GoodsOutOrderEntity;
import com.knapp.kisoft.mock.service.GoodsOutOrderLifecycleService;
import com.knapp.kisoft.mock.service.GoodsOutOrderStoreService;
import com.knapp.kisoft.mock.service.ODataQuerySupport;
import com.knapp.kisoft.mock.service.ReplyCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mock KiSoft Goods-Out Order API — Goods-Out (HIS Appendix §7.4, GS §5.2.1).
 * POST /goodsOutOrder ({@code PostGoodsOutOrder}), PATCH /goodsOutOrder ({@code PatchGoodsOutOrder}),
 * DELETE /goodsOutOrder ({@code DeleteGoodsOutOrder}).
 *
 * <p>On create, invalid lines are rejected synchronously with HTTP 400 and Product One API
 * {@code lineCode}s ({@code E-AKO-MAST-0001}, {@code E-AKO-STOC-0001}, {@code E-AKO-GENR-0002}, …).
 * Accepted orders return HTTP 200; PostGoodsOutOrderReply(NEW) reports all lines as {@code UNTOUCHED}.</p>
 */
@Tag(name = "Goods-Out", description = "Goods-out order (POST, PATCH, DELETE, GET mock read). Status is driven STARTED→PROCESSED→FINISHED via the Goods-Out Operator (mock) endpoints; PostGoodsOutOrderReply is sent on each change.")
@RestController
@RequestMapping("/oneapi/v1")
public class GoodsOutOrderController {

    private final GoodsOutOrderStoreService store;
    private final GoodsOutOrderLifecycleService lifecycle;
    private final ReplyCallbackService replyCallbackService;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public GoodsOutOrderController(GoodsOutOrderStoreService store,
                                   GoodsOutOrderLifecycleService lifecycle,
                                   ReplyCallbackService replyCallbackService) {
        this.store = store;
        this.lifecycle = lifecycle;
        this.replyCallbackService = replyCallbackService;
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OData v4 collection (mock-only; not KiSoft API)")
    })
    @Operation(
            operationId = "GetGoodsOutOrders",
            summary = "[MOCK ONLY — not KiSoft API] List goods-out orders (OData read)",
            description = "**Not part of the KiSoft One Product API** (HIS Appendix §2.3.1: KiSoft exposes no GET requests). "
                    + "Mock-only inspection endpoint. Supports `$filter`, `$top`, `$skip`, `$count`. "
                    + "Filter fields: clientNumber, orderNumber, sheetNumber, processingStatus.",
            tags = { OpenApiConfig.MOCK_ODATA_READ_TAG })
    @GetMapping("/goodsOutOrder")
    public ResponseEntity<ODataCollectionResponse<GoodsOutOrderRead>> getGoodsOutOrders(
            @RequestParam(value = "$filter", required = false) String filter,
            @RequestParam(value = "$top", required = false) String top,
            @RequestParam(value = "$skip", required = false) String skip,
            @Parameter(description = "Include @odata.count when true")
            @RequestParam(value = "$count", required = false) String count) {
        List<GoodsOutOrderRead> all = store.listAllEntities().stream()
                .map(this::toRead)
                .toList();
        ODataCollectionResponse<GoodsOutOrderRead> response = ODataQuerySupport.buildPage(
                ODataQuerySupport.metadataContext(contextPath, "GoodsOutOrders"),
                all,
                ODataQuerySupport.parseFilter(filter),
                read -> {
                    GoodsOutOrder o = read.goodsOutOrder();
                    return ODataQuerySupport.fields(
                            "clientNumber", ODataQuerySupport.str(o.clientNumber()),
                            "orderNumber", ODataQuerySupport.str(o.orderNumber()),
                            "sheetNumber", ODataQuerySupport.str(o.sheetNumber()),
                            "processingStatus", ODataQuerySupport.str(read.processingStatus()));
                },
                ODataQuerySupport.parseTop(top),
                ODataQuerySupport.parseSkip(skip),
                ODataQuerySupport.parseCount(count));
        return ResponseEntity.ok(response);
    }

    private GoodsOutOrderRead toRead(GoodsOutOrderEntity entity) {
        return new GoodsOutOrderRead(entity.getProcessingStatus(), store.readPayload(entity));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goods-out order accepted. PostGoodsOutOrderReply(NEW) "
                    + "reports all lines as UNTOUCHED."),
            @ApiResponse(responseCode = "400", description = "Order rejected. lineCode: E-AKO-MAST-0001 (Unknown article), "
                    + "E-AKO-STOC-0001 (Not enough stock), E-AKO-GENR-0002 (Format error), E-AKO-GENR-0001 (General error), "
                    + "E-AKO-MOVM-0002 (Order already active)")
    })
    @Operation(operationId = "PostGoodsOutOrder")
    @PostMapping("/goodsOutOrder")
    public ResponseEntity<?> postGoodsOutOrder(@RequestBody @Valid GoodsOutOrder request) {
        if (store.exists(request.clientNumber(), request.orderNumber(), request.sheetNumber())) {
            return ResponseEntity.status(400).body(orderKeyError(request, List.of("E-AKO-MOVM-0002")));
        }
        List<LineCodeError> lineErrors = lifecycle.validateIntakeLines(
                request.clientNumber(), request.goodsOutOrderLines());
        if (!lineErrors.isEmpty()) {
            return ResponseEntity.status(400).body(lineError(request, lineErrors));
        }
        store.createNew(request);
        // GS §5.2.1: affected lines are reported back via PostGoodsOutOrderReply with a processing result.
        replyCallbackService.sendGoodsOutOrderReply(lifecycle.buildIntakeReply(request, "HOST"));
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Goods-out order accepted"));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goods-out order updated"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MAST-0001 (Unknown article), "
                    + "E-AKO-STOC-0001 (Not enough stock), E-AKO-GENR-0002 (Format error), E-AKO-GENR-0001 (General error), "
                    + "E-AKO-MOVM-0003 (Order not found)"),
            @ApiResponse(responseCode = "409", description = "Conflict - order already active. lineCode: E-AKO-MOVM-0005 "
                    + "(Wrong order process status). Patch is only allowed while the order is NEW.")
    })
    @Operation(operationId = "PatchGoodsOutOrder")
    @PatchMapping("/goodsOutOrder")
    public ResponseEntity<?> patchGoodsOutOrder(@RequestBody @Valid UpdateGoodsOutOrder request) {
        GoodsOutOrderEntity existing = store.find(request.clientNumber(), request.orderNumber(), request.sheetNumber()).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(400).body(orderKeyError(request, List.of("E-AKO-MOVM-0003")));
        }
        if (!"NEW".equals(existing.getProcessingStatus())) {
            return ResponseEntity.status(409).body(orderKeyError(request, List.of("E-AKO-MOVM-0005")));
        }
        if (request.addGoodsOutOrderLines() != null) {
            List<LineCodeError> lineErrors = lifecycle.validateIntakeLines(
                    request.clientNumber(), request.addGoodsOutOrderLines());
            if (!lineErrors.isEmpty()) {
                return ResponseEntity.status(400).body(lineError(
                        request.clientNumber(), request.orderNumber(), request.sheetNumber(), lineErrors));
            }
        }
        GoodsOutOrder current = store.readPayload(existing);
        Integer priority = request.priority() != null ? request.priority()
                : (current.priority() != null ? current.priority() : 1);
        List<GoodsOutOrderLine> lines = new ArrayList<>(current.goodsOutOrderLines() != null ? current.goodsOutOrderLines() : List.of());
        if (request.addGoodsOutOrderLines() != null) {
            lines.addAll(request.addGoodsOutOrderLines());
        }
        if (request.deleteLinesByReference() != null) {
            lines.removeIf(l -> request.deleteLinesByReference().contains(l.lineReference()));
        }
        GoodsOutOrder updated = new GoodsOutOrder(
                current.clientNumber(), current.orderNumber(), current.sheetNumber(), current.loadCarrier(),
                priority, current.businessCase(), current.startStationName(), current.loadUnitCode(),
                current.departureTime(), current.departureDate(), current.customerNumber(), current.routeNumber(),
                current.areaWeights(), current.dispatchRampNumbers(),
                request.vasTasks() != null ? request.vasTasks() : current.vasTasks(),
                request.additionalProperties() != null ? request.additionalProperties() : current.additionalProperties(),
                current.controlFlags(), current.transportTargets(), current.printDocuments(),
                lines.isEmpty() ? null : lines
        );
        store.update(existing, updated);
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Goods-out order updated"));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goods-out order cancelled and deleted"),
            @ApiResponse(responseCode = "400", description = "Errors detected. lineCode: E-AKO-MOVM-0003 (Order not found)"),
            @ApiResponse(responseCode = "409", description = "Conflict - order already active. lineCode: E-AKO-MOVM-0005 "
                    + "(Wrong order process status). Cancel is only allowed while the order is NEW.")
    })
    @Operation(operationId = "DeleteGoodsOutOrder")
    @DeleteMapping("/goodsOutOrder")
    public ResponseEntity<?> deleteGoodsOutOrder(@RequestBody @Valid GoodsOutOrderRef request) {
        GoodsOutOrderEntity existing = store.find(request.clientNumber(), request.orderNumber(), request.sheetNumber()).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(400).body(orderKeyError(request, List.of("E-AKO-MOVM-0003")));
        }
        if (!"NEW".equals(existing.getProcessingStatus())) {
            return ResponseEntity.status(409).body(orderKeyError(request, List.of("E-AKO-MOVM-0005")));
        }
        GoodsOutOrder order = store.readPayload(existing);
        store.delete(request.clientNumber(), request.orderNumber(), request.sheetNumber());

        GoodsOutOrderReply reply = new GoodsOutOrderReply(
                order.clientNumber(), order.orderNumber(), order.sheetNumber(), "KISOFT", "CANCELLED",
                order.businessCase() != null ? order.businessCase() : "GOODS_OUT",
                Instant.now().toString(), order.loadUnitCode(), order.loadCarrier(),
                order.customerNumber(), null, null);
        replyCallbackService.sendGoodsOutOrderReply(reply);
        return ResponseEntity.ok(new OneApiOkResponse(200, "OK", "Goods-out order cancelled"));
    }

    private static GoodsOutOrderLineErrorResponse lineError(GoodsOutOrder request, List<LineCodeError> lineCodes) {
        return lineError(request.clientNumber(), request.orderNumber(), request.sheetNumber(), lineCodes);
    }

    private static GoodsOutOrderLineErrorResponse lineError(
            String clientNumber, String orderNumber, Integer sheetNumber, List<LineCodeError> lineCodes) {
        Set<String> codes = new LinkedHashSet<>();
        for (LineCodeError lineCode : lineCodes) {
            codes.add(lineCode.lineCode());
        }
        return new GoodsOutOrderLineErrorResponse(
                clientNumber, orderNumber, sheetNumber, List.copyOf(codes), lineCodes);
    }

    private static GoodsOutOrderLineErrorResponse orderKeyError(GoodsOutOrder request, List<String> codes) {
        return new GoodsOutOrderLineErrorResponse(
                request.clientNumber(), request.orderNumber(), request.sheetNumber(), codes, null);
    }

    private static GoodsOutOrderLineErrorResponse orderKeyError(GoodsOutOrderRef request, List<String> codes) {
        return new GoodsOutOrderLineErrorResponse(
                request.clientNumber(), request.orderNumber(), request.sheetNumber(), codes, null);
    }

    private static GoodsOutOrderLineErrorResponse orderKeyError(UpdateGoodsOutOrder request, List<String> codes) {
        return new GoodsOutOrderLineErrorResponse(
                request.clientNumber(), request.orderNumber(), request.sheetNumber(), codes, null);
    }
}
