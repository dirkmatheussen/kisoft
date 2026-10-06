using System.Text.Json;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Persistence;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace KnappKiSoftMock.Services;

/// <summary>
/// Bulk-load ASRS stock from an InventoryReport <c>stockInventory</c> array (mock load-test helper).
/// </summary>
public sealed class InventoryImportService(
    AppDbContext db,
    IOptions<JsonOptions> jsonOptions)
{
    private const int BatchSize = 500;
    private readonly JsonSerializerOptions _jsonOptions = jsonOptions.Value.JsonSerializerOptions;

    public sealed record ImportResult(int RowsRead, int RowsWritten, bool UniquifyArticles, bool ReplaceAll);

    public ImportResult ImportItems(List<StockInventory?>? items, bool uniquifyArticles, bool replaceAll)
    {
        if (items is null || items.Count == 0)
        {
            return new ImportResult(0, 0, uniquifyArticles, replaceAll);
        }
        if (replaceAll)
        {
            ReplaceAllStock();
        }
        var written = PersistBatches(items, uniquifyArticles);
        return new ImportResult(items.Count, written, uniquifyArticles, replaceAll);
    }

    /// <summary>
    /// Stream-parse an InventoryReport JSON file and import <c>stockInventory</c>.
    /// Mirrors Java InventoryImportService: reads the file, walks the stockInventory array, persists in batches of 500.
    /// </summary>
    public ImportResult ImportFromFile(string path, bool uniquifyArticles, bool replaceAll)
    {
        if (replaceAll)
        {
            ReplaceAllStock();
        }

        var read = 0;
        var written = 0;
        var batch = new List<StockInventory?>(BatchSize);

        using var stream = File.OpenRead(path);
        using var doc = JsonDocument.Parse(stream);
        if (doc.RootElement.ValueKind != JsonValueKind.Object)
        {
            throw new ArgumentException("Expected InventoryReport object at root");
        }

        foreach (var property in doc.RootElement.EnumerateObject())
        {
            if (property.Name != "stockInventory") continue;
            if (property.Value.ValueKind != JsonValueKind.Array)
            {
                throw new ArgumentException("stockInventory must be an array");
            }

            foreach (var element in property.Value.EnumerateArray())
            {
                var item = element.Deserialize<StockInventory>(_jsonOptions);
                batch.Add(item);
                read++;
                if (batch.Count >= BatchSize)
                {
                    written += PersistBatch(batch, uniquifyArticles, read - batch.Count);
                    batch.Clear();
                    if (read % 10_000 == 0)
                    {
                        Console.WriteLine($"Inventory import progress: {read} rows read");
                    }
                }
            }
        }

        if (batch.Count > 0)
        {
            written += PersistBatch(batch, uniquifyArticles, read - batch.Count);
        }

        Console.WriteLine(
            $"Inventory import done: read={read}, written={written}, uniquify={uniquifyArticles}, replaceAll={replaceAll}");
        return new ImportResult(read, written, uniquifyArticles, replaceAll);
    }

    private void ReplaceAllStock()
    {
        db.AsrsStock.RemoveRange(db.AsrsStock);
        db.SaveChanges();
    }

    private int PersistBatches(List<StockInventory?> items, bool uniquifyArticles)
    {
        var written = 0;
        for (var i = 0; i < items.Count; i += BatchSize)
        {
            var slice = items.GetRange(i, Math.Min(BatchSize, items.Count - i));
            written += PersistBatch(slice, uniquifyArticles, i);
        }
        return written;
    }

    private int PersistBatch(List<StockInventory?> slice, bool uniquifyArticles, int startIndex)
    {
        var entities = new List<AsrsStockEntity>(slice.Count);
        for (var i = 0; i < slice.Count; i++)
        {
            var item = slice[i];
            if (item?.packUnit?.articleNumber is null) continue;

            var pu = item.packUnit;
            var seq = startIndex + i + 1;
            var article = uniquifyArticles
                ? pu.articleNumber + " - " + seq
                : pu.articleNumber;
            var packSize = pu.packSize is not null ? pu.packSize.Value.ToString() : "1";
            if (pu.clientNumber is null)
            {
                // Java: client_number is NOT NULL → DataIntegrityViolationException for the whole batch.
                throw new InvalidOperationException(
                    "NULL not allowed for column \"CLIENT_NUMBER\" (asrs_stock row " + seq + ")");
            }
            var entity = new AsrsStockEntity
            {
                ClientNumber = pu.clientNumber,
                ArticleNumber = article ?? "",
                PackSize = packSize,
                ReservationCode = ReservationCodes.Normalize(item.reservationCode),
                Quantity = item.quantity ?? 0,
                StockType = item.stockType,
                LotNumber = item.lotNumber,
                DateMark = item.dateMark,
                SerialNumber = item.serialNumber
            };
            if (item.stockLockReasons is { Count: > 0 })
            {
                try
                {
                    entity.StockLockReasonsJson = JsonSerializer.Serialize(item.stockLockReasons, _jsonOptions);
                }
                catch
                {
                    entity.StockLockReasonsJson = null;
                }
            }
            entities.Add(entity);
        }

        db.AsrsStock.AddRange(entities);
        db.SaveChanges();
        return entities.Count;
    }
}
