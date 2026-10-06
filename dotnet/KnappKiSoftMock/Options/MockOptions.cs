namespace KnappKiSoftMock.Options;

public sealed class MockOptions
{
    public const string SectionName = "Knapp:Mock";

    public bool BypassAuth { get; set; }
    public bool UiAuthEnabled { get; set; } = true;
    public string UiUsername { get; set; } = "knapp";
    public string UiPassword { get; set; } = "";
    public bool ReplyCallbackEnabled { get; set; } = true;
    public string ReplyCallbackUrl { get; set; } = "";
    public string ReplyCallbackPathPrefix { get; set; } = "oneapi/v1/_webhooks";
    public string WebhookIbmClientId { get; set; } = "";
    public string WebhookIbmClientSecret { get; set; } = "";
    public string WebhookOauthTenantId { get; set; } = "";
    public string WebhookOauthClientId { get; set; } = "";
    public string WebhookOauthClientSecret { get; set; } = "";
    public string WebhookOauthScope { get; set; } = "";
    public bool StorageOrderReplyEnabled { get; set; }
    public bool InboundAutoStock { get; set; } = true; // Java KnappMockProperties default; appsettings/deploy set it explicitly
    public string? ImportInventoryReport { get; set; }
    public bool ImportUniquifyArticles { get; set; } = true;
    public bool ImportReplaceAll { get; set; }
    public string ImportDir { get; set; } = "data";

    public bool AreCallbacksEnabled =>
        ReplyCallbackEnabled && !string.IsNullOrWhiteSpace(ReplyCallbackUrl);

    public string? WebhookTargetUrl(string messageName)
    {
        if (string.IsNullOrWhiteSpace(ReplyCallbackUrl))
        {
            return null;
        }

        // Java: replaceAll("/$", "") / replaceAll("^/", "") strip exactly one slash.
        var baseUrl = StripOneTrailing(ReplyCallbackUrl);
        var name = StripOneLeading(messageName);
        var prefix = ReplyCallbackPathPrefix;
        if (!string.IsNullOrWhiteSpace(prefix))
        {
            prefix = StripOneTrailing(StripOneLeading(prefix));
            return $"{baseUrl}/{prefix}/{name}";
        }

        return $"{baseUrl}/{name}";
    }

    private static string StripOneLeading(string value) =>
        value.StartsWith('/') ? value[1..] : value;

    private static string StripOneTrailing(string value) =>
        value.EndsWith('/') ? value[..^1] : value;
}
