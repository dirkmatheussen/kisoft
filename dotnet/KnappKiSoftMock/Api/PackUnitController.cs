using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Services;
using Microsoft.AspNetCore.Mvc;

namespace KnappKiSoftMock.Api;

[ApiController]
[Route("oneapi/v1")]
public sealed class PackUnitController(PackUnitStoreService packUnits, AsrsStockService stock) : ControllerBase
{
    public const int MaxBatchSize = 10_000;

    [HttpGet("packUnit")]
    public ODataCollectionResponse<PackUnitFull> GetPackUnits(
        [FromQuery(Name = "$filter")] string? filter,
        [FromQuery(Name = "$top")] string? top,
        [FromQuery(Name = "$skip")] string? skip,
        [FromQuery(Name = "$count")] string? count) =>
        ODataQuerySupport.BuildPage(
            ODataQuerySupport.MetadataContext(Request.PathBase.Value, "PackUnits"),
            packUnits.ListAll(),
            ODataQuerySupport.ParseFilter(filter),
            pu => ODataQuerySupport.Fields(
                "clientNumber", ODataQuerySupport.Str(pu.article?.clientNumber),
                "articleNumber", ODataQuerySupport.Str(pu.article?.articleNumber),
                "packSize", ODataQuerySupport.Str(pu.packSize)),
            ODataQuerySupport.ParseTop(top),
            ODataQuerySupport.ParseSkip(skip),
            ODataQuerySupport.ParseCount(count));

    [HttpPost("packUnit/updateSession")]
    public OneApiOkResponse PostUpdateSession([FromBody, Valid] MasterDataUpdateSession request)
    {
        var clientNumber = request.clientNumber ?? "DEFAULT";
        if (request.transmissionTag == "SET") packUnits.StartUpdateSession(clientNumber);
        else if (request.transmissionTag == "CLEANUP") packUnits.CleanupUpdateSession(clientNumber);
        var verb = request.transmissionTag == "SET" ? "opened" : "closed";
        return new OneApiOkResponse(200, "OK", $"Update session {verb} successfully");
    }

    [HttpPut("packUnit")]
    public IActionResult PutPackUnits([FromBody] List<PackUnitFull> body)
    {
        if (body is { Count: > MaxBatchSize })
        {
            return BadRequest(new OneApiErrorResponse(null, null, null, null, null, null,
                $"Batch size {body.Count} exceeds maximum {MaxBatchSize}", ["E-AKO-GENR-0002"]));
        }
        if (body is null || body.Count == 0)
        {
            return Ok(new OneApiOkResponse(200, "OK", "Empty batch ignored"));
        }

        var seen = new HashSet<string>();
        var valid = new List<PackUnitFull>();
        var errors = new List<OneApiErrorResponse>();
        foreach (var unit in body)
        {
            var validation = Validate(unit);
            if (validation is not null)
            {
                errors.Add(validation);
                continue;
            }
            var client = unit.article!.clientNumber ?? "DEFAULT";
            var key = PackUnitStoreService.Key(client, unit.article.articleNumber, unit.packSize);
            if (!seen.Add(key))
            {
                errors.Add(new OneApiErrorResponse(client, null, null, unit.article.articleNumber,
                    unit.packSize?.ToString(), null, "Duplicate pack unit in same batch", ["E-AKO-MAST-0006"]));
            }
            else valid.Add(unit);
        }

        if (errors.Count > 0)
        {
            if (valid.Count > 0)
            {
                packUnits.UpsertAll(valid);
                return StatusCode(207, errors);
            }
            return BadRequest(errors.Count == 1 ? errors[0] : errors);
        }

        packUnits.UpsertAll(body);
        return Ok(new OneApiOkResponse(200, "OK", "Pack units created/updated successfully"));
    }

    [HttpDelete("packUnit")]
    public IActionResult DeletePackUnits([FromBody] List<PackUnitKeyRef>? refs)
    {
        if (refs is null || refs.Count == 0)
        {
            var blocked = new List<OneApiErrorResponse>();
            foreach (var unit in packUnits.ListAll())
            {
                var client = unit.article?.clientNumber ?? "DEFAULT";
                var article = unit.article?.articleNumber;
                if (stock.HasStock(client, article, PackSizeKeys.ToKey(unit.packSize)))
                {
                    blocked.Add(StockGuard(client, article, PackSizeKeys.ToKey(unit.packSize)));
                }
                else packUnits.DeleteOne(client, article, unit.packSize);
            }
            return blocked.Count > 0
                ? StatusCode(207, blocked)
                : Ok(new OneApiOkResponse(200, "OK", "All pack units deleted"));
        }

        var errors = new List<OneApiErrorResponse>();
        var toDelete = new List<PackUnitKeyRef>();
        foreach (var reference in refs)
        {
            var client = reference.clientNumber ?? "DEFAULT";
            if (stock.HasStock(client, reference.articleNumber, PackSizeKeys.ToKey(reference.packSize)))
            {
                errors.Add(StockGuard(client, reference.articleNumber, PackSizeKeys.ToKey(reference.packSize)));
            }
            else toDelete.Add(reference);
        }
        foreach (var reference in toDelete)
        {
            // Java passes the raw clientNumber here (null → matches no row), unlike the stock guard above.
            packUnits.DeleteOne(reference.clientNumber, reference.articleNumber, reference.packSize);
        }
        return errors.Count > 0
            ? StatusCode(207, errors)
            : Ok(new OneApiOkResponse(200, "OK", "Pack units deleted"));
    }

    private static OneApiErrorResponse? Validate(PackUnitFull? unit)
    {
        if (unit is null) return Format(null, null, null, "Pack unit entry is missing");
        if (string.IsNullOrWhiteSpace(unit.article?.articleNumber)) return Format(unit, "article.articleNumber is required");
        if (unit.packSize is null) return Format(unit, "packSize is required");
        if (unit.capacityInformation is null || unit.capacityInformation.Count == 0)
        {
            return Format(unit, "capacityInformation is required");
        }
        return null;
    }

    private static OneApiErrorResponse Format(PackUnitFull unit, string message)
    {
        // Java formatError dereferences u.article() unconditionally: a pack unit without "article"
        // raises a NullPointerException → HTTP 500 (Spring error body) and nothing is upserted.
        var article = unit.article ?? throw new NullReferenceException(
            "Cannot invoke \"Article.clientNumber()\" because the return value of \"PackUnitFull.article()\" is null");
        return Format(article.clientNumber, article.articleNumber, unit.packSize?.ToString(), message);
    }

    private static OneApiErrorResponse Format(string? clientNumber, string? articleNumber, string? packSize, string message) =>
        new(clientNumber, null, null, articleNumber, packSize, null, message, ["E-AKO-GENR-0002"]);

    private static OneApiErrorResponse StockGuard(string? client, string? articleNumber, string? packSize) =>
        new(client, null, null, articleNumber, packSize, null,
            "Cannot delete: ASRS still holds inventory for this part (MA-01 E1)", ["E-AKO-STOC-0002"]);
}
