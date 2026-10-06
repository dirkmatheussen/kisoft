using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record GoodsOutOrderLine(
    [property: NotBlank] string? lineReference,
    [property: NotNull] int? requestedQuantity,
    [property: NotBlank] string? articleNumber,
    int? packSize,
    string? stockType,
    string? lotNumber,
    string? dateMark,
    [property: NotBlank] string? reservationCode,
    string? stationName,
    string? locationNumber,
    string? noteOnProcessing,
    AdditionalProperty[]? additionalProperties,
    List<PrintDocument?>? printDocuments,
    string? selectionStrategy
);
