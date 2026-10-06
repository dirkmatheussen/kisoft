using System.Net.Http.Headers;
using System.Text;
using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Options;
using Microsoft.Extensions.Options;

namespace KnappKiSoftMock.Api;

/// <summary>
/// Mirrors the Java SecurityConfig: /oneapi/** requires a Bearer token (BearerTokenFilter — any token,
/// scheme case-sensitive, 401 with the mock's JSON body); UI paths require HTTP Basic (Spring default
/// realm "Realm", 401 with the Spring default error body).
/// </summary>
public sealed class MockAuthMiddleware(RequestDelegate next, IOptions<MockOptions> options)
{
    private const string BearerPrefix = "Bearer ";
    private readonly MockOptions _options = options.Value;

    public async Task Invoke(HttpContext context)
    {
        var path = context.Request.Path;
        if (path.StartsWithSegments("/oneapi"))
        {
            if (!_options.BypassAuth && !HasBearer(context.Request))
            {
                context.Response.StatusCode = StatusCodes.Status401Unauthorized;
                context.Response.ContentType = "application/json";
                await context.Response.WriteAsync("{\"error\":\"unauthorized\",\"message\":\"Bearer token required\"}");
                return;
            }
        }
        else if (_options.UiAuthEnabled && IsUiPath(path) && !HasBasic(context.Request))
        {
            context.Response.Headers.WWWAuthenticate = "Basic realm=\"Realm\"";
            await SpringErrors.WriteAsync(context, StatusCodes.Status401Unauthorized);
            return;
        }

        await next(context);
    }

    private bool HasBasic(HttpRequest request)
    {
        if (!AuthenticationHeaderValue.TryParse(request.Headers.Authorization, out var header)
            || !header.Scheme.Equals("Basic", StringComparison.OrdinalIgnoreCase)
            || string.IsNullOrEmpty(header.Parameter))
        {
            return false;
        }

        try
        {
            var decoded = Encoding.UTF8.GetString(Convert.FromBase64String(header.Parameter));
            var split = decoded.Split(':', 2);
            return split.Length == 2
                   && split[0] == _options.UiUsername
                   && split[1] == _options.UiPassword;
        }
        catch (FormatException)
        {
            return false;
        }
    }

    private static bool HasBearer(HttpRequest request)
    {
        string? authHeader = request.Headers.Authorization;
        if (string.IsNullOrWhiteSpace(authHeader) || !authHeader.StartsWith(BearerPrefix, StringComparison.Ordinal))
        {
            return false;
        }
        return authHeader[BearerPrefix.Length..].Trim().Length > 0;
    }

    private static bool IsUiPath(PathString path) =>
        path == "/" || path == "/home"
        || path.StartsWithSegments("/swagger")
        || path.StartsWithSegments("/swagger-ui")
        || path.StartsWithSegments("/swagger-ui.html")
        || path.StartsWithSegments("/openapi")
        || path.StartsWithSegments("/v3");
}
