using System.Text.Json;
using System.Text.Json.Serialization;
using KnappKiSoftMock.Api.Dtos;

namespace KnappKiSoftMock.Services;

/// <summary>
/// Jackson enum semantics for <see cref="LockAction"/>: the constant name must match exactly
/// (case-sensitive); an integer is accepted as ordinal; anything else is a 400 (unreadable body).
/// </summary>
public sealed class LockActionJsonConverter : JsonConverter<LockAction>
{
    public override LockAction Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        switch (reader.TokenType)
        {
            case JsonTokenType.String:
                var text = reader.GetString();
                if (text is not null && Enum.TryParse<LockAction>(text, ignoreCase: false, out var parsed)
                                     && Enum.IsDefined(parsed))
                {
                    return parsed;
                }
                throw new JsonException($"Cannot deserialize value of type LockAction from String \"{text}\"");
            case JsonTokenType.Number:
                if (reader.TryGetInt32(out var ordinal) && Enum.IsDefined((LockAction)ordinal))
                {
                    return (LockAction)ordinal;
                }
                throw new JsonException("Cannot deserialize value of type LockAction from Number");
            default:
                throw new JsonException("Cannot deserialize value of type LockAction from " + reader.TokenType);
        }
    }

    public override void Write(Utf8JsonWriter writer, LockAction value, JsonSerializerOptions options) =>
        writer.WriteStringValue(value.ToString());
}
