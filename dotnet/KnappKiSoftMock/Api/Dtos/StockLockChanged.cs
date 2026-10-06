namespace KnappKiSoftMock.Api.Dtos;

public record StockLockChanged(
    string? eventId,
    StockLockRequestReference? stockLockRequestReference,
    int? processedQuantity,
    List<string?>? addedStockLocks,
    List<string?>? removedStockLocks,
    string? stationName,
    string? reason,
    string? userCode,
    string? eventTime,
    StockEntry? processedStock
);
