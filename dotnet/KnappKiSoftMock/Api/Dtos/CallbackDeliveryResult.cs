using System.Text.Json;
using System.Text.Json.Serialization;

namespace KnappKiSoftMock.Api.Dtos;

// Java: no @JsonInclude(NON_NULL) on this record, so null members are serialized.
public record CallbackDeliveryResult(
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? TargetUrl,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] bool Delivered,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] int? CallbackHttpStatus,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? CallbackResponseBody,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? CallbackHttpMessage,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? MoreInformation,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? ErrorMessage)
{
    private const int MaxBodyLength = 2000;

    public static CallbackDeliveryResult Success(string url, int status, string? body) =>
        new(url, true, status, Truncate(body), null, null, null);

    public static CallbackDeliveryResult Failure(string url, int? status, string? body, string? httpMessage, string? fallback)
    {
        string? moreInformation = null;
        if (!string.IsNullOrWhiteSpace(body))
        {
            try
            {
                using var doc = JsonDocument.Parse(body);
                var root = doc.RootElement;
                if (root.TryGetProperty("httpCode", out var code) && code.ValueKind != JsonValueKind.Null)
                {
                    status = code.ValueKind == JsonValueKind.Number ? code.GetInt32() : int.TryParse(code.GetString(), out var parsed) ? parsed : status;
                }
                if (root.TryGetProperty("httpMessage", out var message) && message.ValueKind == JsonValueKind.String)
                {
                    httpMessage = message.GetString();
                }
                if (root.TryGetProperty("moreInformation", out var more) && more.ValueKind == JsonValueKind.String)
                {
                    moreInformation = more.GetString();
                }
            }
            catch (JsonException)
            {
                // keep the raw body
            }
        }

        return new(
            url,
            false,
            status,
            Truncate(body),
            httpMessage,
            moreInformation,
            FormatErrorMessage(status, httpMessage, moreInformation, fallback));
    }

    public static CallbackDeliveryResult Failure(string url, string? errorMessage) =>
        new(url, false, null, null, null, null, errorMessage);

    public string LogLine(string messageName) =>
        Delivered
            ? $"Sent {messageName} to {TargetUrl}"
            : $"Failed to send {messageName} to {TargetUrl}: {ErrorMessage}";

    private static string? FormatErrorMessage(int? status, string? httpMessage, string? moreInformation, string? fallback)
    {
        if (status is null && httpMessage is null && moreInformation is null)
        {
            return fallback;
        }

        var sb = new System.Text.StringBuilder();
        if (status is not null)
        {
            sb.Append(status);
        }
        if (!string.IsNullOrWhiteSpace(httpMessage))
        {
            if (sb.Length > 0) sb.Append(' ');
            sb.Append(httpMessage);
        }
        if (!string.IsNullOrWhiteSpace(moreInformation))
        {
            sb.Append(":\"").Append(moreInformation).Append('"');
        }
        return sb.Length == 0 ? fallback : sb.ToString();
    }

    private static string? Truncate(string? body)
    {
        if (string.IsNullOrWhiteSpace(body) || body.Length <= MaxBodyLength)
        {
            return body;
        }
        return body[..MaxBodyLength] + "…";
    }
}
