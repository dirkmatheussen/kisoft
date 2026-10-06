using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record GoodsOutPickConfirmation(
    [property: NotBlank] string? clientNumber,
    [property: NotBlank] string? orderNumber,
    [property: NotNull] int? sheetNumber,
    [property: Valid] List<GoodsOutPickLine?>? lines
);
