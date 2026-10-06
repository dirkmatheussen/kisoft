namespace KnappKiSoftMock.Api.Dtos;

public record PackUnitKeyRef(
    string? clientNumber,
    string? articleNumber,
    int? packSize
);
