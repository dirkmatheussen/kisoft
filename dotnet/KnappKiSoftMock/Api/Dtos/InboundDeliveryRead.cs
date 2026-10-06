namespace KnappKiSoftMock.Api.Dtos;

public record InboundDeliveryRead(
    string? processingStatus,
    InboundDelivery? inboundDelivery
);
