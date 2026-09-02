package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * printDocuments entry of a Goods-Out Order (HIS Appendix §7.4.1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PrintDocument(
        String documentType,
        String documentContent
) {}
