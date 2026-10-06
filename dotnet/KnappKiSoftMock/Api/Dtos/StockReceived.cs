namespace KnappKiSoftMock.Api.Dtos;

public record StockReceived(
    string? eventId,
    InboundDeliveryReference? inboundDeliveryReference,
    int? processedQuantity,
    string? stationName,
    string? reason,
    string? userCode,
    string? eventTime,
    StockEntry? processedStock
);
