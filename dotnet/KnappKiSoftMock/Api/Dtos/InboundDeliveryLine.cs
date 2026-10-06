using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record InboundDeliveryLine(
    [property: NotBlank] string? lineReference,
    [property: NotBlank] string? articleNumber,
    [property: NotNull] int? packSize,
    [property: NotNull] int? expectedQuantity,
    string? loadUnitCode,
    string? loadCarrier,
    string? stockType,
    string? lotNumber,
    string? dateMark,
    string? serialNumber,
    string? reservationCode,
    List<string?>? stockLockReasons,
    string? noteOnProcessing,
    string? stockQuality,
    AdditionalProperty[]? additionalProperties
);
