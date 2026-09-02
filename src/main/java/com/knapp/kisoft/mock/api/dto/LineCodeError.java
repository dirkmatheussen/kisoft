package com.knapp.kisoft.mock.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error code applicable to a specific order line (Product One API LineCode)")
public record LineCodeError(
        String lineReference,
        String lineCode
) {}
