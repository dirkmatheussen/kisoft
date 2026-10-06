using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Options;
using Microsoft.Extensions.Options;

namespace KnappKiSoftMock.Services;

public sealed class WebhookOAuthTokenService(IOptions<MockOptions> options, IHttpClientFactory httpClientFactory)
{
    private const int ExpiryBufferSeconds = 60;
    private readonly MockOptions _options = options.Value;
    private readonly object _gate = new();
    private string? _cachedToken;
    private DateTimeOffset _expiresAt = DateTimeOffset.MinValue;

    public bool IsConfigured() =>
        !string.IsNullOrWhiteSpace(_options.WebhookOauthTenantId)
        && !string.IsNullOrWhiteSpace(_options.WebhookOauthClientId)
        && !string.IsNullOrWhiteSpace(_options.WebhookOauthClientSecret)
        && !string.IsNullOrWhiteSpace(_options.WebhookOauthScope);

    public void Invalidate()
    {
        lock (_gate)
        {
            _cachedToken = null;
            _expiresAt = DateTimeOffset.MinValue;
        }
    }

    public string? GetAccessToken()
    {
        if (!IsConfigured()) return null;
        var now = DateTimeOffset.UtcNow;
        if (_cachedToken is not null && now < _expiresAt) return _cachedToken;
        lock (_gate)
        {
            now = DateTimeOffset.UtcNow;
            if (_cachedToken is not null && now < _expiresAt) return _cachedToken;
            var renewing = _cachedToken is not null;
            if (renewing)
            {
                Console.WriteLine("Webhook OAuth access token expired — renewing");
            }
            return FetchToken(renewing);
        }
    }

    private string? FetchToken(bool renewing)
    {
        var tokenUrl = $"https://login.microsoftonline.com/{_options.WebhookOauthTenantId}/oauth2/v2.0/token";
        var form = new Dictionary<string, string>
        {
            ["grant_type"] = "client_credentials",
            ["client_id"] = _options.WebhookOauthClientId,
            ["client_secret"] = _options.WebhookOauthClientSecret,
            ["scope"] = _options.WebhookOauthScope
        };
        try
        {
            var client = httpClientFactory.CreateClient("webhooks");
            using var response = client.PostAsync(tokenUrl, new FormUrlEncodedContent(form)).GetAwaiter().GetResult();
            var body = response.Content.ReadAsStringAsync().GetAwaiter().GetResult();
            if (!response.IsSuccessStatusCode)
            {
                // RestTemplate throws HttpStatusCodeException here: "<status> <reason>: \"<body>\"".
                throw new HttpRequestException(
                    $"{(int)response.StatusCode} {response.ReasonPhrase}: \"{body}\"");
            }
            using var doc = JsonDocument.Parse(body);
            if (doc.RootElement.ValueKind != JsonValueKind.Object
                || !doc.RootElement.TryGetProperty("access_token", out var accessToken)
                || accessToken.ValueKind == JsonValueKind.Null)
            {
                Console.WriteLine("Webhook OAuth token response missing access_token");
                ClearCache();
                return null;
            }
            var token = accessToken.ValueKind == JsonValueKind.String ? accessToken.GetString()! : accessToken.GetRawText();
            var expiresIn = doc.RootElement.TryGetProperty("expires_in", out var expires)
                            && expires.ValueKind == JsonValueKind.Number
                ? (int)expires.GetDouble()
                : 3600;
            _cachedToken = token;
            _expiresAt = DateTimeOffset.UtcNow.AddSeconds(Math.Max(0, expiresIn - ExpiryBufferSeconds));
            Console.WriteLine(renewing
                ? $"Renewed webhook OAuth access token (expires in {expiresIn}s, refresh before {ExpiryBufferSeconds}s)"
                : $"Acquired webhook OAuth access token (expires in {expiresIn}s)");
            return token;
        }
        catch (Exception ex)
        {
            ClearCache();
            Console.WriteLine($"Failed to {(renewing ? "renew" : "obtain")} webhook OAuth token: {ex.Message}");
            return null;
        }
    }

    private void ClearCache()
    {
        _cachedToken = null;
        _expiresAt = DateTimeOffset.MinValue;
    }
}

public sealed class ReplyCallbackService(
    IOptions<MockOptions> options,
    IHttpClientFactory httpClientFactory,
    WebhookOAuthTokenService oauth,
    JsonPayloadMapper json)
{
    private readonly MockOptions _options = options.Value;

    public CallbackDeliveryResult? DeliverSync(string path, object payload, string messageName)
    {
        var body = SafeJson(payload);
        if (!_options.AreCallbacksEnabled)
        {
            Console.WriteLine($"Callback {messageName} skipped (disabled) — payload={body}");
            return null;
        }
        var url = _options.WebhookTargetUrl(path);
        if (url is null)
        {
            Console.WriteLine($"Callback {messageName} skipped (no URL) — payload={body}");
            return null;
        }
        Console.WriteLine($"Callback {messageName} → {url} — payload={body}");
        var result = DeliverWithResult(url, payload, messageName);
        Console.WriteLine(result.Delivered
            ? $"Sent {messageName} to {url} — HTTP {result.CallbackHttpStatus}"
            : $"Failed to send {messageName} to {url}: {result.ErrorMessage}");
        return result;
    }

    public void SendInboundDeliveryReply(InboundDeliveryReply reply) => Post("inboundDeliveryReply", reply, "InboundDeliveryReply");
    public void SendStockReceived(StockReceived stockReceived) => Post("stockReceived", stockReceived, "StockReceived");
    public void SendStorageOrderReply(StorageOrderReply reply) => Post("storageOrderReply", reply, "StorageOrderReply");
    public void SendGoodsOutOrderReply(GoodsOutOrderReply reply) => Post("goodsOutOrderReply", reply, "GoodsOutOrderReply");
    public void SendInventoryRequestReply(InventoryRequestReply reply) => Post("inventoryRequestReply", reply, "InventoryRequestReply");
    public void SendLoadUnitMoved(LoadUnitMoved moved) => Post("loadUnitMoved", moved, "LoadUnitMoved");
    public void SendStockCorrected(StockCorrected corrected) => Post("stockCorrected", corrected, "StockCorrected");
    public void SendStockLockChanged(StockLockChanged changed) => Post("stockLockChanged", changed, "StockLockChanged");
    public void SendInventoryReport(InventoryReport report) => Post("inventoryReport", report, "InventoryReport");
    public void SendStorageCapacityReport(StorageCapacityReport report) => Post("storageCapacityReport", report, "StorageCapacityReport");

    private void Post(string path, object payload, string messageName)
    {
        var body = SafeJson(payload);
        if (!_options.AreCallbacksEnabled)
        {
            Console.WriteLine($"Callback {messageName} skipped (disabled) — payload={body}");
            return;
        }
        var url = _options.WebhookTargetUrl(path);
        if (url is null)
        {
            Console.WriteLine($"Callback {messageName} skipped (no URL) — payload={body}");
            return;
        }
        Console.WriteLine($"Callback {messageName} → {url} — payload={body}");
        _ = Task.Run(() =>
        {
            try
            {
                var result = DeliverWithResult(url, payload, messageName);
                Console.WriteLine(result.Delivered
                    ? $"Sent {messageName} to {url} — HTTP {result.CallbackHttpStatus}"
                    : $"Failed to send {messageName} to {url}: {result.ErrorMessage}");
            }
            catch (Exception ex)
            {
                // Java: exception escapes the executor task and is logged by the executor.
                Console.Error.WriteLine($"Failed to send {messageName} to {url}: {ex}");
            }
        });
    }

    /// <summary>
    /// Same outcome mapping as the Java RestTemplate flow: 4xx/5xx → failure with parsed APIC body,
    /// any other status → delivered, connection/IO error → failure with null status and the exception
    /// text, 401 → one token renewal + retry (an IO error during the retry propagates like in Java).
    /// </summary>
    private CallbackDeliveryResult DeliverWithResult(string url, object payload, string messageName)
    {
        var token = oauth.GetAccessToken();
        if (oauth.IsConfigured() && token is null)
        {
            const string msg = "OAuth is configured but no Bearer token could be obtained";
            Console.Error.WriteLine($"Skipping {messageName} to {url} — {msg}");
            return CallbackDeliveryResult.Failure(url, msg);
        }

        var response = PostOnce(url, payload, token);
        if (response.Status is null)
        {
            return CallbackDeliveryResult.Failure(url, response.Error);
        }
        if (response.Status == 401 && oauth.IsConfigured())
        {
            Console.WriteLine($"{messageName} to {url} returned 401 — renewing OAuth token and retrying once");
            oauth.Invalidate();
            token = oauth.GetAccessToken();
            if (token is null)
            {
                return CallbackDeliveryResult.Failure(url, 401, response.Body, response.Reason, response.Reason);
            }
            response = PostOnce(url, payload, token);
            if (response.Status is null)
            {
                throw new HttpRequestException(response.Error);
            }
        }

        if (response.Status < 400)
        {
            return CallbackDeliveryResult.Success(url, response.Status.Value, response.Body);
        }
        return CallbackDeliveryResult.Failure(url, response.Status, response.Body, response.Reason, response.Reason);
    }

    private (int? Status, string? Body, string? Reason, string? Error) PostOnce(string url, object payload, string? token)
    {
        try
        {
            var client = httpClientFactory.CreateClient("webhooks");
            using var request = new HttpRequestMessage(HttpMethod.Post, url);
            request.Content = new StringContent(json.ToJson(payload), Encoding.UTF8);
            // Java sends "Content-Type: application/json" without a charset parameter.
            request.Content.Headers.ContentType = new MediaTypeHeaderValue("application/json");
            if (!string.IsNullOrWhiteSpace(_options.WebhookIbmClientId))
            {
                request.Headers.TryAddWithoutValidation("X-IBM-Client-Id", _options.WebhookIbmClientId);
            }
            if (!string.IsNullOrWhiteSpace(_options.WebhookIbmClientSecret))
            {
                request.Headers.TryAddWithoutValidation("X-IBM-Client-Secret", _options.WebhookIbmClientSecret);
            }
            var clientNumber = json.FindClientNumber(payload);
            if (clientNumber is not null)
            {
                request.Headers.TryAddWithoutValidation("clientNumber", clientNumber);
            }
            if (!string.IsNullOrWhiteSpace(token))
            {
                request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
            }
            using var response = client.Send(request);
            var body = response.Content.ReadAsStringAsync().GetAwaiter().GetResult();
            return ((int)response.StatusCode, body, response.ReasonPhrase, null);
        }
        catch (Exception ex)
        {
            return (null, null, null, ex.Message);
        }
    }

    private string SafeJson(object payload)
    {
        try { return json.ToJson(payload); }
        catch { return payload.ToString() ?? ""; }
    }
}
