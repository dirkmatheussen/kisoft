namespace KnappKiSoftMock.Api.Dtos;

public record InboundDeliveryReference(
    string? clientNumber,
    string? inboundDeliveryNumber,
    string? lineReference
);
