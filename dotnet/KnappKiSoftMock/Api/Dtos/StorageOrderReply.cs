namespace KnappKiSoftMock.Api.Dtos;

public record StorageOrderReply(
    string? loadUnitCode,
    string? clientNumber,
    string? inboundDeliveryNumber,
    string? processingStatus,
    string? statusEventTime
);
