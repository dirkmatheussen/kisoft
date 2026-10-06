using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record InventoryCountLine(
    [property: NotBlank] string? lineReference,
    string? articleNumber,
    int? packSize,
    int? countedQuantity,
    string? loadUnitCode,
    int? slot
);
