using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record GoodsOutOrder(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? orderNumber,
    [property: NotNull] int? sheetNumber,
    [property: NotBlank] string? loadCarrier,
    int? priority,
    string? businessCase,
    string? startStationName,
    string? loadUnitCode,
    string? departureTime,
    string? departureDate,
    string? customerNumber,
    string? routeNumber,
    List<AreaWeight?>? areaWeights,
    List<int?>? dispatchRampNumbers,
    VasTask[]? vasTasks,
    AdditionalProperty[]? additionalProperties,
    List<string?>? controlFlags,
    List<string?>? transportTargets,
    List<PrintDocument?>? printDocuments,
    [property: Valid] List<GoodsOutOrderLine?>? goodsOutOrderLines
);
