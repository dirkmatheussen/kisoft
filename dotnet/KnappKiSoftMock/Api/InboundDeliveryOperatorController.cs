using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Services;
using Microsoft.AspNetCore.Mvc;
using static KnappKiSoftMock.Services.InboundDeliveryLifecycleService;

namespace KnappKiSoftMock.Api;

[ApiController]
[Route("oneapi/v1")]
public sealed class InboundDeliveryOperatorController(InboundDeliveryLifecycleService lifecycle) : ControllerBase
{
    [HttpPost("inboundDelivery/operator/start")]
    public IActionResult Start([FromBody, Valid] InboundDeliveryRef refBody)
    {
        var res = lifecycle.StartProcessing(refBody.clientNumber, refBody.inboundDeliveryNumber);
        return ToResponse(res, refBody.clientNumber, refBody.inboundDeliveryNumber,
            "Processing started, PostInboundDeliveryReply(STARTED) sent");
    }

    [HttpPost("inboundDelivery/operator/loadUnit")]
    public IActionResult LoadUnit([FromBody, Valid] InboundDeliveryLoadUnitReceipt body)
    {
        var res = lifecycle.RecordLoadUnit(body);
        if (res.Code != Code.Ok)
        {
            return ToResponse(res, body.clientNumber, body.inboundDeliveryNumber, null);
        }

        var msg = res.Finished
            ? "Load unit stored, PostStockReceived sent, delivery FINISHED"
            : "Load unit stored, PostStockReceived sent";
        return Ok(new OneApiOkResponse(200, "OK", msg));
    }

    [HttpPost("inboundDelivery/operator/finish")]
    public IActionResult Finish([FromBody, Valid] InboundDeliveryRef refBody)
    {
        var res = lifecycle.FinishProcessing(refBody.clientNumber, refBody.inboundDeliveryNumber);
        return ToResponse(res, refBody.clientNumber, refBody.inboundDeliveryNumber,
            "Processing finished, PostInboundDeliveryReply(FINISHED) sent");
    }

    private IActionResult ToResponse(LifecycleResult res, string? client, string? idn, string? successMsg)
    {
        if (res.Code == Code.Ok)
        {
            return Ok(new OneApiOkResponse(200, "OK", successMsg));
        }

        return BadRequest(new OneApiErrorResponse(
            client,
            idn,
            null,
            null,
            null,
            null,
            res.Message,
            [ToCode(res.Code)]));
    }

    private static string ToCode(Code c) => c switch
    {
        Code.Ok => "",
        Code.NotFound => "E-AKO-MOVM-0003",
        Code.WrongStatus => "E-AKO-MOVM-0004",
        Code.QtyExceedsOpen => "E-AKO-MOVM-0006",
        Code.LineNotFound => "E-AKO-MOVM-0007",
        Code.CompartmentNotEmpty => "E-AKO-MOVM-0008",
        Code.WrongArticle => "E-AKO-MOVM-0009",
        _ => ""
    };
}
