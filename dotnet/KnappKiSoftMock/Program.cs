using System.Net;
using System.Reflection;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Serialization;
using KnappKiSoftMock.Api;
using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Components;
using KnappKiSoftMock.Options;
using KnappKiSoftMock.Persistence;
using KnappKiSoftMock.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.OpenApi;
using Swashbuckle.AspNetCore.Swagger;

// A published single-file app extracts wwwroot and appsettings next to the bundled
// assemblies (AppContext.BaseDirectory). dotnet run has no publish manifest there and
// keeps the project directory as content root.
var publishedEndpoints = Path.Combine(
    AppContext.BaseDirectory,
    Assembly.GetExecutingAssembly().GetName().Name + ".staticwebassets.endpoints.json");
var processDir = Path.GetDirectoryName(Environment.ProcessPath);
var extractedBundle = File.Exists(publishedEndpoints)
    && !string.IsNullOrEmpty(processDir)
    && !string.Equals(
        Path.GetFullPath(AppContext.BaseDirectory).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar),
        Path.GetFullPath(processDir).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar),
        StringComparison.OrdinalIgnoreCase);
var builder = extractedBundle
    ? WebApplication.CreateBuilder(new WebApplicationOptions
    {
        Args = args,
        ContentRootPath = AppContext.BaseDirectory
    })
    : WebApplication.CreateBuilder(args);
DeployConfiguration.Apply(builder.Configuration, args);

if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("ASPNETCORE_URLS")))
{
    builder.WebHost.UseUrls(DeployConfiguration.ListenUrl(builder.Configuration));
}
builder.WebHost.ConfigureKestrel(options => options.Limits.MaxRequestBodySize = 52 * 1024 * 1024);

builder.Services.Configure<MockOptions>(builder.Configuration.GetSection(MockOptions.SectionName));
builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseSqlite(builder.Configuration.GetConnectionString("KiSoft") ?? "Data Source=data/kisoftmock.db"));

builder.Services.AddControllers(options =>
    {
        // Java: @RequestBody @Valid → jakarta bean validation → 400 with the Spring default error body.
        options.Filters.Add<ValidateRequestBodyFilter>();
    })
    .ConfigureApiBehaviorOptions(options =>
    {
        // Unreadable/missing JSON body → same Spring default error body as Java (HttpMessageNotReadable).
        options.InvalidModelStateResponseFactory = context =>
            SpringErrors.Result(context.HttpContext, StatusCodes.Status400BadRequest);
    })
    .AddJsonOptions(options =>
    {
        // Jackson defaults: @JsonInclude(NON_NULL) on all DTOs, case-sensitive property names,
        // no HTML/Unicode escaping, case-sensitive enum constants.
        options.JsonSerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
        options.JsonSerializerOptions.PropertyNameCaseInsensitive = false;
        options.JsonSerializerOptions.Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping;
        options.JsonSerializerOptions.Converters.Add(new LockActionJsonConverter());
    });
// Java RestTemplate has no read/connect timeout configured.
builder.Services.AddHttpClient("webhooks", client => client.Timeout = Timeout.InfiniteTimeSpan);
builder.Services.AddSingleton<JsonPayloadMapper>();
builder.Services.AddScoped<MasterdataSessionService>();
builder.Services.AddScoped<PackUnitStoreService>();
builder.Services.AddScoped<InboundDeliveryStoreService>();
builder.Services.AddScoped<GoodsOutOrderStoreService>();
builder.Services.AddScoped<InventoryRequestStoreService>();
builder.Services.AddScoped<AsrsStockService>();
builder.Services.AddScoped<InboundDeliveryLifecycleService>();
builder.Services.AddScoped<GoodsOutOrderLifecycleService>();
builder.Services.AddScoped<InventoryRequestLifecycleService>();
builder.Services.AddScoped<InventoryImportService>();
builder.Services.AddSingleton<WebhookOAuthTokenService>();
builder.Services.AddSingleton<ReplyCallbackService>();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(options =>
{
    var mock = builder.Configuration.GetSection(MockOptions.SectionName).Get<MockOptions>() ?? new MockOptions();
    var apicExample = mock.AreCallbacksEnabled
        ? mock.WebhookTargetUrl("stockReceived")
        : "{reply-callback-url}/oneapi/v1/_webhooks/stockReceived";
    var webhookNote = mock.AreCallbacksEnabled
        ? "Lifecycle events POST callbacks to IBM APIC server-side (e.g. **" + apicExample + "**). "
          + "Use **Webhooks (outgoing)** with wait=true to see the APIC response in the reply."
        : "Set knapp.mock.reply-callback-url to enable outgoing IBM APIC callbacks.";

    options.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "KNAPP KiSoft Mock API",
        Version = "4.0.10",
        Description = "Mock server simulating the KNAPP KiSoft One API (OpenAPI 4.0.0 / KiSoft 2.12.2), limited to the message subset in scope for "
            + "VOLVO TRUCKS Tacoma per the HOST Interface Specification One API Appendix (P000-013061). "
            + "Only the in-scope HOST → KiSoft One calls are exposed. Bearer token required (OAuth2/Entra ID). "
            + "**Operations under tag \"" + JavaOpenApiOverlayFilter.MockODataReadTag + "\" are mock-only inspection GETs — they are "
            + "not part of the KiSoft One Product API (HIS Appendix §2.3.1: KiSoft exposes no GET endpoints).** "
            + "Swagger Try it out uses the host you opened the docs on. "
            + webhookNote
    });
    options.CustomSchemaIds(JavaOpenApiOverlayFilter.JavaSchemaId);
    options.UseInlineDefinitionsForEnums(); // springdoc inlines LockAction instead of a component schema
    options.DocumentFilter<JavaOpenApiOverlayFilter>();
    options.AddSecurityDefinition("bearerAuth", new OpenApiSecurityScheme
    {
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT",
        Description = "OAuth2 Bearer token for inbound /oneapi calls (any token accepted on the mock)."
    });
    options.AddSecurityRequirement(document => new OpenApiSecurityRequirement
    {
        [new OpenApiSecuritySchemeReference("bearerAuth", document)] = []
    });
});
builder.Services.AddRazorComponents().AddInteractiveServerComponents();

var app = builder.Build();

// Java UiAuthConfig.uiPasswordStartupCheck: refuse to start without a UI password.
var startupOptions = app.Services.GetRequiredService<Microsoft.Extensions.Options.IOptions<MockOptions>>().Value;
if (startupOptions.UiAuthEnabled && string.IsNullOrWhiteSpace(startupOptions.UiPassword))
{
    throw new InvalidOperationException(
        "UI login is enabled but no password is set. "
        + "Set knapp.mock.ui-password or the MOCK_UI_PASSWORD environment variable.");
}

var sqlitePath = DeployConfiguration.SqliteDataSource(app.Configuration.GetConnectionString("KiSoft"));
if (!string.IsNullOrWhiteSpace(sqlitePath)
    && !sqlitePath.Equals(":memory:", StringComparison.OrdinalIgnoreCase))
{
    var directory = Path.GetDirectoryName(sqlitePath);
    if (!string.IsNullOrWhiteSpace(directory)) Directory.CreateDirectory(directory);
}
Directory.CreateDirectory(Path.Combine(app.Environment.ContentRootPath, "data"));
using (var scope = app.Services.CreateScope())
{
    scope.ServiceProvider.GetRequiredService<AppDbContext>().Database.EnsureCreated();
    H2ToSqliteImporter.ImportIfNeeded(app.Configuration.GetConnectionString("KiSoft"));
    ImportInventoryAtStartup(scope.ServiceProvider);
}
LogDeploy(app);

// Path base must run before routing. Otherwise Blazor's catch-all matches
// /kisoft/... first and rejects PUT/POST/PATCH/DELETE.
app.UsePathBase(DeployConfiguration.PathBase(app.Configuration));
app.UseRouting();
if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Error", createScopeForErrors: true);
}
// /oneapi: unhandled exceptions and body-less 404/405/415 answer with the Spring default error JSON.
app.UseWhen(
    context => context.Request.Path.StartsWithSegments("/oneapi"),
    branch =>
    {
        branch.UseExceptionHandler(new ExceptionHandlerOptions
        {
            ExceptionHandler = http => SpringErrors.WriteAsync(http, StatusCodes.Status500InternalServerError)
        });
        branch.UseStatusCodePages(context =>
            SpringErrors.WriteAsync(context.HttpContext, context.HttpContext.Response.StatusCode));
    });
app.UseMiddleware<MockAuthMiddleware>();
app.UseWhen(
    context => context.Request.Path.StartsWithSegments("/oneapi"),
    branch => branch.UseMiddleware<SpringMethodNotAllowedMiddleware>());
app.UseWhen(
    context => !context.Request.Path.StartsWithSegments("/oneapi"),
    branch => branch.UseStatusCodePagesWithReExecute("/not-found", createScopeForStatusCodePages: true));
app.UseAntiforgery();
app.UseSwagger(options =>
{
    options.RouteTemplate = "swagger-ui/{documentName}/swagger.json";
});
app.UseSwaggerUI(options =>
{
    options.RoutePrefix = "swagger-ui";
    options.SwaggerEndpoint("v1/swagger.json", "KNAPP KiSoft Mock");
    options.EnablePersistAuthorization();
    options.ConfigObject.AdditionalItems["operationsSorter"] = "method";
    options.ConfigObject.AdditionalItems["tagsSorter"] = "alpha";
    // Same as the Java UI: Try it out sends a dummy bearer on /oneapi unless Authorize set one.
    options.UseRequestInterceptor(
        "(request) => { const url = String(request.url || ''); if (url.indexOf('/oneapi/') !== -1 && !request.headers['Authorization']) { request.headers['Authorization'] = 'Bearer swagger-ui'; } return request; }");
});
app.MapGet("/swagger-ui.html", SwaggerUiRedirect).ExcludeFromDescription();
app.MapGet("/swagger", SwaggerUiRedirect).ExcludeFromDescription();
app.MapGet("/swagger/index.html", SwaggerUiRedirect).ExcludeFromDescription();
app.MapGet("/openapi", (HttpContext http) =>
{
    var document = http.RequestServices.GetRequiredService<ISwaggerProvider>().GetSwagger("v1");
    var json = OpenApiJson(document);
    var html = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8" />
          <title>OpenAPI — KNAPP KiSoft One Mock</title>
          <style>
            html, body { margin: 0; background: #ffffff; color: #1f2328; }
            pre {
              margin: 0;
              padding: 24px;
              font: 13px/1.45 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
              white-space: pre;
            }
          </style>
        </head>
        <body><pre>
        """ + WebUtility.HtmlEncode(json) + "</pre></body></html>";
    return Results.Content(html, "text/html; charset=utf-8");
}).ExcludeFromDescription();
app.MapControllers().DisableAntiforgery();
app.MapStaticAssets();
app.MapRazorComponents<App>().AddInteractiveServerRenderMode();
app.Run();

static IResult SwaggerUiRedirect(HttpContext http) =>
    Results.Redirect(http.Request.PathBase + "/swagger-ui/index.html");

static void ImportInventoryAtStartup(IServiceProvider services)
{
    var options = services.GetRequiredService<Microsoft.Extensions.Options.IOptions<MockOptions>>().Value;
    var path = options.ImportInventoryReport;
    if (string.IsNullOrWhiteSpace(path)) return;
    if (!File.Exists(path))
    {
        Console.Error.WriteLine("knapp.mock.import-inventory-report set but file not found: " + path);
        return;
    }

    try
    {
        Console.WriteLine(
            $"Starting inventory import from {path} (uniquify={options.ImportUniquifyArticles}, replaceAll={options.ImportReplaceAll})");
        var result = services.GetRequiredService<InventoryImportService>()
            .ImportFromFile(path, options.ImportUniquifyArticles, options.ImportReplaceAll);
        Console.WriteLine($"Startup inventory import finished: read={result.RowsRead}, written={result.RowsWritten}");
    }
    catch (Exception ex)
    {
        Console.Error.WriteLine("Startup inventory import failed: " + ex.Message);
    }
}

static void LogDeploy(WebApplication app)
{
    var options = app.Services.GetRequiredService<Microsoft.Extensions.Options.IOptions<MockOptions>>().Value;
    var log = app.Logger;
    log.LogInformation(
        "UI auth {State} for user {User}; inbound-auto-stock={AutoStock}; storage-order-reply={StorageReply}; import-dir={ImportDir}",
        options.UiAuthEnabled ? "enabled" : "disabled",
        options.UiUsername,
        options.InboundAutoStock,
        options.StorageOrderReplyEnabled,
        options.ImportDir);
    log.LogInformation("SQLite database {DataSource}", app.Configuration.GetConnectionString("KiSoft"));

    if (!options.AreCallbacksEnabled)
    {
        log.LogWarning(options.ReplyCallbackEnabled
            ? "Outgoing KiSoft → HOST webhooks are DISABLED (knapp.mock.reply-callback-url is blank)"
            : "Outgoing KiSoft → HOST webhooks are DISABLED (knapp.mock.reply-callback-enabled=false)");
        return;
    }

    log.LogInformation("Outgoing KiSoft → HOST webhooks ENABLED → {Url}", options.ReplyCallbackUrl);
    log.LogInformation("  Example: POST {Target}", options.WebhookTargetUrl("inboundDeliveryReply"));
    log.LogInformation("  IBM APIC headers: X-IBM-Client-Id={ClientId}", Mask(options.WebhookIbmClientId));
    log.LogInformation(
        "  IBM APIC headers: X-IBM-Client-Secret={Secret}",
        string.IsNullOrWhiteSpace(options.WebhookIbmClientSecret) ? "missing" : "configured");

    var oauth = app.Services.GetRequiredService<WebhookOAuthTokenService>();
    if (!oauth.IsConfigured())
    {
        log.LogWarning("  Entra ID OAuth not fully configured — webhooks will be sent without Authorization Bearer");
        return;
    }

    var token = oauth.GetAccessToken();
    if (token is null)
    {
        log.LogError("  Entra ID Bearer token: FAILED — webhooks will be skipped until token can be obtained");
    }
    else
    {
        log.LogInformation("  Entra ID Bearer token: acquired (length={Length})", token.Length);
    }
}

static string Mask(string? value)
{
    if (string.IsNullOrWhiteSpace(value)) return "missing";
    if (value.Length <= 8) return "configured";
    return value[..4] + "…" + value[^4..];
}

static string OpenApiJson(OpenApiDocument document)
{
    using var writer = new StringWriter();
    document.SerializeAsV3(new OpenApiJsonWriter(writer));
    writer.Flush();
    using var parsed = JsonDocument.Parse(writer.ToString());
    return JsonSerializer.Serialize(parsed, new JsonSerializerOptions
    {
        WriteIndented = true,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping
    });
}
