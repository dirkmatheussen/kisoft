using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record InboundDelivery(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? inboundDeliveryNumber,
    [property: NotBlank] string? supplierNumber,
    int? priority,
    string? businessCase,
    VasTask[]? vasTasks,
    AdditionalProperty[]? additionalProperties,
    [property: Valid] List<InboundDeliveryLine?>? inboundDeliveryLines
);
