using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Services;
using Microsoft.AspNetCore.Mvc;

namespace KnappKiSoftMock.Api;

/// <summary>
/// Mock KiSoft Inventory Request API — Inventory (HIS Appendix §7.2, GS §5.3.5).
/// </summary>
[ApiController]
[Route("oneapi/v1")]
public sealed class InventoryRequestController(
    InventoryRequestStoreService store,
    InventoryRequestLifecycleService lifecycle,
    PackUnitStoreService packUnitStore,
    ReplyCallbackService replyCallbackService) : ControllerBase
{
    [HttpPost("inventoryRequest")]
    public IActionResult PostInventoryRequest([FromBody, Valid] InventoryRequest request)
    {
        if (store.Exists(request.clientNumber, request.requestNumber))
        {
            return BadRequest(Error(request.clientNumber, request.requestNumber, "E-AKO-MOVM-0002"));
        }

        if (request.inventoryRequestLine is not null)
        {
            var line = request.inventoryRequestLine;
            if (line.articleNumber is not null
                && !packUnitStore.ExistsArticle(request.clientNumber, line.articleNumber))
            {
                return BadRequest(new OneApiErrorResponse(
                    request.clientNumber, null, null, line.articleNumber,
                    line.packSize?.ToString(),
                    request.requestNumber, "Unknown article: " + line.articleNumber,
                    ["E-AKO-MAST-0001"]));
            }

            if (line.articleNumber is not null
                && store.HasActiveRequestForArticle(request.clientNumber, line.articleNumber, line.reservationCode))
            {
                return Conflict(new OneApiErrorResponse(
                    request.clientNumber, null, null, line.articleNumber,
                    line.packSize?.ToString(),
                    request.requestNumber,
                    "Active inventory request already exists for article " + line.articleNumber
                    + " and reservationCode " + (line.reservationCode ?? "null"),
                    ["E-AKO-MOVM-0005"]));
            }
        }

        store.CreateNew(request);
        replyCallbackService.SendInventoryRequestReply(lifecycle.BuildIntakeReply(request, "HOST"));
        return Ok(new OneApiOkResponse(200, "OK", "Inventory request created successfully"));
    }

    [HttpDelete("inventoryRequest")]
    public IActionResult DeleteInventoryRequest([FromBody, Valid] InventoryRequestRef request)
    {
        if (!store.Delete(request.clientNumber, request.requestNumber))
        {
            return BadRequest(Error(request.clientNumber, request.requestNumber, "E-AKO-MOVM-0003"));
        }
        return Ok(new OneApiOkResponse(200, "OK", "Inventory request deleted"));
    }

    private static OneApiErrorResponse Error(string? clientNumber, string? requestNumber, string code) =>
        new(clientNumber, null, null, null, null, requestNumber, null, [code]);
}
