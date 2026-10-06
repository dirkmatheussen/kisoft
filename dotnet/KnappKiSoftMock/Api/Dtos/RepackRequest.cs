using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record RepackRequest(
    [property: NotBlank] string? clientNumber,
    string? sourceLoadUnitCode,
    int? sourceSlot,
    string? targetLoadUnitCode,
    int? targetSlot,
    [property: NotBlank] string? articleNumber,
    int? packSize,
    int? deltaQuantity,
    string? stationName,
    string? reason,
    string? reservationCode
);
