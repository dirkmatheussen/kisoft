using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Services;
using Microsoft.AspNetCore.Mvc;

namespace KnappKiSoftMock.Api;

/// <summary>
/// Mock-only operator endpoint for spontaneous stock corrections (spec 007 / IN-02).
/// </summary>
[ApiController]
[Route("oneapi/v1/stock/operator")]
public sealed class StockOperatorController(AsrsStockService asrsStock, ReplyCallbackService callback) : ControllerBase
{
    private const string CodeStockNotFound = "E-AKO-STOC-0003";
    private const string CodeFormatError = "E-AKO-GENR-0002";

    [HttpPost("correct")]
    public IActionResult Correct(
        [FromBody, Valid] SpontaneousStockCorrection request,
        [FromQuery] bool wait = true)
    {
        var delta = asrsStock.SetQuantity(
            request.clientNumber, request.articleNumber, PackSizeKeys.ToKey(request.packSize),
            request.reservationCode, request.countedQuantity ?? 0);
        var entry = new StockEntry(
            null, null,
            new PackUnitKeyRef(request.clientNumber, request.articleNumber, request.packSize),
            request.countedQuantity, null, null, null, null,
            request.reservationCode, null, null, null,
            KiSoftTime.Now());
        var stockEvent = new StockCorrected(
            Guid.NewGuid().ToString(),
            null, null, delta,
            request.stationName,
            request.reason ?? "SPONTANEOUS_CORRECTION",
            null, null, KiSoftTime.Now(),
            entry);

        if (wait)
        {
            var delivery = callback.DeliverSync("stockCorrected", stockEvent, "StockCorrected");
            return WebhookWait(delivery, "Spontaneous stock correction applied", "StockCorrected");
        }

        callback.SendStockCorrected(stockEvent);
        return Ok(new OneApiOkResponse(200, "OK", "Spontaneous stock correction applied"));
    }

    [HttpPost("lock")]
    public IActionResult Lock(
        [FromBody, Valid] StockLockOperatorRequest request,
        [FromQuery] bool wait = true)
    {
        var reasons = request.StockLockReasons ?? [];
        var invalid = StockLockReasons.Invalid(reasons);
        if (invalid.Count > 0)
        {
            // Java: List.toString() → "[A, B]"
            return Error(400, request, CodeFormatError,
                "Unknown stockLockReasons " + FormatList(invalid)
                + "; allowed: " + FormatList(StockLockReasons.All));
        }

        if (request.Action == LockAction.LOCK && reasons.Count == 0)
        {
            return Error(400, request, CodeFormatError, "LOCK requires at least one stockLockReasons entry");
        }

        var packSizeKey = PackSizeKeys.ToKey(request.PackSize);
        var change = asrsStock.ChangeLocks(
            request.ClientNumber, request.ArticleNumber, packSizeKey,
            request.ReservationCode, request.Action ?? LockAction.LOCK, reasons);
        if (change is null)
        {
            return Error(404, request, CodeStockNotFound,
                "No ASRS stock for " + request.ArticleNumber + " / packSize " + (request.PackSize?.ToString() ?? "null")
                + " with reservationCode " + (request.ReservationCode ?? "null"));
        }

        var row = change.Entity;
        var isLock = request.Action == LockAction.LOCK;
        var processedStock = new StockEntry(
            null, null,
            new PackUnitKeyRef(row.ClientNumber, row.ArticleNumber, request.PackSize),
            row.Quantity, row.StockType, row.LotNumber, row.DateMark, row.SerialNumber,
            row.ReservationCode,
            change.ResultingReasons?.Cast<string?>().ToList(),
            null, null, null);
        var lockEvent = new StockLockChanged(
            Guid.NewGuid().ToString(),
            null,
            row.Quantity,
            isLock ? change.Added.Cast<string?>().ToList() : null,
            isLock ? null : change.Removed.Cast<string?>().ToList(),
            request.StationName,
            request.Reason ?? (isLock ? "OPERATOR_LOCK" : "OPERATOR_UNLOCK"),
            null,
            KiSoftTime.Now(),
            processedStock);

        var message = (isLock
                ? "Stock lock added " + FormatList(change.Added)
                : "Stock lock removed " + FormatList(change.Removed))
            + "; current locks "
            + (change.ResultingReasons is null ? "[]" : FormatList(change.ResultingReasons));

        if (wait)
        {
            var delivery = callback.DeliverSync("stockLockChanged", lockEvent, "StockLockChanged");
            return WebhookWait(delivery, message, "StockLockChanged");
        }

        callback.SendStockLockChanged(lockEvent);
        return Ok(new OneApiOkResponse(200, "OK", message));
    }

    private static string FormatList(IEnumerable<string> list) =>
        "[" + string.Join(", ", list) + "]";

    private IActionResult Error(int status, StockLockOperatorRequest r, string code, string message) =>
        StatusCode(status, new OneApiErrorResponse(
            r.ClientNumber, null, null, r.ArticleNumber,
            r.PackSize?.ToString(),
            null, message, [code]));

    private IActionResult WebhookWait(CallbackDeliveryResult? callback, string successMessage, string messageName)
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
}
