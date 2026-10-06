using KnappKiSoftMock.Api.Validation;

namespace KnappKiSoftMock.Api.Dtos;

public record StockLockOperatorRequest(
    [property: NotNull] LockAction? Action,
    [property: NotBlank] string? ClientNumber,
    [property: NotBlank] string? ArticleNumber,
    [property: NotNull] int? PackSize,
    [property: NotBlank] string? ReservationCode,
    List<string>? StockLockReasons,
    string? StationName,
    string? Reason);
