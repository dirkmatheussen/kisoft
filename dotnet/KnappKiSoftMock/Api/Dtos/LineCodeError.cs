using System.Text.Json.Serialization;

namespace KnappKiSoftMock.Api.Dtos;

// Java: no @JsonInclude(NON_NULL) on this record, so null members are serialized.
public record LineCodeError(
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? lineReference,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? lineCode
);
