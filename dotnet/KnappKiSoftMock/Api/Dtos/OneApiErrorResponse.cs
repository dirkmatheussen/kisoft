namespace KnappKiSoftMock.Api.Dtos;

public record OneApiErrorResponse(
    string? clientNumber,
    string? inboundDeliveryNumber,
    string? orderNumber,
    string? articleNumber,
    string? packSize,
    string? requestNumber,
    string? message,
    List<string?>? codes
);
