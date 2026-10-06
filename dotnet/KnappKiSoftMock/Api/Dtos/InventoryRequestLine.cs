using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record InventoryRequestLine(
    [property: NotBlank] string? lineReference,
    string? articleNumber,
    int? packSize,
    string? stockType,
    string? lotNumber,
    string? dateMark,
    string? reservationCode,
    string? storageArea,
    string? locationNumber,
    string? loadUnitCode,
    string? slot,
    string? noteOnProcessing,
    AdditionalProperty[]? additionalProperties
);
