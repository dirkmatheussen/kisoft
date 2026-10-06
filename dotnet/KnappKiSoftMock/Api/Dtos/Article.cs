namespace KnappKiSoftMock.Api.Dtos;

public record Article(
    string? clientNumber,
    string? articleNumber,
    string? articleName,
    bool? isLotRequired,
    bool? isDateMarkRequired,
    bool? isSerialNumberRequired,
    string? sizeRange,
    int? sizePosition,
    string? sizeName,
    string? colorName,
    string? colorNumber,
    double? salesPrice,
    string? currencyUnit,
    AdditionalProperty[]? additionalProperties
);
