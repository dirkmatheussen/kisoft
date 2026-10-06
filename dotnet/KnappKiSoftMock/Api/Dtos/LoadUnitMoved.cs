namespace KnappKiSoftMock.Api.Dtos;

public record LoadUnitMoved(
    string? eventId,
    string? loadUnitCode,
    string? loadCarrier,
    string? statusEventTime,
    string? businessCase,
    NewPosition? newPosition,
    string? contentType,
    List<string?>? controlReasons,
    LoadUnitProperties? loadUnitProperties,
    List<StockEntry?>? loadUnitStock
);
