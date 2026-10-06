using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Options;
using KnappKiSoftMock.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace KnappKiSoftMock.Api;

/// <summary>
/// Swagger-accessible triggers for KiSoft One → HOST callbacks.
/// Use wait=true (default) to include the APIC response in the HTTP reply.
/// </summary>
[ApiController]
[Route("oneapi/v1/_webhooks")]
public sealed class WebhooksController(
    IOptions<MockOptions> options,
    ReplyCallbackService callbacks) : ControllerBase
{
    private readonly MockOptions _options = options.Value;

    private IActionResult Dispatch(
        string pathKey,
        object payload,
        string messageName,
        bool wait,
        Action asyncSend)
    {
        // Always route through ReplyCallbackService so the payload is logged (also when disabled).
        if (!_options.AreCallbacksEnabled)
        {
            if (wait) callbacks.DeliverSync(pathKey, payload, messageName);
            else asyncSend();
            return StatusCode(503, new OneApiOkResponse(
                503,
                "UNAVAILABLE",
                "Outgoing callbacks disabled — set knapp.mock.reply-callback-enabled=true "
                + "and knapp.mock.reply-callback-url"));
        }

        var target = _options.WebhookTargetUrl(pathKey);
        if (wait)
        {
            var callback = callbacks.DeliverSync(pathKey, payload, messageName);
            return WebhookWait("Callback delivered to POST " + target, messageName, callback);
        }

        asyncSend();
        return Accepted(new OneApiOkResponse(
            202,
            "ACCEPTED",
            "Callback dispatched to POST " + target));
    }

    private IActionResult WebhookWait(string successMessage, string messageName, CallbackDeliveryResult? callback)
    {
        if (callback is null)
        {
            return StatusCode(503, new OneApiOkResponse(
                503,
                "UNAVAILABLE",
                "Outgoing callback could not be sent — check reply-callback-url configuration"));
        }

        if (callback.Delivered)
        {
            return Ok(new OneApiOkResponse(200, "OK", successMessage, callback));
        }

        var httpStatus = callback.CallbackHttpStatus ?? 502;
        return StatusCode(httpStatus, new OneApiOkResponse(
            httpStatus,
            "CALLBACK_FAILED",
            callback.LogLine(messageName),
            callback));
    }

    [HttpPost("inboundDeliveryReply")]
    [Consumes("application/json")]
    public IActionResult SendInboundDeliveryReply(
        [FromBody] InboundDeliveryReply body,
        [FromQuery] bool wait = true) =>
        Dispatch("inboundDeliveryReply", body, "InboundDeliveryReply", wait,
            () => callbacks.SendInboundDeliveryReply(body));

    [HttpPost("goodsOutOrderReply")]
    [Consumes("application/json")]
    public IActionResult SendGoodsOutOrderReply(
        [FromBody] GoodsOutOrderReply body,
        [FromQuery] bool wait = true) =>
        Dispatch("goodsOutOrderReply", body, "GoodsOutOrderReply", wait,
            () => callbacks.SendGoodsOutOrderReply(body));

    [HttpPost("inventoryRequestReply")]
    [Consumes("application/json")]
    public IActionResult SendInventoryRequestReply(
        [FromBody] InventoryRequestReply body,
        [FromQuery] bool wait = true) =>
        Dispatch("inventoryRequestReply", body, "InventoryRequestReply", wait,
            () => callbacks.SendInventoryRequestReply(body));

    [HttpPost("loadUnitMoved")]
    [Consumes("application/json")]
    public IActionResult SendLoadUnitMoved(
        [FromBody] LoadUnitMoved body,
        [FromQuery] bool wait = true) =>
        Dispatch("loadUnitMoved", body, "LoadUnitMoved", wait,
            () => callbacks.SendLoadUnitMoved(body));

    [HttpPost("stockReceived")]
    [Consumes("application/json")]
    public IActionResult SendStockReceived(
        [FromBody] StockReceived body,
        [FromQuery] bool wait = true) =>
        Dispatch("stockReceived", body, "StockReceived", wait,
            () => callbacks.SendStockReceived(body));

    [HttpPost("stockCorrected")]
    [Consumes("application/json")]
    public IActionResult SendStockCorrected(
        [FromBody] StockCorrected body,
        [FromQuery] bool wait = true) =>
        Dispatch("stockCorrected", body, "StockCorrected", wait,
            () => callbacks.SendStockCorrected(body));

    [HttpPost("stockLockChanged")]
    [Consumes("application/json")]
    public IActionResult SendStockLockChanged(
        [FromBody] StockLockChanged body,
        [FromQuery] bool wait = true) =>
        Dispatch("stockLockChanged", body, "StockLockChanged", wait,
            () => callbacks.SendStockLockChanged(body));

    [HttpPost("inventoryReport")]
    [Consumes("application/json")]
    public IActionResult SendInventoryReport(
        [FromBody] InventoryReport body,
        [FromQuery] bool wait = true) =>
        Dispatch("inventoryReport", body, "InventoryReport", wait,
            () => callbacks.SendInventoryReport(body));

    [HttpPost("storageOrderReply")]
    [Consumes("application/json")]
    public IActionResult SendStorageOrderReply(
        [FromBody] StorageOrderReply body,
        [FromQuery] bool wait = true) =>
        Dispatch("storageOrderReply", body, "StorageOrderReply", wait,
            () => callbacks.SendStorageOrderReply(body));

    [HttpPost("storageCapacityReport")]
    [Consumes("application/json")]
    public IActionResult SendStorageCapacityReport(
        [FromBody] StorageCapacityReport body,
        [FromQuery] bool wait = true) =>
        Dispatch("storageCapacityReport", body, "StorageCapacityReport", wait,
            () => callbacks.SendStorageCapacityReport(body));
}
