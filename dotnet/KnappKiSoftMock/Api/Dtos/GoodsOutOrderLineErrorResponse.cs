namespace KnappKiSoftMock.Api.Dtos;

public record GoodsOutOrderLineErrorResponse(
    string? clientNumber,
    string? orderNumber,
    int? sheetNumber,
    List<string?>? codes,
    List<LineCodeError?>? lineCodes
);
