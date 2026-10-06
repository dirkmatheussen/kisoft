using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record LoadUnitRetrievalRequest(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? loadUnitCode,
    string? loadCarrier,
    string? stationName,
    string? locationNumber,
    string? articleNumber,
    int? packSize,
    int? quantity,
    int? slot,
    string? stockType,
    bool? toConventional,
    string? reservationCode
);
