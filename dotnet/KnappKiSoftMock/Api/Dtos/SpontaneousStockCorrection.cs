using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record SpontaneousStockCorrection(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? articleNumber,
    [property: NotNull] int? packSize,
    string? reservationCode,
    [property: NotNull, Min(0)] int? countedQuantity,
    string? reason,
    string? stationName
);
