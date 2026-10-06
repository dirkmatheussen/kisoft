using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Services;
using Microsoft.AspNetCore.Mvc;

namespace KnappKiSoftMock.Api;

/// <summary>
/// Mock-only endpoints that simulate the KiSoft workstation goods-out flow (GS §5.2.3–§5.2.5).
/// </summary>
[ApiController]
[Route("oneapi/v1/goodsOutOrder/operator")]
public sealed class GoodsOutOperatorController(GoodsOutOrderLifecycleService lifecycle) : ControllerBase
{
    [HttpPost("start")]
    public IActionResult Start([FromBody, Valid] GoodsOutOrderRef refBody) =>
        ToResponse(lifecycle.StartProcessing(refBody.clientNumber, refBody.orderNumber, refBody.sheetNumber),
            refBody.clientNumber, refBody.orderNumber);

    [HttpPost("pick")]
    public IActionResult Pick([FromBody, Valid] GoodsOutPickConfirmation confirmation) =>
        ToResponse(lifecycle.ConfirmPicking(confirmation),
            confirmation.clientNumber, confirmation.orderNumber);

    [HttpPost("finalCheck")]
    public IActionResult FinalCheck([FromBody, Valid] GoodsOutOrderRef refBody) =>
        ToResponse(lifecycle.FinalCheck(refBody.clientNumber, refBody.orderNumber, refBody.sheetNumber),
            refBody.clientNumber, refBody.orderNumber);

    private IActionResult ToResponse(
        GoodsOutOrderLifecycleService.Result res, string? clientNumber, string? orderNumber)
    {
        if (res.Code == GoodsOutOrderLifecycleService.Code.Ok)
        {
            return Ok(new OneApiOkResponse(200, "OK", res.Message));
        }

        var code = res.Code == GoodsOutOrderLifecycleService.Code.NotFound
            ? "E-AKO-MOVM-0003"
            : "E-AKO-MOVM-0004";
        return StatusCode(400, new OneApiErrorResponse(
            clientNumber, null, orderNumber, null, null, null, res.Message, [code]));
    }
}
