using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record RequestStorageCapacityReport(
    [property: NotBlank] string? requestNumber,
    string? storageArea
);
