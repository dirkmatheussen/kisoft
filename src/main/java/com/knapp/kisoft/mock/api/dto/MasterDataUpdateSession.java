package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * MsgMasterDataUpdateSession - Open or close update session for masterdata.
 * transmissionTag: SET = open session, CLEANUP = close and delete non-transmitted data.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MasterDataUpdateSession(
        String clientNumber,
        @NotBlank @Pattern(regexp = "SET|CLEANUP") String transmissionTag
) {}
