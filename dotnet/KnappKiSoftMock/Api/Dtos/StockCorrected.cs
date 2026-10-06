namespace KnappKiSoftMock.Api.Dtos;

public record StockCorrected(
    string? eventId,
    InventoryRequestReference? inventoryRequestReference,
    GoodsOutOrderReference? goodsOutOrderReference,
    int? deltaQuantity,
    string? stationName,
    string? reason,
    string? extendedReason,
    string? userCode,
    string? eventTime,
    StockEntry? processedStock
);
