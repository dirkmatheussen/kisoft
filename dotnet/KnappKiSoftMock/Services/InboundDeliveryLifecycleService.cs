using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Options;
using KnappKiSoftMock.Persistence;
using Microsoft.Extensions.Options;

namespace KnappKiSoftMock.Services;

public sealed class InboundDeliveryLifecycleService(
    InboundDeliveryStoreService inboundStore,
    AppDbContext db,
    AsrsStockService asrsStock,
    ReplyCallbackService callback,
    IOptions<MockOptions> options)
{
    public const string StatusNew = "NEW";
    public const string StatusStarted = "STARTED";
    public const string StatusFinished = "FINISHED";

    public enum Code
    {
        Ok,
        NotFound,
        WrongStatus,
        QtyExceedsOpen,
        LineNotFound,
        CompartmentNotEmpty,
        WrongArticle
    }

    public sealed record LifecycleResult(
        Code Code,
        string? Message,
        StockReceived? StockReceived,
        InboundDeliveryReply? Reply,
        bool Finished);

    private readonly MockOptions _options = options.Value;

    // Java: every public lifecycle method is @Transactional.
    public void BookExpectedStock(InboundDelivery delivery) => db.InTransaction(() => BookExpectedStockCore(delivery));
    public void ReleaseExpectedStock(InboundDelivery delivery) => db.InTransaction(() => ReleaseExpectedStockCore(delivery));
    public LifecycleResult StartProcessing(string? clientNumber, string? inboundDeliveryNumber) =>
        db.InTransaction(() => StartProcessingCore(clientNumber, inboundDeliveryNumber));
    public LifecycleResult RecordLoadUnit(InboundDeliveryLoadUnitReceipt receipt) =>
        db.InTransaction(() => RecordLoadUnitCore(receipt));
    public LifecycleResult FinishProcessing(string? clientNumber, string? inboundDeliveryNumber) =>
        db.InTransaction(() => FinishProcessingCore(clientNumber, inboundDeliveryNumber));

    private void BookExpectedStockCore(InboundDelivery delivery)
    {
        if (!_options.InboundAutoStock || delivery.inboundDeliveryLines is null) return;
        foreach (var line in delivery.inboundDeliveryLines)
        {
            if (line?.articleNumber is null || line.packSize is null || line.expectedQuantity is null or <= 0) continue;
            var packSizeKey = PackSizeKeys.ToKey(line.packSize);
            asrsStock.AddStock(delivery.clientNumber, line.articleNumber, packSizeKey, line.expectedQuantity.Value, AttributesFrom(line));
            var processedStock = new StockEntry(
                line.loadUnitCode, null,
                new PackUnitKeyRef(delivery.clientNumber, line.articleNumber, line.packSize),
                line.expectedQuantity, line.stockType, line.lotNumber, line.dateMark, line.serialNumber,
                line.reservationCode, line.stockLockReasons, line.stockQuality, null, null);
            callback.SendStockReceived(new StockReceived(
                Guid.NewGuid().ToString(),
                new InboundDeliveryReference(delivery.clientNumber, delivery.inboundDeliveryNumber, line.lineReference),
                line.expectedQuantity, null, "INBOUND_AUTO_STOCK", null, KiSoftTime.Now(), processedStock));
        }
    }

    private void ReleaseExpectedStockCore(InboundDelivery delivery)
    {
        if (!_options.InboundAutoStock || delivery.inboundDeliveryLines is null) return;
        foreach (var line in delivery.inboundDeliveryLines)
        {
            if (line?.articleNumber is null || line.packSize is null || line.expectedQuantity is null or <= 0) continue;
            asrsStock.RemoveStock(delivery.clientNumber, line.articleNumber, PackSizeKeys.ToKey(line.packSize),
                line.reservationCode, line.expectedQuantity.Value);
        }
    }

    private LifecycleResult StartProcessingCore(string? clientNumber, string? inboundDeliveryNumber)
    {
        var entity = inboundStore.Find(clientNumber, inboundDeliveryNumber);
        if (entity is null)
        {
            return new LifecycleResult(Code.NotFound, "Inbound delivery not found", null, null, false);
        }
        if (entity.ProcessingStatus != StatusNew)
        {
            return new LifecycleResult(Code.WrongStatus, "Cannot start processing in status " + entity.ProcessingStatus, null, null, false);
        }

        var delivery = inboundStore.ReadPayload(entity);
        if (delivery.inboundDeliveryLines is not null)
        {
            // Java: progressRepo.existsBy… sees rows flushed earlier in the same transaction, so a
            // duplicate lineReference within one delivery is skipped instead of violating the unique index.
            var seen = new HashSet<string>();
            foreach (var line in delivery.inboundDeliveryLines)
            {
                if (line is null) continue;
                if (!seen.Add(line.lineReference ?? "")) continue;
                var exists = db.InboundProgress.Any(x =>
                    x.ClientNumber == clientNumber && x.InboundDeliveryNumber == inboundDeliveryNumber
                    && x.LineReference == line.lineReference);
                if (exists) continue;
                db.InboundProgress.Add(new InboundDeliveryProgressEntity
                {
                    ClientNumber = clientNumber ?? "",
                    InboundDeliveryNumber = inboundDeliveryNumber ?? "",
                    LineReference = line.lineReference ?? "",
                    ArticleNumber = line.articleNumber ?? "",
                    PackSize = line.packSize is null ? "" : PackSizeKeys.ToKey(line.packSize) ?? "",
                    ExpectedQuantity = line.expectedQuantity ?? 0,
                    ReceivedQuantity = 0
                });
            }
            db.SaveChanges();
        }

        inboundStore.UpdateStatus(entity, StatusStarted);
        var reply = new InboundDeliveryReply(
            delivery.clientNumber, delivery.inboundDeliveryNumber,
            delivery.businessCase ?? "GOODS_IN", "KISOFT", StatusStarted,
            KiSoftTime.Now(), delivery.inboundDeliveryLines);
        callback.SendInboundDeliveryReply(reply);
        return new LifecycleResult(Code.Ok, "Processing started", null, reply, false);
    }

    private LifecycleResult RecordLoadUnitCore(InboundDeliveryLoadUnitReceipt receipt)
    {
        var entity = inboundStore.Find(receipt.clientNumber, receipt.inboundDeliveryNumber);
        if (entity is null)
        {
            return new LifecycleResult(Code.NotFound, "Inbound delivery not found", null, null, false);
        }
        if (entity.ProcessingStatus != StatusStarted)
        {
            return new LifecycleResult(Code.WrongStatus,
                "Inbound delivery not in STARTED status (current: " + entity.ProcessingStatus + ")", null, null, false);
        }

        var progress = db.InboundProgress.FirstOrDefault(x =>
            x.ClientNumber == receipt.clientNumber && x.InboundDeliveryNumber == receipt.inboundDeliveryNumber
            && x.LineReference == receipt.lineReference);
        if (progress is null)
        {
            return new LifecycleResult(Code.LineNotFound, "Inbound delivery line " + receipt.lineReference + " not found", null, null, false);
        }

        var quantity = receipt.quantity ?? 0;
        if (quantity > progress.OpenQuantity)
        {
            return new LifecycleResult(Code.QtyExceedsOpen,
                $"Confirmed quantity {quantity} exceeds remaining open quantity {progress.OpenQuantity}", null, null, false);
        }

        var compartment = db.ToteCompartments.FirstOrDefault(x =>
            x.ClientNumber == receipt.clientNumber && x.LoadUnitCode == receipt.loadUnitCode && x.Compartment == receipt.compartment);
        if (compartment is not null)
        {
            if (compartment.ArticleNumber != progress.ArticleNumber || compartment.PackSize != progress.PackSize)
            {
                return new LifecycleResult(Code.WrongArticle,
                    $"Compartment already holds article {compartment.ArticleNumber}/{compartment.PackSize}", null, null, false);
            }
            return new LifecycleResult(Code.CompartmentNotEmpty, "Compartment is not empty - topping-up not allowed", null, null, false);
        }

        progress.ReceivedQuantity += quantity;
        db.ToteCompartments.Add(new ToteCompartmentEntity
        {
            ClientNumber = receipt.clientNumber ?? "",
            LoadUnitCode = receipt.loadUnitCode ?? "",
            Compartment = receipt.compartment ?? "",
            ArticleNumber = progress.ArticleNumber,
            PackSize = progress.PackSize,
            Quantity = quantity
        });
        db.SaveChanges();

        if (!_options.InboundAutoStock)
        {
            var deliveryForStock = inboundStore.ReadPayload(entity);
            asrsStock.AddStock(receipt.clientNumber, progress.ArticleNumber, progress.PackSize, quantity,
                AttributesFrom(receipt, CooOf(deliveryForStock, receipt.lineReference)));
        }

        int? packSize = int.TryParse(progress.PackSize, out var parsedPack) ? parsedPack : null;
        var processedStock = new StockEntry(
            receipt.loadUnitCode, ParseSlot(receipt.compartment),
            new PackUnitKeyRef(receipt.clientNumber, progress.ArticleNumber, packSize),
            quantity, receipt.stockType, receipt.lotNumber, receipt.dateMark, receipt.serialNumber,
            receipt.reservationCode, null, receipt.stockQuality, null, null);
        var stockReceived = new StockReceived(
            Guid.NewGuid().ToString(),
            new InboundDeliveryReference(receipt.clientNumber, receipt.inboundDeliveryNumber, receipt.lineReference),
            quantity, null, null, null, KiSoftTime.Now(), processedStock);
        callback.SendStockReceived(stockReceived);
        if (_options.StorageOrderReplyEnabled)
        {
            SendStorageOrderReply(receipt.clientNumber, receipt.inboundDeliveryNumber, receipt.loadUnitCode, "STARTED");
            SendStorageOrderReply(receipt.clientNumber, receipt.inboundDeliveryNumber, receipt.loadUnitCode, "FINISHED");
        }

        var allDone = IsAllReceived(receipt.clientNumber, receipt.inboundDeliveryNumber);
        InboundDeliveryReply? reply = null;
        if (allDone) reply = AutoFinish(entity);
        return new LifecycleResult(Code.Ok, "Load unit recorded", stockReceived, reply, allDone);
    }

    private LifecycleResult FinishProcessingCore(string? clientNumber, string? inboundDeliveryNumber)
    {
        var entity = inboundStore.Find(clientNumber, inboundDeliveryNumber);
        if (entity is null)
        {
            return new LifecycleResult(Code.NotFound, "Inbound delivery not found", null, null, false);
        }
        if (entity.ProcessingStatus != StatusStarted)
        {
            return new LifecycleResult(Code.WrongStatus, "Inbound delivery not in STARTED status", null, null, false);
        }
        var reply = AutoFinish(entity);
        return new LifecycleResult(Code.Ok, "Processing finished", null, reply, true);
    }

    private InboundDeliveryReply AutoFinish(InboundDeliveryEntity entity)
    {
        var delivery = inboundStore.ReadPayload(entity);
        if (_options.InboundAutoStock) CorrectAutoStockShortfall(delivery);
        inboundStore.UpdateStatus(entity, StatusFinished);
        var reply = new InboundDeliveryReply(
            delivery.clientNumber, delivery.inboundDeliveryNumber,
            delivery.businessCase ?? "GOODS_IN", "KISOFT", StatusFinished,
            KiSoftTime.Now(), delivery.inboundDeliveryLines);
        callback.SendInboundDeliveryReply(reply);
        return reply;
    }

    private bool IsAllReceived(string? clientNumber, string? inboundDeliveryNumber)
    {
        var progress = db.InboundProgress.Where(x =>
            x.ClientNumber == clientNumber && x.InboundDeliveryNumber == inboundDeliveryNumber).ToList();
        return progress.Count > 0 && progress.All(p => p.ReceivedQuantity >= p.ExpectedQuantity);
    }

    private void CorrectAutoStockShortfall(InboundDelivery delivery)
    {
        var progressList = db.InboundProgress.Where(x =>
            x.ClientNumber == delivery.clientNumber && x.InboundDeliveryNumber == delivery.inboundDeliveryNumber).ToList();
        foreach (var progress in progressList)
        {
            var shortfall = progress.ExpectedQuantity - progress.ReceivedQuantity;
            if (shortfall > 0)
            {
                asrsStock.RemoveStock(progress.ClientNumber, progress.ArticleNumber, progress.PackSize,
                    CooOf(delivery, progress.LineReference), shortfall);
            }
        }
    }

    private void SendStorageOrderReply(string? clientNumber, string? inboundDeliveryNumber, string? loadUnitCode, string status) =>
        callback.SendStorageOrderReply(new StorageOrderReply(
            loadUnitCode, clientNumber, inboundDeliveryNumber, status, KiSoftTime.Now()));

    private static AsrsStockAttributes AttributesFrom(InboundDeliveryLine line) =>
        new(line.stockType, line.lotNumber, line.dateMark, line.serialNumber, line.reservationCode, Strings(line.stockLockReasons));

    private static AsrsStockAttributes AttributesFrom(InboundDeliveryLoadUnitReceipt receipt, string lineCoo)
    {
        var coo = !string.IsNullOrWhiteSpace(receipt.reservationCode) ? receipt.reservationCode : lineCoo;
        return new AsrsStockAttributes(receipt.stockType, receipt.lotNumber, receipt.dateMark, receipt.serialNumber, coo, null);
    }

    private static string CooOf(InboundDelivery delivery, string? lineReference)
    {
        if (delivery.inboundDeliveryLines is null) return "";
        return delivery.inboundDeliveryLines
            .Where(line => line is not null && line.lineReference == lineReference)
            .Select(line => ReservationCodes.Normalize(line!.reservationCode))
            .FirstOrDefault() ?? "";
    }

    private static int? ParseSlot(string? compartment) =>
        int.TryParse(compartment, out var slot) ? slot : null;

    private static List<string>? Strings(List<string?>? values) =>
        values?.Where(v => v is not null).Select(v => v!).ToList();
}
