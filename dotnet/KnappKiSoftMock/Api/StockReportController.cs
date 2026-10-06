using System.Globalization;
using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Persistence;
using KnappKiSoftMock.Services;
using Microsoft.AspNetCore.Mvc;

namespace KnappKiSoftMock.Api;

/// <summary>
/// Mock KiSoft Stock Report API — Stock Management (HIS Appendix §8.2, §8.3),
/// plus mock-only OData GET for ASRS inventory items.
/// </summary>
[ApiController]
[Route("oneapi/v1")]
public sealed class StockReportController(AsrsStockService asrsStock, ReplyCallbackService replyCallbackService)
    : ControllerBase
{
    [HttpGet("inventoryItem")]
    public ODataCollectionResponse<InventoryItem> GetInventoryItems(
        [FromQuery(Name = "$filter")] string? filter,
        [FromQuery(Name = "$top")] string? top,
        [FromQuery(Name = "$skip")] string? skip,
        [FromQuery(Name = "$count")] string? count)
    {
        // Java filters in SQL (equalitySpecification on the entity columns): a NULL column never matches,
        // integer columns are compared as their string form.
        var all = asrsStock.ListAll();
        return ODataQuerySupport.BuildPage(
            ODataQuerySupport.MetadataContext(Request.PathBase.Value, "InventoryItems"),
            all,
            ODataQuerySupport.ParseFilter(filter),
            s => ODataQuerySupport.ColumnFields(
                "packUnit.clientNumber", s.ClientNumber,
                "clientNumber", s.ClientNumber,
                "packUnit.articleNumber", s.ArticleNumber,
                "articleNumber", s.ArticleNumber,
                "packUnit.packSize", s.PackSize,
                "packSize", s.PackSize,
                "quantity", s.Quantity.ToString(),
                "reservationCode", s.ReservationCode,
                "stockType", s.StockType),
            ToInventoryItem,
            ODataQuerySupport.ParseTop(top),
            ODataQuerySupport.ParseSkip(skip),
            ODataQuerySupport.ParseCount(count));
    }

    [HttpPost("requestInventoryReport")]
    public OneApiOkResponse RequestInventoryReport([FromBody, Valid] RequestInventoryReport request)
    {
        var stockInventory = asrsStock.ListAll()
            .Where(s => Match(request.clientNumber, s.ClientNumber))
            .Where(s => Match(request.articleNumber, s.ArticleNumber))
            .Where(s => MatchPackSize(request.packSize, s.PackSize))
            .Select(ToStockInventory)
            .Cast<StockInventory?>()
            .ToList();
        var report = new InventoryReport(
            request.requestNumber,
            stockInventory.Count == 0 ? null : stockInventory);
        replyCallbackService.SendInventoryReport(report);
        return new OneApiOkResponse(200, "OK", "Inventory report requested; PostInventoryReport will follow");
    }

    [HttpPost("requestStorageCapacityReport")]
    public OneApiOkResponse RequestStorageCapacityReport([FromBody, Valid] RequestStorageCapacityReport request)
    {
        var area = request.storageArea ?? "DEFAULT";
        var detail = new StorageCapacityDetail(
            area, 1000, 250, 750, 250, null, null, null, null, null);
        var report = new StorageCapacityReport(
            request.requestNumber,
            KiSoftTime.Now(),
            [detail]);
        replyCallbackService.SendStorageCapacityReport(report);
        return new OneApiOkResponse(200, "OK", "Storage capacity report requested; PostStorageCapacityReport will follow");
    }

    private static bool Match(string? filter, string? value) =>
        string.IsNullOrWhiteSpace(filter) || filter == value;

    private static bool MatchPackSize(int? filter, string? stored) =>
        filter is null || PackSizeKeys.ToKey(filter) == stored;

    /// <summary>Java <c>Integer.valueOf(String)</c>: optional sign, digits only, otherwise NumberFormatException (500).</summary>
    private static int? ParsePackSize(string? packSize) =>
        packSize is null
            ? null
            : int.Parse(packSize, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture);

    private static InventoryItem ToInventoryItem(AsrsStockEntity s)
    {
        var packSize = ParsePackSize(s.PackSize);
        return new InventoryItem(
            new PackUnitKeyRef(s.ClientNumber, s.ArticleNumber, packSize),
            s.Quantity,
            s.StockType,
            s.LotNumber,
            s.DateMark,
            s.SerialNumber,
            s.ReservationCode,
            AsrsStockService.ReadLockReasons(s)?.Cast<string?>().ToList());
    }

    private static StockInventory ToStockInventory(AsrsStockEntity s)
    {
        var packSize = ParsePackSize(s.PackSize);
        return new StockInventory(
            new PackUnitKeyRef(s.ClientNumber, s.ArticleNumber, packSize),
            s.Quantity,
            s.StockType,
            s.LotNumber,
            s.DateMark,
            s.SerialNumber,
            s.ReservationCode,
            AsrsStockService.ReadLockReasons(s)?.Cast<string?>().ToList());
    }
}
