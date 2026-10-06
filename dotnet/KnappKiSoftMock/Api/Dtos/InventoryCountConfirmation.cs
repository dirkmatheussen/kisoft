using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record InventoryCountConfirmation(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? requestNumber,
    [property: Valid] List<InventoryCountLine?>? lines
);
