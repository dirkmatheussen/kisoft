using System.Globalization;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.WebUtilities;

namespace KnappKiSoftMock.Api.Validation;

/// <summary>
/// Spring Boot default error body (<c>DefaultErrorAttributes</c>, Spring Boot 3.x defaults:
/// no message, no trace, no field errors): <c>{"timestamp","status","error","path"}</c>.
/// The Java mock returns this for bean-validation failures, unreadable JSON, unknown routes,
/// wrong HTTP method, missing UI credentials and unhandled exceptions.
/// </summary>
public static class SpringErrors
{
    public sealed record Body(string timestamp, int status, string error, string path);

    private static readonly JsonSerializerOptions Options = new()
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping
    };

    public static Body Build(HttpContext http, int status) =>
        new(Timestamp(), status, ReasonPhrases.GetReasonPhrase(status), http.Request.PathBase + http.Request.Path);

    public static IActionResult Result(HttpContext http, int status) =>
        new ObjectResult(Build(http, status)) { StatusCode = status };

    public static Task WriteAsync(HttpContext http, int status)
    {
        http.Response.StatusCode = status;
        http.Response.ContentType = "application/json";
        return http.Response.WriteAsync(JsonSerializer.Serialize(Build(http, status), Options));
    }

    /// <summary>java.util.Date serialized by Spring Boot's Jackson: <c>yyyy-MM-dd'T'HH:mm:ss.SSS+00:00</c>.</summary>
    private static string Timestamp() =>
        DateTime.UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss.fff'+00:00'", CultureInfo.InvariantCulture);
}
