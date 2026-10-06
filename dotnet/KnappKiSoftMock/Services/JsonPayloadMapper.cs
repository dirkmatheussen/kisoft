using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace KnappKiSoftMock.Services;

public sealed class JsonPayloadMapper(IOptions<JsonOptions> jsonOptions)
{
    private readonly JsonSerializerOptions _options = jsonOptions.Value.JsonSerializerOptions;

    public string ToJson(object? value)
    {
        try
        {
            return JsonSerializer.Serialize(value, _options);
        }
        catch (Exception ex)
        {
            throw new InvalidOperationException("Failed to serialize payload to JSON", ex);
        }
    }

    public T FromJson<T>(string json)
    {
        try
        {
            return JsonSerializer.Deserialize<T>(json, _options)
                   ?? throw new InvalidOperationException("Failed to deserialize payload from JSON");
        }
        catch (Exception ex) when (ex is not InvalidOperationException)
        {
            throw new InvalidOperationException("Failed to deserialize payload from JSON", ex);
        }
    }

    public string? FindClientNumber(object? payload)
    {
        if (payload is null) return null;
        try
        {
            var node = JsonSerializer.SerializeToNode(payload, payload.GetType(), _options);
            return FindClientNumber(node);
        }
        catch
        {
            return null;
        }
    }

    private static string? FindClientNumber(JsonNode? node)
    {
        switch (node)
        {
            case null:
                return null;
            case JsonObject obj:
            {
                if (obj.TryGetPropertyValue("clientNumber", out var direct))
                {
                    var text = TextOrNull(direct);
                    if (text is not null) return text;
                }
                foreach (var child in obj)
                {
                    var found = FindClientNumber(child.Value);
                    if (found is not null) return found;
                }
                return null;
            }
            case JsonArray array:
            {
                foreach (var child in array)
                {
                    var found = FindClientNumber(child);
                    if (found is not null) return found;
                }
                return null;
            }
            default:
                return null;
        }
    }

    private static string? TextOrNull(JsonNode? node)
    {
        if (node is null) return null;
        var value = node switch
        {
            JsonValue v when v.TryGetValue<string>(out var s) => s,
            JsonValue v when v.TryGetValue<int>(out var i) => i.ToString(),
            JsonValue v when v.TryGetValue<long>(out var l) => l.ToString(),
            _ => null
        };
        return string.IsNullOrWhiteSpace(value) ? null : value;
    }
}
