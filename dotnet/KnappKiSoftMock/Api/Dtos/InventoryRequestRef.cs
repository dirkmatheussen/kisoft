using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record InventoryRequestRef(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? requestNumber
);
