using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record MasterDataUpdateSession(
    string? clientNumber,
    [property: NotBlank, Pattern("SET|CLEANUP")] string? transmissionTag
);
