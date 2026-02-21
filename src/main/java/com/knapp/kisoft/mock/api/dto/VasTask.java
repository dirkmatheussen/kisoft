package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Value-added service task for inbound/outbound processing.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VasTask(
        String taskReference,
        String workZone,
        String vasProcessingNote,
        String lineReference
) {}
