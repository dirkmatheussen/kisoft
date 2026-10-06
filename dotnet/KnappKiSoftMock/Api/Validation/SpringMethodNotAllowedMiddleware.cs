using Microsoft.AspNetCore.Mvc.Controllers;
using Microsoft.AspNetCore.Routing.Template;

namespace KnappKiSoftMock.Api.Validation;

/// <summary>
/// Spring MVC answers a known path with an unsupported HTTP method with 405 + <c>Allow</c> header
/// and the default error body. ASP.NET would let the Blazor catch-all turn that into a 404, so the
/// controller route templates are matched explicitly here.
/// </summary>
public sealed class SpringMethodNotAllowedMiddleware(RequestDelegate next, EndpointDataSource endpoints)
{
    private readonly Lazy<List<(TemplateMatcher Matcher, IReadOnlyList<string> Methods)>> _routes = new(() => Build(endpoints));

    public async Task Invoke(HttpContext context)
    {
        if (context.GetEndpoint()?.Metadata.GetMetadata<ControllerActionDescriptor>() is null)
        {
            var allowed = new SortedSet<string>(StringComparer.Ordinal);
            foreach (var (matcher, methods) in _routes.Value)
            {
                if (matcher.TryMatch(context.Request.Path, new RouteValueDictionary()))
                {
                    allowed.UnionWith(methods);
                }
            }

            if (allowed.Count > 0 && !allowed.Contains(context.Request.Method.ToUpperInvariant()))
            {
                context.SetEndpoint(null);
                context.Response.Headers.Allow = string.Join(",", allowed);
                await SpringErrors.WriteAsync(context, StatusCodes.Status405MethodNotAllowed);
                return;
            }
        }

        await next(context);
    }

    private static List<(TemplateMatcher, IReadOnlyList<string>)> Build(EndpointDataSource endpoints)
    {
        var routes = new List<(TemplateMatcher, IReadOnlyList<string>)>();
        foreach (var endpoint in endpoints.Endpoints.OfType<RouteEndpoint>())
        {
            if (endpoint.Metadata.GetMetadata<ControllerActionDescriptor>() is null) continue;
            var rawText = endpoint.RoutePattern.RawText;
            if (string.IsNullOrEmpty(rawText)) continue;
            var methods = endpoint.Metadata.GetMetadata<IHttpMethodMetadata>()?.HttpMethods ?? [];
            var template = TemplateParser.Parse(rawText);
            routes.Add((new TemplateMatcher(template, new RouteValueDictionary()), methods));
        }
        return routes;
    }
}
