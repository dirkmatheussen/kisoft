namespace KnappKiSoftMock.Api.Dtos;

public record StorageCapacityReport(
    string? requestNumber,
    string? eventTime,
    List<StorageCapacityDetail?>? storageCapacityDetails
);
