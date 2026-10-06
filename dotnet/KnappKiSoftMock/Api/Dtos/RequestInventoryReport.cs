using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record RequestInventoryReport(
    [property: NotBlank] string? requestNumber,
    string? clientNumber,
    string? articleNumber,
    int? packSize,
    string? stockType,
    string? lotNumber,
    string? dateMark,
    string? serialNumber,
    string? reservationCode,
    string? storageArea
);
