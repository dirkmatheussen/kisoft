package com.knapp.kisoft.mock.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * storageCapacityDetails entry of a Storage Capacity Report (HIS Appendix §8.3.1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StorageCapacityDetail(
        String storageArea,
        Integer locationCount,
        Integer emptyLocationCount,
        Integer filledLoadUnitCount,
        Integer emptyLoadUnitCount,
        String storageClass,
        String locationClass,
        String rackChannel,
        String aisle,
        String rack
) {}
