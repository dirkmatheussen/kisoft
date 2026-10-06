using System.Net.Http;
using System.Reflection;
using System.Text.Json;
using Microsoft.OpenApi;
using Swashbuckle.AspNetCore.SwaggerGen;

/// <summary>
/// Copies the Java SpringDoc text (tags, summaries, descriptions, responses, schemas)
/// onto the generated document so Swagger UI matches the original mock.
/// </summary>
public sealed class JavaOpenApiOverlayFilter : IDocumentFilter
{
    public const string MockODataReadTag = "Mock OData Read (NOT KiSoft API)";

    private const string ResourceName = "KnappKiSoftMock.java-openapi-overlay.json";

    /// <summary>
    /// springdoc names generic schemas "ODataCollectionResponseInventoryItem"; Swashbuckle defaults to
    /// "InventoryItemODataCollectionResponse". Used via <c>SwaggerGenOptions.CustomSchemaIds</c>.
    /// </summary>
    public static string JavaSchemaId(Type type)
    {
        if (!type.IsGenericType)
        {
            return type.Name;
        }
        var baseName = type.Name[..type.Name.IndexOf('`')];
        return baseName + string.Concat(type.GetGenericArguments().Select(JavaSchemaId));
    }

    private static readonly Dictionary<string, string> SchemaAliases = new(StringComparer.Ordinal);

    private static readonly Lazy<OverlayDocument> Overlay = new(Load);

    public void Apply(OpenApiDocument swaggerDoc, DocumentFilterContext context)
    {
        var overlay = Overlay.Value;
        swaggerDoc.Tags ??= new HashSet<OpenApiTag>();
        swaggerDoc.Tags.Clear();
        foreach (var tag in overlay.Tags)
        {
            swaggerDoc.Tags.Add(new OpenApiTag { Name = tag.Name, Description = tag.Description });
        }

        if (swaggerDoc.Paths is not null)
        {
            foreach (var (path, methods) in overlay.Paths)
            {
                if (!swaggerDoc.Paths.TryGetValue(path, out var pathItem) || pathItem?.Operations is null)
                {
                    continue;
                }

                foreach (var (method, source) in methods)
                {
                    if (!pathItem.Operations.TryGetValue(new HttpMethod(method.ToUpperInvariant()), out var operation) || operation is null)
                    {
                        continue;
                    }

                    ApplyOperation(swaggerDoc, operation, source);
                }
            }
        }

        if (swaggerDoc.Components?.Schemas is not null)
        {
            ApplySchemas(swaggerDoc.Components.Schemas, overlay.Schemas);
        }
    }

    private static void ApplyOperation(OpenApiDocument document, OpenApiOperation operation, OverlayOperation source)
    {
        if (!string.IsNullOrEmpty(source.Summary))
        {
            operation.Summary = source.Summary;
        }

        if (!string.IsNullOrEmpty(source.Description))
        {
            operation.Description = source.Description;
        }

        if (!string.IsNullOrEmpty(source.OperationId))
        {
            operation.OperationId = source.OperationId;
        }

        if (source.Tags is { Count: > 0 })
        {
            operation.Tags ??= new HashSet<OpenApiTagReference>();
            operation.Tags.Clear();
            foreach (var name in source.Tags)
            {
                operation.Tags.Add(new OpenApiTagReference(name, document));
            }
        }

        if (source.Parameters is not null && operation.Parameters is not null)
        {
            foreach (var sourceParameter in source.Parameters)
            {
                if (string.IsNullOrEmpty(sourceParameter.Description))
                {
                    continue;
                }

                foreach (var parameter in operation.Parameters)
                {
                    if (parameter is OpenApiParameter target
                        && string.Equals(target.Name, sourceParameter.Name, StringComparison.Ordinal))
                    {
                        target.Description = sourceParameter.Description;
                    }
                }
            }
        }

        if (source.Responses is null)
        {
            return;
        }

        operation.Responses ??= new OpenApiResponses();
        foreach (var (code, response) in source.Responses)
        {
            if (string.IsNullOrEmpty(response.Description))
            {
                continue;
            }

            if (operation.Responses.TryGetValue(code, out var existing) && existing is OpenApiResponse target)
            {
                target.Description = response.Description;
                continue;
            }

            if (!operation.Responses.ContainsKey(code))
            {
                operation.Responses[code] = new OpenApiResponse { Description = response.Description };
            }
        }
    }

    private static void ApplySchemas(
        IDictionary<string, IOpenApiSchema> schemas,
        Dictionary<string, OverlaySchema> sourceSchemas)
    {
        foreach (var (name, source) in sourceSchemas)
        {
            var targetName = SchemaAliases.TryGetValue(name, out var alias) ? alias : name;
            if (!schemas.TryGetValue(targetName, out var existing))
            {
                if (!string.IsNullOrEmpty(source.Description))
                {
                    schemas[targetName] = ToSchema(source);
                }

                continue;
            }

            if (existing is not OpenApiSchema target)
            {
                continue;
            }

            if (!string.IsNullOrEmpty(source.Description))
            {
                target.Description = source.Description;
            }

            if (source.Properties is null || target.Properties is null)
            {
                continue;
            }

            foreach (var (propertyName, sourceProperty) in source.Properties)
            {
                if (string.IsNullOrEmpty(sourceProperty.Description))
                {
                    continue;
                }

                if (target.Properties.TryGetValue(propertyName, out var property) && property is OpenApiSchema targetProperty)
                {
                    targetProperty.Description = sourceProperty.Description;
                }
            }
        }
    }

    private static OpenApiSchema ToSchema(OverlaySchema source)
    {
        var schema = new OpenApiSchema
        {
            Type = JsonSchemaType.Object,
            Description = source.Description,
            Properties = new Dictionary<string, IOpenApiSchema>()
        };
        if (source.Properties is null)
        {
            return schema;
        }

        foreach (var (name, property) in source.Properties)
        {
            schema.Properties[name] = new OpenApiSchema
            {
                Type = property.Type == "array" ? JsonSchemaType.Array : JsonSchemaType.String,
                Description = property.Description,
                Items = property.Type == "array" ? new OpenApiSchema { Type = JsonSchemaType.String } : null
            };
        }

        return schema;
    }

    private static OverlayDocument Load()
    {
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(ResourceName)
            ?? throw new InvalidOperationException("Missing embedded OpenAPI overlay " + ResourceName);
        return JsonSerializer.Deserialize<OverlayDocument>(stream, new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true
        }) ?? new OverlayDocument();
    }

    private sealed class OverlayDocument
    {
        public List<OverlayTag> Tags { get; set; } = [];
        public Dictionary<string, Dictionary<string, OverlayOperation>> Paths { get; set; } = new(StringComparer.Ordinal);
        public OverlayComponents? Components { get; set; }
        public Dictionary<string, OverlaySchema> Schemas => Components?.Schemas ?? [];
    }

    private sealed class OverlayComponents
    {
        public Dictionary<string, OverlaySchema> Schemas { get; set; } = new(StringComparer.Ordinal);
    }

    private sealed class OverlayTag
    {
        public string Name { get; set; } = "";
        public string? Description { get; set; }
    }

    private sealed class OverlayOperation
    {
        public List<string>? Tags { get; set; }
        public string? Summary { get; set; }
        public string? Description { get; set; }
        public string? OperationId { get; set; }
        public List<OverlayParameter>? Parameters { get; set; }
        public Dictionary<string, OverlayResponse>? Responses { get; set; }
    }

    private sealed class OverlayParameter
    {
        public string? Name { get; set; }
        public string? Description { get; set; }
    }

    private sealed class OverlayResponse
    {
        public string? Description { get; set; }
    }

    private sealed class OverlaySchema
    {
        public string? Description { get; set; }
        public Dictionary<string, OverlaySchemaProperty>? Properties { get; set; }
    }

    private sealed class OverlaySchemaProperty
    {
        public string? Type { get; set; }
        public string? Description { get; set; }
    }
}
