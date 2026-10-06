namespace KnappKiSoftMock.Api.Dtos;

public record OneApiOkResponse(
    int? Http,
    string? Status,
    string? Message,
    CallbackDeliveryResult? Callback)
{
    public OneApiOkResponse(int? http, string? status, string? message)
        : this(http, status, message, null)
    {
    }
}
