using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Services;
using Microsoft.AspNetCore.Mvc;

namespace KnappKiSoftMock.Api;

/// <summary>
/// Mock KiSoft Goods-Out Order API — Goods-Out (HIS Appendix §7.4, GS §5.2.1).
/// </summary>
[ApiController]
[Route("oneapi/v1")]
public sealed class GoodsOutOrderController(
    GoodsOutOrderStoreService store,
    GoodsOutOrderLifecycleService lifecycle,
    ReplyCallbackService replyCallbackService) : ControllerBase
{
    [HttpGet("goodsOutOrder")]
    public ODataCollectionResponse<GoodsOutOrderRead> GetGoodsOutOrders(
        [FromQuery(Name = "$filter")] string? filter,
        [FromQuery(Name = "$top")] string? top,
        [FromQuery(Name = "$skip")] string? skip,
        [FromQuery(Name = "$count")] string? count)
    {
        // Java filters on the entity columns in SQL (equalitySpecification) and maps the page afterwards.
        return ODataQuerySupport.BuildPage(
            ODataQuerySupport.MetadataContext(Request.PathBase.Value, "GoodsOutOrders"),
            store.ListAllEntities(),
            ODataQuerySupport.ParseFilter(filter),
            e => ODataQuerySupport.ColumnFields(
                "clientNumber", e.ClientNumber,
                "orderNumber", e.OrderNumber,
                "sheetNumber", e.SheetNumber,
                "processingStatus", e.ProcessingStatus),
            e => new GoodsOutOrderRead(e.ProcessingStatus, store.ReadPayload(e)),
            ODataQuerySupport.ParseTop(top),
            ODataQuerySupport.ParseSkip(skip),
            ODataQuerySupport.ParseCount(count));
    }

    [HttpPost("goodsOutOrder")]
    public IActionResult PostGoodsOutOrder([FromBody, Valid] GoodsOutOrder request)
    {
        if (store.Exists(request.clientNumber, request.orderNumber, request.sheetNumber))
        {
            return StatusCode(400, OrderKeyError(request, ["E-AKO-MOVM-0002"]));
        }

        var lineErrors = lifecycle.ValidateIntakeLines(request.clientNumber, request.goodsOutOrderLines);
        if (lineErrors.Count > 0)
        {
            return StatusCode(400, LineError(request, lineErrors));
        }

        store.CreateNew(request);
        replyCallbackService.SendGoodsOutOrderReply(lifecycle.BuildIntakeReply(request, "HOST"));
        return Ok(new OneApiOkResponse(200, "OK", "Goods-out order accepted"));
    }

    [HttpPatch("goodsOutOrder")]
    public IActionResult PatchGoodsOutOrder([FromBody, Valid] UpdateGoodsOutOrder request)
    {
        var existing = store.Find(request.clientNumber, request.orderNumber, request.sheetNumber);
        if (existing is null)
        {
            return StatusCode(400, OrderKeyError(request, ["E-AKO-MOVM-0003"]));
        }
        if (existing.ProcessingStatus != "NEW")
        {
            return StatusCode(409, OrderKeyError(request, ["E-AKO-MOVM-0005"]));
        }

        if (request.addGoodsOutOrderLines is not null)
        {
            var lineErrors = lifecycle.ValidateIntakeLines(request.clientNumber, request.addGoodsOutOrderLines);
            if (lineErrors.Count > 0)
            {
                return StatusCode(400, LineError(
                    request.clientNumber, request.orderNumber, request.sheetNumber, lineErrors));
            }
        }

        var current = store.ReadPayload(existing);
        var priority = request.priority ?? current.priority ?? 1;
        var lines = new List<GoodsOutOrderLine?>(current.goodsOutOrderLines ?? []);
        if (request.addGoodsOutOrderLines is not null)
        {
            lines.AddRange(request.addGoodsOutOrderLines);
        }
        if (request.deleteLinesByReference is not null)
        {
            lines.RemoveAll(l => l is not null && request.deleteLinesByReference.Contains(l.lineReference));
        }

        var updated = new GoodsOutOrder(
            current.clientNumber, current.orderNumber, current.sheetNumber, current.loadCarrier,
            priority, current.businessCase, current.startStationName, current.loadUnitCode,
            current.departureTime, current.departureDate, current.customerNumber, current.routeNumber,
            current.areaWeights, current.dispatchRampNumbers,
            request.vasTasks ?? current.vasTasks,
            request.additionalProperties ?? current.additionalProperties,
            current.controlFlags, current.transportTargets, current.printDocuments,
            lines.Count == 0 ? null : lines);
        store.Update(existing, updated);
        return Ok(new OneApiOkResponse(200, "OK", "Goods-out order updated"));
    }

    [HttpDelete("goodsOutOrder")]
    public IActionResult DeleteGoodsOutOrder([FromBody, Valid] GoodsOutOrderRef request)
    {
        var existing = store.Find(request.clientNumber, request.orderNumber, request.sheetNumber);
        if (existing is null)
        {
            return StatusCode(400, OrderKeyError(request, ["E-AKO-MOVM-0003"]));
        }
        if (existing.ProcessingStatus != "NEW")
        {
            return StatusCode(409, OrderKeyError(request, ["E-AKO-MOVM-0005"]));
        }

        var order = store.ReadPayload(existing);
        store.Delete(request.clientNumber, request.orderNumber, request.sheetNumber);

        var reply = new GoodsOutOrderReply(
            order.clientNumber, order.orderNumber, order.sheetNumber, "KISOFT", "CANCELLED",
            order.businessCase ?? "GOODS_OUT",
            KiSoftTime.Now(), order.loadUnitCode, order.loadCarrier,
            order.customerNumber, null, null);
        replyCallbackService.SendGoodsOutOrderReply(reply);
        return Ok(new OneApiOkResponse(200, "OK", "Goods-out order cancelled"));
    }

    private static GoodsOutOrderLineErrorResponse LineError(GoodsOutOrder request, List<LineCodeError> lineCodes) =>
        LineError(request.clientNumber, request.orderNumber, request.sheetNumber, lineCodes);

    private static GoodsOutOrderLineErrorResponse LineError(
        string? clientNumber, string? orderNumber, int? sheetNumber, List<LineCodeError> lineCodes)
    {
        var codes = new List<string?>();
        foreach (var lineCode in lineCodes)
        {
            if (lineCode.lineCode is not null && !codes.Contains(lineCode.lineCode))
            {
                codes.Add(lineCode.lineCode);
            }
        }
        return new GoodsOutOrderLineErrorResponse(
            clientNumber, orderNumber, sheetNumber, codes, lineCodes.ConvertAll(c => (LineCodeError?)c));
    }

    private static GoodsOutOrderLineErrorResponse OrderKeyError(GoodsOutOrder request, List<string?> codes) =>
        new(request.clientNumber, request.orderNumber, request.sheetNumber, codes, null);

    private static GoodsOutOrderLineErrorResponse OrderKeyError(GoodsOutOrderRef request, List<string?> codes) =>
        new(request.clientNumber, request.orderNumber, request.sheetNumber, codes, null);

    private static GoodsOutOrderLineErrorResponse OrderKeyError(UpdateGoodsOutOrder request, List<string?> codes) =>
        new(request.clientNumber, request.orderNumber, request.sheetNumber, codes, null);
}
