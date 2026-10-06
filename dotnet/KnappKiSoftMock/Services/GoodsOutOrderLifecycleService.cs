using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Persistence;

namespace KnappKiSoftMock.Services;

/// <summary>
/// KiSoft-side goods-out order logic (GS §5.2.1, §5.2.3–§5.2.5), excluding multiphase picking (§5.2.2).
/// </summary>
public sealed class GoodsOutOrderLifecycleService(
    GoodsOutOrderStoreService store,
    PackUnitStoreService packUnitStore,
    AsrsStockService asrsStock,
    ReplyCallbackService callback,
    AppDbContext db)
{
    public const string StatusNew = "NEW";
    public const string StatusStarted = "STARTED";
    public const string StatusProcessed = "PROCESSED";
    public const string StatusFinished = "FINISHED";

    private const string FeatureLocked = "ARTICLE_IS_LOCKED";
    private const string FeatureTakenOffSale = "TAKEN_OFF_SALE";

    public const string CodeFormatError = "E-AKO-GENR-0002";
    public const string CodeGeneralError = "E-AKO-GENR-0001";
    public const string CodeUnknownArticle = "E-AKO-MAST-0001";
    public const string CodeNotEnoughStock = "E-AKO-STOC-0001";

    public enum Code
    {
        Ok,
        NotFound,
        WrongStatus
    }

    public sealed record Result(Code Code, string Message)
    {
        public static Result Ok(string message) => new(Code.Ok, message);
    }

    // ---- Intake (PostGoodsOutOrder, GS §5.2.1) -------------------------------------------------

    public GoodsOutOrderReply BuildIntakeReply(GoodsOutOrder order, string createdBy)
    {
        var lines = new List<GoodsOutOrderReplyLine?>();
        if (order.goodsOutOrderLines is not null)
        {
            foreach (var line in order.goodsOutOrderLines)
            {
                if (line is null) continue;
                lines.Add(ReplyLine(order, line, Guid.NewGuid().ToString(), 0, "UNTOUCHED", null, null));
            }
        }
        return Reply(order, createdBy, StatusNew, lines.Count == 0 ? null : lines);
    }

    public List<LineCodeError> ValidateIntakeLines(string? clientNumber, List<GoodsOutOrderLine?>? lines)
    {
        if (lines is null) return [];
        var errors = new List<LineCodeError>();
        foreach (var line in lines)
        {
            if (line is null) continue;
            var code = IntakeLineCode(clientNumber, line);
            if (code is not null)
            {
                errors.Add(new LineCodeError(line.lineReference, code));
            }
        }
        return errors;
    }

    private string? IntakeLineCode(string? clientNumber, GoodsOutOrderLine line)
    {
        if (line.requestedQuantity is null or <= 0) return CodeFormatError;
        if (!MasterDataExists(clientNumber, line)) return CodeUnknownArticle;

        var master = packUnitStore.FindAnyByArticle(clientNumber, line.articleNumber);
        if (master?.articleFeatures is not null)
        {
            var features = master.articleFeatures;
            if (features.Contains(FeatureLocked) || features.Contains(FeatureTakenOffSale))
            {
                return CodeGeneralError;
            }
        }

        if (AvailableStock(clientNumber, line) < line.requestedQuantity.Value)
        {
            return CodeNotEnoughStock;
        }
        return null;
    }

    private int AvailableStock(string? clientNumber, GoodsOutOrderLine line)
    {
        if (line.packSize is not null)
        {
            return asrsStock.GetQuantity(
                clientNumber, line.articleNumber, PackSizeKeys.ToKey(line.packSize), line.reservationCode);
        }
        return asrsStock.AvailableForArticle(
            clientNumber, line.articleNumber, ReservationCodes.Normalize(line.reservationCode));
    }

    /// <summary>
    /// Runtime pick validation: returns a processingResult when the line cannot be picked, or null when pickable.
    /// </summary>
    public string? ValidateLine(string? clientNumber, GoodsOutOrderLine line)
    {
        if (line.requestedQuantity is null or <= 0) return "ZERO_REQUESTED";

        var master = packUnitStore.FindAnyByArticle(clientNumber, line.articleNumber);
        if (master?.articleFeatures is not null)
        {
            var features = master.articleFeatures;
            if (features.Contains(FeatureLocked)) return "ARTICLE_IS_BLOCKED";
            if (features.Contains(FeatureTakenOffSale)) return "TAKEN_OFF_SALE";
        }

        if (AvailableStock(clientNumber, line) < line.requestedQuantity.Value) return "OUT_OF_STOCK";
        return null;
    }

    public bool MasterDataExists(string? clientNumber, GoodsOutOrderLine line)
    {
        if (line.packSize is not null)
        {
            return packUnitStore.Exists(clientNumber, line.articleNumber, line.packSize);
        }
        return packUnitStore.ExistsArticle(clientNumber, line.articleNumber);
    }

    // ---- Lifecycle -----------------------------------------------------------------------------

    // Java: startProcessing / confirmPicking / finalCheck are @Transactional.
    public Result StartProcessing(string? clientNumber, string? orderNumber, int? sheetNumber) =>
        db.InTransaction(() => StartProcessingCore(clientNumber, orderNumber, sheetNumber));
    public Result ConfirmPicking(GoodsOutPickConfirmation c) => db.InTransaction(() => ConfirmPickingCore(c));
    public Result FinalCheck(string? clientNumber, string? orderNumber, int? sheetNumber) =>
        db.InTransaction(() => FinalCheckCore(clientNumber, orderNumber, sheetNumber));

    private Result StartProcessingCore(string? clientNumber, string? orderNumber, int? sheetNumber)
    {
        var entity = store.Find(clientNumber, orderNumber, sheetNumber);
        if (entity is null) return new Result(Code.NotFound, "Goods-out order not found");
        if (entity.ProcessingStatus != StatusNew)
        {
            return new Result(Code.WrongStatus, "Order is not NEW (current: " + entity.ProcessingStatus + ")");
        }

        var order = store.ReadPayload(entity);
        store.UpdateStatus(clientNumber, orderNumber, sheetNumber, StatusStarted);
        callback.SendGoodsOutOrderReply(Reply(order, "SYSTEM", StatusStarted, UntouchedLines(order)));
        return Result.Ok("Order started, PostGoodsOutOrderReply(STARTED) sent");
    }

    private Result ConfirmPickingCore(GoodsOutPickConfirmation c)
    {
        var entity = store.Find(c.clientNumber, c.orderNumber, c.sheetNumber);
        if (entity is null) return new Result(Code.NotFound, "Goods-out order not found");
        if (entity.ProcessingStatus != StatusStarted)
        {
            return new Result(Code.WrongStatus, "Order is not STARTED (current: " + entity.ProcessingStatus + ")");
        }

        var order = store.ReadPayload(entity);
        var replyLines = new List<GoodsOutOrderReplyLine>();
        if (order.goodsOutOrderLines is not null)
        {
            foreach (var line in order.goodsOutOrderLines)
            {
                if (line is null) continue;
                replyLines.Add(PickLine(order, line, FindPick(c, line.lineReference)));
            }
        }

        store.SavePickResult(entity, replyLines);
        store.UpdateStatus(c.clientNumber, c.orderNumber, c.sheetNumber, StatusProcessed);
        List<GoodsOutOrderReplyLine?>? callbackLines = replyLines.Count == 0
            ? null
            : replyLines.ConvertAll(l => (GoodsOutOrderReplyLine?)l);
        callback.SendGoodsOutOrderReply(Reply(order, "SYSTEM", StatusProcessed, callbackLines));
        return Result.Ok("Picking confirmed, PostGoodsOutOrderReply(PROCESSED) sent");
    }

    private Result FinalCheckCore(string? clientNumber, string? orderNumber, int? sheetNumber)
    {
        var entity = store.Find(clientNumber, orderNumber, sheetNumber);
        if (entity is null) return new Result(Code.NotFound, "Goods-out order not found");
        if (entity.ProcessingStatus != StatusProcessed)
        {
            return new Result(Code.WrongStatus, "Order is not PROCESSED (current: " + entity.ProcessingStatus + ")");
        }

        var order = store.ReadPayload(entity);
        store.UpdateStatus(clientNumber, orderNumber, sheetNumber, StatusFinished);
        callback.SendGoodsOutOrderReply(Reply(order, "SYSTEM", StatusFinished, FinishedLines(order, store.ReadPickResult(entity))));
        return Result.Ok("Final check passed, PostGoodsOutOrderReply(FINISHED) sent");
    }

    // ---- Picking of a single line --------------------------------------------------------------

    private GoodsOutOrderReplyLine PickLine(GoodsOutOrder order, GoodsOutOrderLine line, GoodsOutPickLine? pick)
    {
        var uuid = Guid.NewGuid().ToString();
        var validation = ValidateLine(order.clientNumber, line);
        if (validation is not null)
        {
            return ReplyLine(order, line, uuid, 0, validation, null, null);
        }

        var requested = line.requestedQuantity!.Value;
        var picked = pick?.pickedQuantity is not null ? Math.Max(0, pick.pickedQuantity.Value) : requested;
        var damaged = pick?.damaged == true;
        var sourceLoadUnitCode = pick?.sourceLoadUnitCode;
        var slot = pick?.slot;

        if (line.packSize is not null)
        {
            asrsStock.RemoveStock(
                order.clientNumber, line.articleNumber, PackSizeKeys.ToKey(line.packSize), line.reservationCode, picked);
        }

        var result = "PROCESSED";
        if (picked < requested)
        {
            result = "QUANTITY_ERROR";
            callback.SendStockCorrected(ShortPickCorrection(order, line, picked - requested,
                sourceLoadUnitCode, slot, picked));
        }
        if (damaged)
        {
            callback.SendStockLockChanged(DamageLock(order, line, sourceLoadUnitCode, slot, picked));
        }

        return ReplyLine(order, line, uuid, picked, result, null, PickedStockFor(order, line, picked, damaged));
    }

    // ---- Reply / message builders --------------------------------------------------------------

    private static GoodsOutOrderReply Reply(GoodsOutOrder order, string createdBy, string status,
        List<GoodsOutOrderReplyLine?>? lines) =>
        new(
            order.clientNumber, order.orderNumber, order.sheetNumber, createdBy, status,
            order.businessCase ?? "GOODS_OUT",
            KiSoftTime.Now(), order.loadUnitCode, order.loadCarrier,
            order.customerNumber, null, lines);

    private List<GoodsOutOrderReplyLine?>? UntouchedLines(GoodsOutOrder order)
    {
        if (order.goodsOutOrderLines is null) return null;
        var lines = new List<GoodsOutOrderReplyLine?>();
        foreach (var line in order.goodsOutOrderLines)
        {
            if (line is null) continue;
            lines.Add(ReplyLine(order, line, Guid.NewGuid().ToString(), 0, "UNTOUCHED", null, null));
        }
        return lines.Count == 0 ? null : lines;
    }

    private List<GoodsOutOrderReplyLine?>? FinishedLines(GoodsOutOrder order, List<GoodsOutOrderReplyLine> pickLines)
    {
        // Stock was already deducted at PROCESSED — do not re-validate ASRS quantity here.
        if (pickLines.Count > 0)
        {
            var lines = new List<GoodsOutOrderReplyLine?>();
            foreach (var pick in pickLines)
            {
                lines.Add(new GoodsOutOrderReplyLine(
                    Guid.NewGuid().ToString(),
                    pick.prjContainerID,
                    pick.lineReference,
                    pick.processedQuantity,
                    pick.processingResult,
                    pick.processingError,
                    pick.pickedStock));
            }
            return lines;
        }

        if (order.goodsOutOrderLines is null) return null;
        var fallback = new List<GoodsOutOrderReplyLine?>();
        foreach (var line in order.goodsOutOrderLines)
        {
            if (line is null) continue;
            var qty = line.requestedQuantity ?? 0;
            fallback.Add(ReplyLine(order, line, Guid.NewGuid().ToString(), qty, "PROCESSED", null,
                PickedStockFor(order, line, qty, false)));
        }
        return fallback.Count == 0 ? null : fallback;
    }

    private static StockCorrected ShortPickCorrection(GoodsOutOrder order, GoodsOutOrderLine line, int delta,
        string? loadUnitCode, int? slot, int picked) =>
        new(
            Guid.NewGuid().ToString(),
            null,
            new GoodsOutOrderReference(order.clientNumber, order.orderNumber, order.sheetNumber, line.lineReference),
            delta, line.stationName, "SHORT_PICK", null, null, KiSoftTime.Now(),
            StockEntryFor(order.clientNumber, line, loadUnitCode, slot, picked, null));

    private static StockLockChanged DamageLock(GoodsOutOrder order, GoodsOutOrderLine line,
        string? loadUnitCode, int? slot, int picked) =>
        new(
            Guid.NewGuid().ToString(),
            new StockLockRequestReference(order.clientNumber, order.orderNumber),
            picked, ["LOCKED_FOR_VISION_CHECK"], null, line.stationName,
            "DAMAGED_ARTICLE", null, KiSoftTime.Now(),
            StockEntryFor(order.clientNumber, line, loadUnitCode, slot, picked, ["LOCKED_FOR_VISION_CHECK"]));

    private static StockEntry StockEntryFor(string? clientNumber, GoodsOutOrderLine line, string? loadUnitCode,
        int? slot, int qty, List<string?>? locks) =>
        new(loadUnitCode, slot,
            new PackUnitKeyRef(clientNumber, line.articleNumber, line.packSize),
            qty, line.stockType, line.lotNumber, line.dateMark, null,
            line.reservationCode, locks, null, null, null);

    private static GoodsOutPickLine? FindPick(GoodsOutPickConfirmation c, string? lineReference)
    {
        if (c.lines is null) return null;
        return c.lines.FirstOrDefault(l => l is not null && lineReference == l.lineReference);
    }

    private List<PickedStock?> PickedStockFor(GoodsOutOrder order, GoodsOutOrderLine line, int qty, bool damaged)
    {
        AsrsStockEntity? row = null;
        if (line.packSize is not null)
        {
            row = asrsStock.Find(order.clientNumber, line.articleNumber,
                PackSizeKeys.ToKey(line.packSize), line.reservationCode);
        }

        List<string?>? locks;
        if (damaged)
        {
            locks = ["LOCKED_FOR_VISION_CHECK"];
        }
        else if (row is not null)
        {
            var reasons = AsrsStockService.ReadLockReasons(row);
            locks = reasons?.ConvertAll(r => (string?)r);
        }
        else
        {
            locks = null;
        }

        return
        [
            new PickedStock(
                qty,
                line.articleNumber,
                line.packSize,
                FirstNonBlank(line.stockType, row?.StockType),
                FirstNonBlank(line.lotNumber, row?.LotNumber),
                FirstNonBlank(line.dateMark, row?.DateMark),
                line.reservationCode,
                row?.SerialNumber,
                locks)
        ];
    }

    private static string? FirstNonBlank(string? preferred, string? fallback) =>
        !string.IsNullOrWhiteSpace(preferred) ? preferred : fallback;

    private static GoodsOutOrderReplyLine ReplyLine(GoodsOutOrder order, GoodsOutOrderLine line, string uuid,
        int processedQuantity, string processingResult, string? processingError, List<PickedStock?>? pickedStock) =>
        new(
            uuid,
            PrjContainerIds.ForLine(order.orderNumber, line.lineReference),
            line.lineReference,
            processedQuantity,
            processingResult,
            processingError,
            pickedStock);
}
