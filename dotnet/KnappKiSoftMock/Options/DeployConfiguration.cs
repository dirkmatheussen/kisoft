using System.Collections;
using System.Text.Json;

namespace KnappKiSoftMock.Options;

/// <summary>
/// Maps the Java deploy contract onto .NET configuration: the variables in
/// <c>/etc/knapp-kisoft-mock/env</c>, Spring relaxed names (<c>KNAPP_MOCK_*</c>),
/// <c>SPRING_APPLICATION_JSON</c>, and <c>--knapp.mock.*</c> / <c>--server.port</c>.
/// An explicit .NET setting (<c>Knapp__Mock__*</c>, <c>ConnectionStrings__KiSoft</c>,
/// <c>ASPNETCORE_URLS</c>, <c>--Knapp:Mock:*</c>) is left as-is.
/// </summary>
public static class DeployConfiguration
{
    private static readonly Dictionary<string, string> MockKeys = new(StringComparer.OrdinalIgnoreCase)
    {
        ["bypass-auth"] = "Knapp:Mock:BypassAuth",
        ["ui-auth-enabled"] = "Knapp:Mock:UiAuthEnabled",
        ["ui-username"] = "Knapp:Mock:UiUsername",
        ["ui-password"] = "Knapp:Mock:UiPassword",
        ["reply-callback-enabled"] = "Knapp:Mock:ReplyCallbackEnabled",
        ["reply-callback-url"] = "Knapp:Mock:ReplyCallbackUrl",
        ["reply-callback-path-prefix"] = "Knapp:Mock:ReplyCallbackPathPrefix",
        ["webhook-ibm-client-id"] = "Knapp:Mock:WebhookIbmClientId",
        ["webhook-ibm-client-secret"] = "Knapp:Mock:WebhookIbmClientSecret",
        ["webhook-oauth-tenant-id"] = "Knapp:Mock:WebhookOauthTenantId",
        ["webhook-oauth-client-id"] = "Knapp:Mock:WebhookOauthClientId",
        ["webhook-oauth-client-secret"] = "Knapp:Mock:WebhookOauthClientSecret",
        ["webhook-oauth-scope"] = "Knapp:Mock:WebhookOauthScope",
        ["storage-order-reply-enabled"] = "Knapp:Mock:StorageOrderReplyEnabled",
        ["inbound-auto-stock"] = "Knapp:Mock:InboundAutoStock",
        ["import-inventory-report"] = "Knapp:Mock:ImportInventoryReport",
        ["import-uniquify-articles"] = "Knapp:Mock:ImportUniquifyArticles",
        ["import-replace-all"] = "Knapp:Mock:ImportReplaceAll",
        ["import-dir"] = "Knapp:Mock:ImportDir",
    };

    public static void Apply(ConfigurationManager configuration, string[] args)
    {
        var values = new Dictionary<string, string?>(StringComparer.OrdinalIgnoreCase);

        void Set(string key, string? value)
        {
            if (string.IsNullOrWhiteSpace(value) || HasNativeOverride(key, args)) return;
            values[key] = value.Trim();
        }

        ApplyEnvironment(Set);
        ApplySpringApplicationJson(Set);
        ApplyCommandLine(args, Set);

        configuration.AddInMemoryCollection(values);
    }

    private static void ApplyEnvironment(Action<string, string?> set)
    {
        // Placeholder names from application.yml and deploy/knapp-kisoft-mock.env.
        // KNAPP_MOCK_* is applied afterwards and wins, matching Spring relaxed binding.
        set(MockKeys["ui-password"], Environment.GetEnvironmentVariable("MOCK_UI_PASSWORD"));
        set(MockKeys["reply-callback-url"], Environment.GetEnvironmentVariable("KNAPP_REPLY_CALLBACK_URL"));
        set(MockKeys["webhook-ibm-client-id"], Environment.GetEnvironmentVariable("KNAPP_WEBHOOK_IBM_CLIENT_ID"));
        set(MockKeys["webhook-ibm-client-secret"], Environment.GetEnvironmentVariable("KNAPP_WEBHOOK_IBM_CLIENT_SECRET"));
        set(MockKeys["webhook-oauth-tenant-id"], Environment.GetEnvironmentVariable("KNAPP_WEBHOOK_OAUTH_TENANT_ID"));
        set(MockKeys["webhook-oauth-client-id"], Environment.GetEnvironmentVariable("KNAPP_WEBHOOK_OAUTH_CLIENT_ID"));
        set(MockKeys["webhook-oauth-client-secret"], Environment.GetEnvironmentVariable("KNAPP_WEBHOOK_OAUTH_CLIENT_SECRET"));
        set(MockKeys["webhook-oauth-scope"], Environment.GetEnvironmentVariable("KNAPP_WEBHOOK_OAUTH_SCOPE"));

        foreach (var (kebab, configKey) in MockKeys)
        {
            var relaxed = "KNAPP_MOCK_" + kebab.Replace('-', '_').ToUpperInvariant();
            set(configKey, Environment.GetEnvironmentVariable(relaxed));
        }

        if (!HasEnv("ASPNETCORE_URLS"))
        {
            set("Knapp:ServerPort", Environment.GetEnvironmentVariable("SERVER_PORT"));
        }

        set("Knapp:PathBase", Environment.GetEnvironmentVariable("SERVER_SERVLET_CONTEXT_PATH"));

        if (!HasEnv("ConnectionStrings__KiSoft"))
        {
            var sqlite = SqliteFromH2File(Environment.GetEnvironmentVariable("KNAPP_H2_FILE"))
                         ?? SqliteFromJdbc(Environment.GetEnvironmentVariable("SPRING_DATASOURCE_URL"));
            set("ConnectionStrings:KiSoft", sqlite);
        }
    }

    private static void ApplySpringApplicationJson(Action<string, string?> set)
    {
        var json = Environment.GetEnvironmentVariable("SPRING_APPLICATION_JSON");
        if (string.IsNullOrWhiteSpace(json)) return;

        try
        {
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            if (TryProperty(root, "knapp", out var knapp) && TryProperty(knapp, "mock", out var mock))
            {
                foreach (var property in mock.EnumerateObject())
                {
                    if (MockKeys.TryGetValue(property.Name, out var configKey))
                    {
                        set(configKey, JsonText(property.Value));
                    }
                }
            }

            if (TryProperty(root, "server", out var server))
            {
                if (TryProperty(server, "port", out var port))
                {
                    set("Knapp:ServerPort", JsonText(port));
                }
                if (TryProperty(server, "servlet", out var servlet)
                    && TryProperty(servlet, "context-path", out var contextPath))
                {
                    set("Knapp:PathBase", JsonText(contextPath));
                }
            }

            if (TryProperty(root, "spring", out var spring)
                && TryProperty(spring, "datasource", out var datasource)
                && TryProperty(datasource, "url", out var url)
                && !HasEnv("ConnectionStrings__KiSoft"))
            {
                set("ConnectionStrings:KiSoft", SqliteFromJdbc(JsonText(url)));
            }
        }
        catch (JsonException)
        {
            Console.Error.WriteLine("SPRING_APPLICATION_JSON is not valid JSON and was ignored.");
        }
    }

    private static void ApplyCommandLine(string[] args, Action<string, string?> set)
    {
        for (var i = 0; i < args.Length; i++)
        {
            var arg = args[i];
            string? name;
            string? value;
            if (arg.StartsWith("--knapp.mock.", StringComparison.OrdinalIgnoreCase))
            {
                var body = arg["--knapp.mock.".Length..];
                var eq = body.IndexOf('=');
                if (eq >= 0)
                {
                    name = body[..eq];
                    value = body[(eq + 1)..];
                }
                else if (i + 1 < args.Length && !args[i + 1].StartsWith('-'))
                {
                    name = body;
                    value = args[++i];
                }
                else
                {
                    continue;
                }

                if (MockKeys.TryGetValue(name, out var configKey))
                {
                    set(configKey, value);
                }
            }
            else if (arg.StartsWith("--server.port=", StringComparison.OrdinalIgnoreCase))
            {
                set("Knapp:ServerPort", arg["--server.port=".Length..]);
            }
            else if (arg.Equals("--server.port", StringComparison.OrdinalIgnoreCase)
                     && i + 1 < args.Length)
            {
                set("Knapp:ServerPort", args[++i]);
            }
            else if (arg.StartsWith("--server.servlet.context-path=", StringComparison.OrdinalIgnoreCase))
            {
                set("Knapp:PathBase", arg["--server.servlet.context-path=".Length..]);
            }
        }
    }

    public static string ListenUrl(IConfiguration configuration)
    {
        if (HasEnv("ASPNETCORE_URLS"))
        {
            return Environment.GetEnvironmentVariable("ASPNETCORE_URLS") ?? "http://*:8084";
        }

        var port = configuration["Knapp:ServerPort"];
        if (string.IsNullOrWhiteSpace(port)) port = "8084";
        return "http://*:" + port.Trim();
    }

    public static string PathBase(IConfiguration configuration)
    {
        var path = configuration["Knapp:PathBase"];
        if (string.IsNullOrWhiteSpace(path)) path = "/kisoft";
        path = path.Trim();
        if (!path.StartsWith('/')) path = "/" + path;
        return path.TrimEnd('/');
    }

    public static string? SqliteDataSource(string? connectionString)
    {
        if (string.IsNullOrWhiteSpace(connectionString)) return null;
        foreach (var part in connectionString.Split(';', StringSplitOptions.RemoveEmptyEntries))
        {
            var eq = part.IndexOf('=');
            if (eq <= 0) continue;
            var name = part[..eq].Trim();
            if (name.Equals("Data Source", StringComparison.OrdinalIgnoreCase)
                || name.Equals("DataSource", StringComparison.OrdinalIgnoreCase))
            {
                return part[(eq + 1)..].Trim();
            }
        }

        return null;
    }

    private static bool HasNativeOverride(string configKey, string[] args)
    {
        var envName = configKey.Replace(":", "__");
        if (HasEnv(envName)) return true;

        var flag = "--" + configKey;
        foreach (var arg in args)
        {
            if (arg.Equals(flag, StringComparison.OrdinalIgnoreCase)
                || arg.StartsWith(flag + "=", StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }
        }

        return false;
    }

    private static bool HasEnv(string name)
    {
        foreach (DictionaryEntry entry in Environment.GetEnvironmentVariables())
        {
            if (string.Equals(entry.Key?.ToString(), name, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }
        }

        return false;
    }

    private static string? SqliteFromH2File(string? path)
    {
        if (string.IsNullOrWhiteSpace(path)) return null;
        path = path.Trim();
        if (path.EndsWith(".mv.db", StringComparison.OrdinalIgnoreCase))
        {
            path = path[..^".mv.db".Length] + ".db";
        }
        else if (!path.EndsWith(".db", StringComparison.OrdinalIgnoreCase))
        {
            path += ".db";
        }

        return "Data Source=" + path;
    }

    private static string? SqliteFromJdbc(string? url)
    {
        if (string.IsNullOrWhiteSpace(url)) return null;
        const string marker = "jdbc:h2:file:";
        var idx = url.IndexOf(marker, StringComparison.OrdinalIgnoreCase);
        if (idx < 0) return null;
        var path = url[(idx + marker.Length)..];
        var semi = path.IndexOf(';');
        if (semi >= 0) path = path[..semi];
        return SqliteFromH2File(path);
    }

    private static bool TryProperty(JsonElement element, string name, out JsonElement value)
    {
        if (element.ValueKind == JsonValueKind.Object)
        {
            foreach (var property in element.EnumerateObject())
            {
                if (string.Equals(property.Name, name, StringComparison.OrdinalIgnoreCase))
                {
                    value = property.Value;
                    return true;
                }
            }
        }

        value = default;
        return false;
    }

    private static string? JsonText(JsonElement value) => value.ValueKind switch
    {
        JsonValueKind.String => value.GetString(),
        JsonValueKind.True => "true",
        JsonValueKind.False => "false",
        JsonValueKind.Number => value.GetRawText(),
        JsonValueKind.Null => null,
        _ => value.GetRawText()
    };
}
