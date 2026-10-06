namespace KnappKiSoftMock.Api.Dtos;

public record GoodsOutOrderRead(
    string? processingStatus,
    GoodsOutOrder? goodsOutOrder
);
