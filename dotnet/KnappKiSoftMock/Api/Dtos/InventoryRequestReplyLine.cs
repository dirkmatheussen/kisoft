namespace KnappKiSoftMock.Api.Dtos;

public record InventoryRequestReplyLine(
    string? lineReference,
    int? processedQuantity
);
