using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record InboundDeliveryRef(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? inboundDeliveryNumber
);
