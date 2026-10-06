namespace KnappKiSoftMock.Api.Dtos;

public record StorageCapacityDetail(
    string? storageArea,
    int? locationCount,
    int? emptyLocationCount,
    int? filledLoadUnitCount,
    int? emptyLoadUnitCount,
    string? storageClass,
    string? locationClass,
    string? rackChannel,
    string? aisle,
    string? rack
);
