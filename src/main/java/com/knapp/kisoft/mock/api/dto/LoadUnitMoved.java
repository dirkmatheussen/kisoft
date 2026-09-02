package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Load Unit Moved (KiSoft One to HOST) — operationId {@code PostLoadUnitMoved},
 * HIS Appendix §7.3.1. POSTed by the mock to {reply-callback-url}/loadUnitMoved when a
 * load unit is relocated (relocation use case is in scope).
 */
@Schema(description = "Payload POSTed by the mock to the callback URL when a load unit is moved/relocated (PostLoadUnitMoved).")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoadUnitMoved(
        String eventId,
        String loadUnitCode,
        String loadCarrier,
        String statusEventTime,
        String businessCase,
        NewPosition newPosition,
        String contentType,
        List<String> controlReasons,
        LoadUnitProperties loadUnitProperties,
        List<StockEntry> loadUnitStock
) {}
