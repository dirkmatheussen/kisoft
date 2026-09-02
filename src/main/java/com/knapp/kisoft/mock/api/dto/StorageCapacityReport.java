package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Storage Capacity Report (KiSoft One to HOST) — operationId
 * {@code PostStorageCapacityReport}, HIS Appendix §8.3.1. POSTed by the mock to
 * {reply-callback-url}/storageCapacityReport in response to a PostRequestStorageCapacityReport.
 */
@Schema(description = "Payload POSTed by the mock to the callback URL in response to a Request Storage Capacity Report (PostStorageCapacityReport).")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StorageCapacityReport(
        String requestNumber,
        String eventTime,
        List<StorageCapacityDetail> storageCapacityDetails
) {}
