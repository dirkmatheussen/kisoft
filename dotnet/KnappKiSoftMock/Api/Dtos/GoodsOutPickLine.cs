using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record GoodsOutPickLine(
    [property: NotBlank] string? lineReference,
    int? pickedQuantity,
    string? sourceLoadUnitCode,
    int? slot,
    bool? damaged
);
