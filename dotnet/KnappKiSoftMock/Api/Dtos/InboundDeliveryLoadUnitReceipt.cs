using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record InboundDeliveryLoadUnitReceipt(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? inboundDeliveryNumber,
    [property: NotBlank] string? lineReference,
    [property: NotBlank] string? loadUnitCode,
    [property: NotBlank] string? compartment,
    [property: NotNull, Min(1)] int? quantity,
    string? lotNumber,
    string? dateMark,
    string? serialNumber,
    string? stockType,
    string? stockQuality,
    string? reservationCode
);
