using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Persistence;

namespace KnappKiSoftMock.Services;

/// <summary>KiSoft-side inventory logic (GS §5.3.5).</summary>
public sealed class InventoryRequestLifecycleService(
    InventoryRequestStoreService store,
    AsrsStockService asrsStock,
    ReplyCallbackService callback,
    AppDbContext db)
{
    public enum Code
    {
        Ok,
        NotFound,
        WrongStatus
    }

    public sealed record Result(Code Code, string Message)
    {
        public static Result Ok(string msg) => new(Code.Ok, msg);
    }

    /// <summary>Build the intake (NEW) reply echoing the request line with processedQuantity 0.</summary>
    public InventoryRequestReply BuildIntakeReply(InventoryRequest request, string createdBy)
    {
        List<InventoryRequestReplyLine?>? lines = null;
        if (request.inventoryRequestLine is not null)
        {
            lines = [new InventoryRequestReplyLine(request.inventoryRequestLine.lineReference, 0)];
        }
        return new InventoryRequestReply(
            request.clientNumber,
            request.requestNumber,
            lines,
            createdBy,
            "NEW",
            KiSoftTime.Now(),
            request.businessCase ?? "INVENTORY",
            request.additionalProperties);
    }

    // Java: count is @Transactional.
    public Result Count(InventoryCountConfirmation c) => db.InTransaction(() => CountCore(c));

    private Result CountCore(InventoryCountConfirmation c)
    {
        var entity = store.Find(c.clientNumber, c.requestNumber);
        if (entity is null) return new Result(Code.NotFound, "Inventory request not found");
        if (entity.ProcessingStatus is "FINISHED" or "CANCELLED")
        {
            return new Result(Code.WrongStatus, "Inventory request already " + entity.ProcessingStatus);
        }

        var request = store.ReadPayload(entity);
        var replyLines = new List<InventoryRequestReplyLine?>();
        StockEntry? countedEntry = null;
        var netDelta = 0;
        if (c.lines is not null)
        {
            foreach (var line in c.lines)
            {
                if (line is null) continue;
                var counted = line.countedQuantity ?? 0;
                replyLines.Add(new InventoryRequestReplyLine(line.lineReference, counted));

                if (line.articleNumber is not null && line.packSize is not null)
                {
                    var reservationCode = CooOf(request, line.lineReference);
                    var delta = asrsStock.SetQuantity(
                        c.clientNumber, line.articleNumber, PackSizeKeys.ToKey(line.packSize),
                        reservationCode, counted);
                    countedEntry = new StockEntry(
                        line.loadUnitCode, line.slot,
                        new PackUnitKeyRef(c.clientNumber, line.articleNumber, line.packSize),
                        counted, null, null, null, null, reservationCode, null, null, null,
                        KiSoftTime.Now());
                    netDelta += delta;
                }
            }
        }

        store.UpdateStatus(c.clientNumber, c.requestNumber, "FINISHED");

        callback.SendInventoryRequestReply(new InventoryRequestReply(
            c.clientNumber, c.requestNumber, replyLines.Count == 0 ? null : replyLines,
            "SYSTEM", "FINISHED", KiSoftTime.Now(),
            request.businessCase ?? "INVENTORY",
            request.additionalProperties));

        if (netDelta != 0 && countedEntry is not null)
        {
            var lineRef = c.lines is { Count: > 0 } ? c.lines[0]?.lineReference : null;
            callback.SendStockCorrected(new StockCorrected(
                Guid.NewGuid().ToString(),
                new InventoryRequestReference(c.clientNumber, c.requestNumber, lineRef),
                null, netDelta, null, "INVENTORY", null, null, KiSoftTime.Now(),
                countedEntry));
        }

        if (countedEntry is not null)
        {
            callback.SendStockLockChanged(new StockLockChanged(
                Guid.NewGuid().ToString(),
                new StockLockRequestReference(c.clientNumber, c.requestNumber),
                null, null, ["LOCKED_FOR_VISION_CHECK"], null, "INVENTORY_DONE", null,
                KiSoftTime.Now(), countedEntry));
        }

        return Result.Ok("Inventory counted, PostInventoryRequestReply(FINISHED) sent");
    }

    private static string CooOf(InventoryRequest request, string? lineReference)
    {
        if (request.inventoryRequestLine is null
            || request.inventoryRequestLine.lineReference != lineReference)
        {
            return "";
        }
        return ReservationCodes.Normalize(request.inventoryRequestLine.reservationCode);
    }
}
