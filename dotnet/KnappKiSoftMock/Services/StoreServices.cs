using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Persistence;

namespace KnappKiSoftMock.Services;

public sealed class MasterdataSessionService(AppDbContext db)
{
    public const string DomainPackUnit = "PACK_UNIT";

    public void StartSession(string domain, string clientNumber)
    {
        db.MasterdataDeltas.RemoveRange(db.MasterdataDeltas.Where(x => x.Domain == domain && x.ClientNumber == clientNumber));
        db.SaveChanges();
    }

    public void MarkSeen(string domain, string clientNumber, string keyValue)
    {
        var exists = db.MasterdataDeltas.Any(x => x.Domain == domain && x.ClientNumber == clientNumber && x.KeyValue == keyValue);
        if (!exists)
        {
            db.MasterdataDeltas.Add(new MasterdataSessionDeltaEntity
            {
                Domain = domain,
                ClientNumber = clientNumber,
                KeyValue = keyValue
            });
            db.SaveChanges();
        }
    }

    public bool WasSeen(string domain, string clientNumber, string keyValue) =>
        db.MasterdataDeltas.Any(x => x.Domain == domain && x.ClientNumber == clientNumber && x.KeyValue == keyValue);

    public void ClearSession(string domain, string clientNumber)
    {
        db.MasterdataDeltas.RemoveRange(db.MasterdataDeltas.Where(x => x.Domain == domain && x.ClientNumber == clientNumber));
        db.SaveChanges();
    }
}

public sealed class PackUnitStoreService(AppDbContext db, MasterdataSessionService sessions, JsonPayloadMapper json)
{
    public static string Key(string? clientNumber, string? articleNumber, int? packSize) =>
        $"{clientNumber}|{articleNumber}|{PackSizeKeys.ToKey(packSize)}";

    public static string Key(string? clientNumber, string? articleNumber, string? packSize) =>
        $"{clientNumber}|{articleNumber}|{packSize}";

    public void StartUpdateSession(string clientNumber) => sessions.StartSession(MasterdataSessionService.DomainPackUnit, clientNumber);

    // Java: cleanupUpdateSession and upsertAll are @Transactional (all-or-nothing).
    public void CleanupUpdateSession(string clientNumber) => db.InTransaction(() => CleanupUpdateSessionCore(clientNumber));
    public void UpsertAll(IEnumerable<PackUnitFull> units) => db.InTransaction(() => UpsertAllCore(units));

    private void CleanupUpdateSessionCore(string clientNumber)
    {
        var existing = db.PackUnits.Where(x => x.ClientNumber == clientNumber).ToList();
        foreach (var entity in existing)
        {
            var key = Key(entity.ClientNumber, entity.ArticleNumber, entity.PackSize);
            if (!sessions.WasSeen(MasterdataSessionService.DomainPackUnit, clientNumber, key))
            {
                db.PackUnits.Remove(entity);
            }
        }
        sessions.ClearSession(MasterdataSessionService.DomainPackUnit, clientNumber);
        db.SaveChanges();
    }

    private void UpsertAllCore(IEnumerable<PackUnitFull> units)
    {
        foreach (var unit in units)
        {
            var client = unit.article?.clientNumber ?? "DEFAULT";
            var article = unit.article?.articleNumber;
            var packSizeKey = PackSizeKeys.ToKey(unit.packSize) ?? "";
            var payload = json.ToJson(unit);
            var entity = db.PackUnits.FirstOrDefault(x =>
                x.ClientNumber == client && x.ArticleNumber == article && x.PackSize == packSizeKey);
            if (entity is null)
            {
                entity = new PackUnitEntity
                {
                    ClientNumber = client,
                    ArticleNumber = article ?? "",
                    PackSize = packSizeKey
                };
                db.PackUnits.Add(entity);
            }
            entity.PayloadJson = payload;
            sessions.MarkSeen(MasterdataSessionService.DomainPackUnit, client, Key(client, article, unit.packSize));
        }
        db.SaveChanges();
    }

    public bool Exists(string? clientNumber, string? articleNumber, int? packSize) =>
        db.PackUnits.Any(x => x.ClientNumber == clientNumber && x.ArticleNumber == articleNumber
                              && x.PackSize == PackSizeKeys.ToKey(packSize));

    public bool ExistsArticle(string? clientNumber, string? articleNumber) =>
        db.PackUnits.Any(x => x.ClientNumber == clientNumber && x.ArticleNumber == articleNumber);

    public PackUnitFull? FindAnyByArticle(string? clientNumber, string? articleNumber)
    {
        var entity = db.PackUnits.FirstOrDefault(x => x.ClientNumber == clientNumber && x.ArticleNumber == articleNumber);
        return entity is null ? null : json.FromJson<PackUnitFull>(entity.PayloadJson);
    }

    public List<PackUnitFull> ListAll() =>
        db.PackUnits.OrderBy(x => x.Id).ToList().Select(e => json.FromJson<PackUnitFull>(e.PayloadJson)).ToList();

    public bool DeleteOne(string? clientNumber, string? articleNumber, int? packSize)
    {
        var entity = db.PackUnits.FirstOrDefault(x =>
            x.ClientNumber == clientNumber && x.ArticleNumber == articleNumber && x.PackSize == PackSizeKeys.ToKey(packSize));
        if (entity is null) return false;
        db.PackUnits.Remove(entity);
        db.SaveChanges();
        return true;
    }
}

public sealed class InboundDeliveryStoreService(AppDbContext db, JsonPayloadMapper json)
{
    public bool Exists(string? clientNumber, string? inboundDeliveryNumber) =>
        db.InboundDeliveries.Any(x => x.ClientNumber == clientNumber && x.InboundDeliveryNumber == inboundDeliveryNumber);

    public void CreateNew(InboundDelivery delivery)
    {
        db.InboundDeliveries.Add(new InboundDeliveryEntity
        {
            ClientNumber = delivery.clientNumber ?? "",
            InboundDeliveryNumber = delivery.inboundDeliveryNumber ?? "",
            ProcessingStatus = "NEW",
            PayloadJson = json.ToJson(delivery)
        });
        db.SaveChanges();
    }

    public InboundDeliveryEntity? Find(string? clientNumber, string? inboundDeliveryNumber) =>
        db.InboundDeliveries.FirstOrDefault(x => x.ClientNumber == clientNumber && x.InboundDeliveryNumber == inboundDeliveryNumber);

    public void Update(InboundDeliveryEntity entity, InboundDelivery updated)
    {
        entity.PayloadJson = json.ToJson(updated);
        db.SaveChanges();
    }

    public void UpdateStatus(InboundDeliveryEntity entity, string status)
    {
        entity.ProcessingStatus = status;
        db.SaveChanges();
    }

    public InboundDelivery ReadPayload(InboundDeliveryEntity entity) => json.FromJson<InboundDelivery>(entity.PayloadJson);

    public List<InboundDeliveryEntity> ListAllEntities() => db.InboundDeliveries.OrderBy(x => x.Id).ToList();

    public void Delete(string? clientNumber, string? inboundDeliveryNumber)
    {
        db.InboundProgress.RemoveRange(db.InboundProgress.Where(x =>
            x.ClientNumber == clientNumber && x.InboundDeliveryNumber == inboundDeliveryNumber));
        var entity = Find(clientNumber, inboundDeliveryNumber);
        if (entity is not null) db.InboundDeliveries.Remove(entity);
        db.SaveChanges();
    }
}

public sealed class GoodsOutOrderStoreService(AppDbContext db, JsonPayloadMapper json)
{
    public bool Exists(string? clientNumber, string? orderNumber, int? sheetNumber) =>
        db.GoodsOutOrders.Any(x => x.ClientNumber == clientNumber && x.OrderNumber == orderNumber
                                   && x.SheetNumber == SheetNumbers.ToKey(sheetNumber));

    public void CreateNew(GoodsOutOrder order)
    {
        db.GoodsOutOrders.Add(new GoodsOutOrderEntity
        {
            ClientNumber = order.clientNumber ?? "",
            OrderNumber = order.orderNumber ?? "",
            SheetNumber = SheetNumbers.ToKey(order.sheetNumber) ?? "",
            ProcessingStatus = "NEW",
            PayloadJson = json.ToJson(order)
        });
        db.SaveChanges();
    }

    public GoodsOutOrderEntity? Find(string? clientNumber, string? orderNumber, int? sheetNumber) =>
        db.GoodsOutOrders.FirstOrDefault(x => x.ClientNumber == clientNumber && x.OrderNumber == orderNumber
                                              && x.SheetNumber == SheetNumbers.ToKey(sheetNumber));

    public GoodsOutOrder ReadPayload(GoodsOutOrderEntity entity) => json.FromJson<GoodsOutOrder>(entity.PayloadJson);

    public void Update(GoodsOutOrderEntity entity, GoodsOutOrder updated)
    {
        entity.PayloadJson = json.ToJson(updated);
        db.SaveChanges();
    }

    public void SavePickResult(GoodsOutOrderEntity entity, List<GoodsOutOrderReplyLine>? lines)
    {
        entity.PickResultJson = lines is null ? null : json.ToJson(lines);
        db.SaveChanges();
    }

    public List<GoodsOutOrderReplyLine> ReadPickResult(GoodsOutOrderEntity entity)
    {
        if (string.IsNullOrWhiteSpace(entity.PickResultJson)) return [];
        return json.FromJson<List<GoodsOutOrderReplyLine>>(entity.PickResultJson) ?? [];
    }

    public void UpdateStatus(string? clientNumber, string? orderNumber, int? sheetNumber, string status)
    {
        var entity = Find(clientNumber, orderNumber, sheetNumber);
        if (entity is null) return;
        entity.ProcessingStatus = status;
        db.SaveChanges();
    }

    public string? GetStatus(string? clientNumber, string? orderNumber, int? sheetNumber) =>
        Find(clientNumber, orderNumber, sheetNumber)?.ProcessingStatus;

    public List<GoodsOutOrderEntity> ListAllEntities() => db.GoodsOutOrders.OrderBy(x => x.Id).ToList();

    public bool Delete(string? clientNumber, string? orderNumber, int? sheetNumber)
    {
        var entity = Find(clientNumber, orderNumber, sheetNumber);
        if (entity is null) return false;
        db.GoodsOutOrders.Remove(entity);
        db.SaveChanges();
        return true;
    }
}

public sealed class InventoryRequestStoreService(AppDbContext db, JsonPayloadMapper json)
{
    public bool Exists(string? clientNumber, string? requestNumber) =>
        db.InventoryRequests.Any(x => x.ClientNumber == clientNumber && x.RequestNumber == requestNumber);

    public void CreateNew(InventoryRequest request)
    {
        db.InventoryRequests.Add(new InventoryRequestEntity
        {
            ClientNumber = request.clientNumber ?? "",
            RequestNumber = request.requestNumber ?? "",
            ProcessingStatus = "NEW",
            PayloadJson = json.ToJson(request)
        });
        db.SaveChanges();
    }

    public InventoryRequestEntity? Find(string? clientNumber, string? requestNumber) =>
        db.InventoryRequests.FirstOrDefault(x => x.ClientNumber == clientNumber && x.RequestNumber == requestNumber);

    public InventoryRequest ReadPayload(InventoryRequestEntity entity) => json.FromJson<InventoryRequest>(entity.PayloadJson);

    public void UpdateStatus(string? clientNumber, string? requestNumber, string status)
    {
        var entity = Find(clientNumber, requestNumber);
        if (entity is null) return;
        entity.ProcessingStatus = status;
        db.SaveChanges();
    }

    public string? GetStatus(string? clientNumber, string? requestNumber) =>
        Find(clientNumber, requestNumber)?.ProcessingStatus;

    public bool HasActiveRequestForArticle(string? clientNumber, string? articleNumber, string? reservationCode)
    {
        var coi = reservationCode ?? "";
        return db.InventoryRequests.Where(x => x.ClientNumber == clientNumber && x.ProcessingStatus == "NEW")
            .AsEnumerable()
            .Any(entity =>
            {
                var request = ReadPayload(entity);
                var line = request.inventoryRequestLine;
                if (line is null) return false;
                return articleNumber == line.articleNumber && coi == (line.reservationCode ?? "");
            });
    }

    public bool Delete(string? clientNumber, string? requestNumber)
    {
        var entity = Find(clientNumber, requestNumber);
        if (entity is null) return false;
        db.InventoryRequests.Remove(entity);
        db.SaveChanges();
        return true;
    }
}
