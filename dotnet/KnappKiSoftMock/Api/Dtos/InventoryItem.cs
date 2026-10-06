namespace KnappKiSoftMock.Api.Dtos;

public record InventoryItem(
    PackUnitKeyRef? packUnit,
    int? quantity,
    string? stockType,
    string? lotNumber,
    string? dateMark,
    string? serialNumber,
    string? reservationCode,
    List<string?>? stockLockReasons
);
