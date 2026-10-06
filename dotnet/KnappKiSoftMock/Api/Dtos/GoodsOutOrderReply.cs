namespace KnappKiSoftMock.Api.Dtos;

public record GoodsOutOrderReply(
    string? clientNumber,
    string? orderNumber,
    int? sheetNumber,
    string? createdBy,
    string? processingStatus,
    string? businessCase,
    string? statusEventTime,
    string? loadUnitCode,
    string? loadCarrier,
    string? customerNumber,
    double? loadUnitGrossWeight,
    List<GoodsOutOrderReplyLine?>? goodsOutOrderLines
);
