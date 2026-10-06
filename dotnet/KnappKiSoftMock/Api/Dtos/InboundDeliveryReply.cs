namespace KnappKiSoftMock.Api.Dtos;

public record InboundDeliveryReply(
    string? clientNumber,
    string? inboundDeliveryNumber,
    string? businessCase,
    string? createdBy,
    string? processingStatus,
    string? statusEventTime,
    List<InboundDeliveryLine?>? inboundDeliveryLines
);
