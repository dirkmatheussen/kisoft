namespace KnappKiSoftMock.Api.Dtos;

public record StockEntry(
    string? loadUnitCode,
    int? slot,
    PackUnitKeyRef? packUnit,
    int? quantity,
    string? stockType,
    string? lotNumber,
    string? dateMark,
    string? serialNumber,
    string? reservationCode,
    List<string?>? stockLockReasons,
    string? stockQuality,
    string? receiptDate,
    string? lastInventoryDate
);
