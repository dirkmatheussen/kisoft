namespace KnappKiSoftMock.Api.Dtos;

public record PackUnitFull(
    Article? article,
    int? packSize,
    int? length,
    int? width,
    int? height,
    int? pocketedWidth,
    double? weight,
    List<string?>? opticalCodes,
    List<string?>? articleFeatures,
    string? loadCarrier,
    string? articleImageLink,
    List<CapacityInformation?>? capacityInformation
);
