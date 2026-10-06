namespace KnappKiSoftMock.Api.Dtos;

public record InventoryRequestReply(
    string? clientNumber,
    string? requestNumber,
    List<InventoryRequestReplyLine?>? inventoryRequestLine,
    string? createdBy,
    string? processingStatus,
    string? statusEventTime,
    string? businessCase,
    AdditionalProperty[]? additionalProperties
);
