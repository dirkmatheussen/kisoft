namespace KnappKiSoftMock.Api.Dtos;

public record PickedStock(
    int? processedQuantity,
    string? articleNumber,
    int? packSize,
    string? stockType,
    string? lotNumber,
    string? dateMark,
    string? reservationCode,
    string? serialNumber,
    List<string?>? stockLockReasons
);
