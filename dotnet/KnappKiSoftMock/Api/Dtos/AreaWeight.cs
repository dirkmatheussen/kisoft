namespace KnappKiSoftMock.Api.Dtos;

public record AreaWeight(
    string? scaleStationName,
    double? expectedWeight,
    double? minimumWeight,
    double? maximumWeight
);
