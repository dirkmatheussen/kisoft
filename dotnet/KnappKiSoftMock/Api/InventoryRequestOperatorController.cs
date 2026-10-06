using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Services;
using Microsoft.AspNetCore.Mvc;
using static KnappKiSoftMock.Services.InventoryRequestLifecycleService;

namespace KnappKiSoftMock.Api;

/// <summary>
/// Mock-only endpoint that simulates the KiSoft inventory count at the AeroBot workstation (GS §5.3.5).
/// </summary>
[ApiController]
[Route("oneapi/v1/inventoryRequest/operator")]
public sealed class InventoryRequestOperatorController(InventoryRequestLifecycleService lifecycle) : ControllerBase
{
    [HttpPost("count")]
    public IActionResult Count([FromBody, Valid] InventoryCountConfirmation confirmation)
    {
        var res = lifecycle.Count(confirmation);
        if (res.Code == Code.Ok)
        {
            return Ok(new OneApiOkResponse(200, "OK", res.Message));
        }

        var code = res.Code == Code.NotFound ? "E-AKO-MOVM-0003" : "E-AKO-MOVM-0004";
        return BadRequest(new OneApiErrorResponse(
            confirmation.clientNumber, null, null, null, null, confirmation.requestNumber,
            res.Message, [code]));
    }
}
