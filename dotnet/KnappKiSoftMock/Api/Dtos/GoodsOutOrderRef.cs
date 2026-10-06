using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record GoodsOutOrderRef(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? orderNumber,
    [property: NotNull] int? sheetNumber
);
