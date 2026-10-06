using System.Text.Json;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Persistence;

namespace KnappKiSoftMock.Services;

public sealed class AsrsStockService(AppDbContext db)
{
    public sealed record LockChange(
        AsrsStockEntity Entity,
        List<string>? ResultingReasons,
        List<string> Added,
        List<string> Removed);

    public void AddStock(string? clientNumber, string? articleNumber, string? packSize, int delta) =>
        AddStock(clientNumber, articleNumber, packSize, ReservationCodes.Normalize(null), delta, null);

    public void AddStock(string? clientNumber, string? articleNumber, string? packSize, int delta, AsrsStockAttributes? attributes)
    {
        var reservationCode = ReservationCodes.Normalize(attributes?.ReservationCode);
        AddStock(clientNumber, articleNumber, packSize, reservationCode, delta, attributes);
    }

    public void AddStock(string? clientNumber, string? articleNumber, string? packSize, string? reservationCode, int delta, AsrsStockAttributes? attributes)
    {
        if (delta <= 0 || clientNumber is null || articleNumber is null || packSize is null) return;
        var coo = ReservationCodes.Normalize(reservationCode);
        var entity = FindTracked(clientNumber, articleNumber, packSize, coo)
                     ?? db.AsrsStock.Add(new AsrsStockEntity
                     {
                         ClientNumber = clientNumber,
                         ArticleNumber = articleNumber,
                         PackSize = packSize,
                         ReservationCode = coo,
                         Quantity = 0
                     }).Entity;
        entity.Quantity += delta;
        if (attributes is not null) ApplyAttributes(entity, attributes);
        db.SaveChanges();
    }

    public int RemoveStock(string? clientNumber, string? articleNumber, string? packSize, int qty) =>
        RemoveStock(clientNumber, articleNumber, packSize, ReservationCodes.Normalize(null), qty);

    public int RemoveStock(string? clientNumber, string? articleNumber, string? packSize, string? reservationCode, int qty)
    {
        if (qty <= 0 || clientNumber is null || articleNumber is null || packSize is null) return 0;
        var coo = ReservationCodes.Normalize(reservationCode);
        var entity = FindTracked(clientNumber, articleNumber, packSize, coo);
        if (entity is null && coo.Length == 0)
        {
            entity = UniqueRowForPackSize(clientNumber, articleNumber, packSize);
        }
        if (entity is null) return 0;
        var removed = Math.Min(qty, entity.Quantity);
        entity.Quantity -= removed;
        db.SaveChanges();
        return removed;
    }

    public int SetQuantity(string? clientNumber, string? articleNumber, string? packSize, int counted) =>
        SetQuantity(clientNumber, articleNumber, packSize, ReservationCodes.Normalize(null), counted);

    public int SetQuantity(string? clientNumber, string? articleNumber, string? packSize, string? reservationCode, int counted)
    {
        if (clientNumber is null || articleNumber is null || packSize is null) return 0;
        if (counted < 0) counted = 0;
        var coo = ReservationCodes.Normalize(reservationCode);
        var entity = FindTracked(clientNumber, articleNumber, packSize, coo);
        if (entity is null && coo.Length == 0)
        {
            entity = UniqueRowForPackSize(clientNumber, articleNumber, packSize);
        }
        if (entity is null)
        {
            entity = db.AsrsStock.Add(new AsrsStockEntity
            {
                ClientNumber = clientNumber,
                ArticleNumber = articleNumber,
                PackSize = packSize,
                ReservationCode = coo,
                Quantity = 0
            }).Entity;
        }
        var delta = counted - entity.Quantity;
        entity.Quantity = counted;
        db.SaveChanges();
        return delta;
    }

    public int AvailableForArticle(string? clientNumber, string? articleNumber) =>
        db.AsrsStock.Where(x => x.ClientNumber == clientNumber && x.ArticleNumber == articleNumber)
            .Sum(x => x.Quantity);

    public int AvailableForArticle(string? clientNumber, string? articleNumber, string? reservationCode)
    {
        var coo = ReservationCodes.Normalize(reservationCode);
        return db.AsrsStock.Where(x => x.ClientNumber == clientNumber && x.ArticleNumber == articleNumber)
            .AsEnumerable()
            .Where(x => coo == ReservationCodes.Normalize(x.ReservationCode))
            .Sum(x => x.Quantity);
    }

    public bool HasStock(string? clientNumber, string? articleNumber, string? packSize) =>
        db.AsrsStock.Any(x => x.ClientNumber == clientNumber && x.ArticleNumber == articleNumber
                              && x.PackSize == packSize && x.Quantity > 0);

    public bool HasStock(string? clientNumber, string? articleNumber, string? packSize, string? reservationCode) =>
        db.AsrsStock.Any(x => x.ClientNumber == clientNumber && x.ArticleNumber == articleNumber
                              && x.PackSize == packSize
                              && x.ReservationCode == ReservationCodes.Normalize(reservationCode)
                              && x.Quantity > 0);

    public int GetQuantity(string? clientNumber, string? articleNumber, string? packSize) =>
        GetQuantity(clientNumber, articleNumber, packSize, ReservationCodes.Normalize(null));

    public int GetQuantity(string? clientNumber, string? articleNumber, string? packSize, string? reservationCode) =>
        FindTracked(clientNumber, articleNumber, packSize, ReservationCodes.Normalize(reservationCode))?.Quantity ?? 0;

    public AsrsStockEntity? Find(string? clientNumber, string? articleNumber, string? packSize, string? reservationCode) =>
        FindTracked(clientNumber, articleNumber, packSize, ReservationCodes.Normalize(reservationCode));

    public List<AsrsStockEntity> ListAll() => db.AsrsStock.OrderBy(x => x.Id).ToList();

    public LockChange? ChangeLocks(string? clientNumber, string? articleNumber, string? packSizeKey,
        string? reservationCode, LockAction action, List<string>? reasons)
    {
        var entity = FindTracked(clientNumber, articleNumber, packSizeKey, ReservationCodes.Normalize(reservationCode));
        if (entity is null) return null;

        var current = ReadLockReasons(entity);
        var result = current is null ? new List<string>() : new List<string>(current);
        var requested = reasons ?? [];
        var added = new List<string>();
        var removed = new List<string>();
        if (action == LockAction.LOCK)
        {
            foreach (var reason in requested)
            {
                if (!result.Contains(reason))
                {
                    result.Add(reason);
                    added.Add(reason);
                }
            }
        }
        else if (requested.Count == 0)
        {
            removed.AddRange(result);
            result.Clear();
        }
        else
        {
            foreach (var reason in requested)
            {
                if (result.Remove(reason)) removed.Add(reason);
            }
        }

        WriteLockReasons(entity, result);
        db.SaveChanges();
        return new LockChange(entity, result.Count == 0 ? null : result, added, removed);
    }

    public static List<string>? ReadLockReasons(AsrsStockEntity entity)
    {
        if (string.IsNullOrWhiteSpace(entity.StockLockReasonsJson)) return null;
        try
        {
            // Jackson List<String>: scalars (numbers/booleans) are coerced to their text, null stays null.
            using var doc = JsonDocument.Parse(entity.StockLockReasonsJson);
            if (doc.RootElement.ValueKind != JsonValueKind.Array) return null;
            var list = new List<string>();
            foreach (var element in doc.RootElement.EnumerateArray())
            {
                list.Add(element.ValueKind switch
                {
                    JsonValueKind.String => element.GetString()!,
                    JsonValueKind.Null => null!,
                    JsonValueKind.Number or JsonValueKind.True or JsonValueKind.False => element.GetRawText(),
                    _ => throw new JsonException("Cannot coerce " + element.ValueKind + " to String")
                });
            }
            return list.Count == 0 ? null : list;
        }
        catch (Exception)
        {
            return null;
        }
    }

    private AsrsStockEntity? FindTracked(string? clientNumber, string? articleNumber, string? packSize, string reservationCode) =>
        db.AsrsStock.FirstOrDefault(x =>
            x.ClientNumber == clientNumber && x.ArticleNumber == articleNumber
            && x.PackSize == packSize && x.ReservationCode == reservationCode);

    private AsrsStockEntity? UniqueRowForPackSize(string clientNumber, string articleNumber, string packSize)
    {
        var matches = db.AsrsStock
            .Where(x => x.ClientNumber == clientNumber && x.ArticleNumber == articleNumber && x.PackSize == packSize)
            .ToList();
        return matches.Count == 1 ? matches[0] : null;
    }

    private static void WriteLockReasons(AsrsStockEntity entity, List<string> reasons)
    {
        entity.StockLockReasonsJson = reasons.Count == 0 ? null : JsonSerializer.Serialize(reasons);
    }

    private static void ApplyAttributes(AsrsStockEntity entity, AsrsStockAttributes attributes)
    {
        entity.StockType = attributes.StockType;
        entity.LotNumber = attributes.LotNumber;
        entity.DateMark = attributes.DateMark;
        entity.SerialNumber = attributes.SerialNumber;
        if (attributes.StockLockReasons is not null)
        {
            WriteLockReasons(entity, attributes.StockLockReasons);
        }
    }
}
