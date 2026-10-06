namespace KnappKiSoftMock.Api.Dtos;

public record LoadUnitProperties(
    bool? isTouched,
    double? loadUnitHeight,
    double? loadUnitWidth,
    double? loadUnitLength,
    double? loadUnitWeight
);
