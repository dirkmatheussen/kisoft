using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Services;
using Microsoft.AspNetCore.Mvc;

namespace KnappKiSoftMock.Api;

/// <summary>
/// Mock-only endpoints for the warehouse-internal processes driven by KiSoft One (GS §5.3).
/// </summary>
[ApiController]
[Route("oneapi/v1/loadUnit")]
public sealed class WarehouseInternalController(ReplyCallbackService callback, AsrsStockService asrsStock)
    : ControllerBase
{
    [HttpPost("retrieve")]
    public OneApiOkResponse Retrieve([FromBody, Valid] LoadUnitRetrievalRequest r)
    {
        var hasStock = r.articleNumber is not null && r.packSize is not null;
        var loadCarrier = r.loadCarrier ?? "FULL";
        List<StockEntry?>? loadUnitStock = hasStock
            ? [new StockEntry(r.loadUnitCode, r.slot,
                new PackUnitKeyRef(r.clientNumber, r.articleNumber, r.packSize),
                r.quantity, r.stockType, null, null, null, r.reservationCode, null, null, null, null)]
            : null;

        callback.SendLoadUnitMoved(new LoadUnitMoved(
            Guid.NewGuid().ToString(),
            r.loadUnitCode, loadCarrier, KiSoftTime.Now(), "RELOCATION",
            new NewPosition(r.stationName, r.locationNumber, null),
            "STOCK", null, null, loadUnitStock));

        if (r.toConventional == true && hasStock)
        {
            var qty = r.quantity ?? 0;
            var removed = asrsStock.RemoveStock(
                r.clientNumber, r.articleNumber, PackSizeKeys.ToKey(r.packSize), r.reservationCode, qty);
            var processedStock = loadUnitStock is { Count: > 0 } ? loadUnitStock[0] : null;
            callback.SendStockCorrected(new StockCorrected(
                Guid.NewGuid().ToString(),
                null, null, -removed, r.stationName, "RETRIEVAL_TO_CONVENTIONAL", null, null,
                KiSoftTime.Now(), processedStock));
        }

        return new OneApiOkResponse(200, "OK", "Targeted retrieval triggered, PostLoadUnitMoved sent");
    }

    [HttpPost("repack")]
    public OneApiOkResponse Repack([FromBody, Valid] RepackRequest r)
    {
        var delta = r.deltaQuantity ?? 0;
        if (delta != 0 && r.packSize is not null)
        {
            if (delta > 0)
            {
                asrsStock.AddStock(r.clientNumber, r.articleNumber, PackSizeKeys.ToKey(r.packSize),
                    r.reservationCode, delta, null);
            }
            else
            {
                asrsStock.RemoveStock(r.clientNumber, r.articleNumber, PackSizeKeys.ToKey(r.packSize),
                    r.reservationCode, -delta);
            }
        }

        var entry = new StockEntry(r.targetLoadUnitCode, r.targetSlot,
            new PackUnitKeyRef(r.clientNumber, r.articleNumber, r.packSize),
            null, null, null, null, null, r.reservationCode, null, null, null, null);
        callback.SendStockCorrected(new StockCorrected(
            Guid.NewGuid().ToString(),
            null, null, delta, r.stationName,
            r.reason ?? "REPACKING", null, null,
            KiSoftTime.Now(), entry));
        return new OneApiOkResponse(200, "OK", "Repacking triggered, PostStockCorrected sent");
    }
}
