package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * OrderKey - Reference to order by client and order number (e.g. for DELETE storage order).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderKey(
        @NotBlank String clientNumber,
        @NotBlank String orderNumber
) {}
