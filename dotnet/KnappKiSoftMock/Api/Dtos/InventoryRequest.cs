using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record InventoryRequest(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? requestNumber,
    [property: NotNull, Valid] InventoryRequestLine? inventoryRequestLine,
    int? priority,
    string? businessCase,
    AdditionalProperty[]? additionalProperties
);
