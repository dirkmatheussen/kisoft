using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

// Java: addGoodsOutOrderLines has no @Valid, so added lines are NOT validated on PATCH.
public record UpdateGoodsOutOrder(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? orderNumber,
    [property: NotNull] int? sheetNumber,
    int? priority,
    VasTask[]? vasTasks,
    AdditionalProperty[]? additionalProperties,
    List<GoodsOutOrderLine?>? addGoodsOutOrderLines,
    List<string?>? deleteLinesByReference
);
