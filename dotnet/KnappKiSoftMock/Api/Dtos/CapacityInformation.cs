using System.Text.Json;
using System.Text.Json.Serialization;

namespace KnappKiSoftMock.Api.Dtos;

/// <summary>
/// Java: <c>@JsonAlias("maxStoredQuantity") Integer maximumStoredQuantity</c> — the alias is accepted on
/// input only; output always uses <c>maximumStoredQuantity</c> (class is @JsonInclude(NON_NULL)).
/// </summary>
[JsonConverter(typeof(CapacityInformationJsonConverter))]
public record CapacityInformation(
    string? loadCarrier,
    int? maximumStoredQuantity
);

public sealed class CapacityInformationJsonConverter : JsonConverter<CapacityInformation>
{
    public override CapacityInformation Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType != JsonTokenType.StartObject)
        {
            throw new JsonException("Cannot deserialize CapacityInformation from " + reader.TokenType);
        }

        string? loadCarrier = null;
        int? maximumStoredQuantity = null;
        while (reader.Read())
        {
            if (reader.TokenType == JsonTokenType.EndObject)
            {
                return new CapacityInformation(loadCarrier, maximumStoredQuantity);
            }
            var name = reader.GetString();
            reader.Read();
            switch (name)
            {
                case "loadCarrier":
                    loadCarrier = JsonSerializer.Deserialize<string?>(ref reader, options);
                    break;
                case "maximumStoredQuantity":
                case "maxStoredQuantity":
                    maximumStoredQuantity = JsonSerializer.Deserialize<int?>(ref reader, options);
                    break;
                default:
                    reader.Skip();
                    break;
            }
        }
        throw new JsonException("Unexpected end of JSON while reading CapacityInformation");
    }

    public override void Write(Utf8JsonWriter writer, CapacityInformation value, JsonSerializerOptions options)
    {
        writer.WriteStartObject();
        if (value.loadCarrier is not null) writer.WriteString("loadCarrier", value.loadCarrier);
        if (value.maximumStoredQuantity is not null) writer.WriteNumber("maximumStoredQuantity", value.maximumStoredQuantity.Value);
        writer.WriteEndObject();
    }
}
