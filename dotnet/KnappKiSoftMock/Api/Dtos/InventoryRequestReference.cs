namespace KnappKiSoftMock.Api.Dtos;

public record InventoryRequestReference(
    string? clientNumber,
    string? requestNumber,
    string? lineReference
);
