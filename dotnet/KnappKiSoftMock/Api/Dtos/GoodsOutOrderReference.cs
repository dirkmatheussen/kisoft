namespace KnappKiSoftMock.Api.Dtos;

public record GoodsOutOrderReference(
    string? clientNumber,
    string? orderNumber,
    int? sheetNumber,
    string? lineReference
);
