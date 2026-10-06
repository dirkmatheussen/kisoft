using System.Text.Json.Serialization;

namespace KnappKiSoftMock.Api.Dtos;

public record ODataCollectionResponse<T>(
    [property: JsonPropertyName("@odata.context")] string? Context,
    [property: JsonPropertyName("@odata.count")] int? Count,
    List<T>? Value);
