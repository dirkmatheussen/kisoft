using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

// Java: addInboundDeliveryLines has no @Valid, so added lines are NOT validated on PATCH.
public record UpdateInboundDelivery(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? inboundDeliveryNumber,
    int? priority,
    VasTask[]? vasTasks,
    AdditionalProperty[]? additionalProperties,
    List<InboundDeliveryLine?>? addInboundDeliveryLines,
    List<string?>? deleteLinesByReference
);
