using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Persistence;
using KnappKiSoftMock.Services;
using Microsoft.AspNetCore.Mvc;

namespace KnappKiSoftMock.Api;

[ApiController]
[Route("oneapi/v1")]
public sealed class InboundDeliveryController(
    InboundDeliveryStoreService inboundStore,
    InboundDeliveryLifecycleService lifecycle,
    PackUnitStoreService packUnitStore,
    ReplyCallbackService replyCallbackService) : ControllerBase
{
    [HttpGet("inboundDelivery")]
    public ODataCollectionResponse<InboundDeliveryRead> GetInboundDeliveries(
        [FromQuery(Name = "$filter")] string? filter,
        [FromQuery(Name = "$top")] string? top,
        [FromQuery(Name = "$skip")] string? skip,
        [FromQuery(Name = "$count")] string? count)
    {
        var all = inboundStore.ListAllEntities().Select(ToRead).ToList();
        return ODataQuerySupport.BuildPage(
            ODataQuerySupport.MetadataContext(Request.PathBase.Value, "InboundDeliveries"),
            all,
            ODataQuerySupport.ParseFilter(filter),
            read =>
            {
                var d = read.inboundDelivery;
                return ODataQuerySupport.Fields(
                    "clientNumber", ODataQuerySupport.Str(d?.clientNumber),
                    "inboundDeliveryNumber", ODataQuerySupport.Str(d?.inboundDeliveryNumber),
                    "processingStatus", ODataQuerySupport.Str(read.processingStatus),
                    "supplierNumber", ODataQuerySupport.Str(d?.supplierNumber));
            },
            ODataQuerySupport.ParseTop(top),
            ODataQuerySupport.ParseSkip(skip),
            ODataQuerySupport.ParseCount(count));
    }

    [HttpPost("inboundDelivery")]
    public IActionResult PostInboundDelivery([FromBody, Valid] InboundDelivery request)
    {
        if (inboundStore.Exists(request.clientNumber, request.inboundDeliveryNumber))
        {
            return BadRequest(new OneApiErrorResponse(
                request.clientNumber,
                request.inboundDeliveryNumber,
                null,
                null,
                null,
                null,
                null,
                ["E-AKO-MOVM-0002"]));
        }

        if (request.inboundDeliveryLines is not null)
        {
            var seenArticles = new HashSet<string>();
            foreach (var line in request.inboundDeliveryLines)
            {
                if (line is null) continue;
                if (!seenArticles.Add(line.articleNumber ?? ""))
                {
                    return BadRequest(new OneApiErrorResponse(
                        request.clientNumber,
                        request.inboundDeliveryNumber,
                        null,
                        line.articleNumber,
                        line.packSize?.ToString(),
                        null,
                        "Duplicate article in inbound delivery: " + line.articleNumber,
                        ["E-AKO-MAST-0007"]));
                }

                if (!packUnitStore.Exists(request.clientNumber, line.articleNumber, line.packSize))
                {
                    return BadRequest(new OneApiErrorResponse(
                        request.clientNumber,
                        request.inboundDeliveryNumber,
                        null,
                        line.articleNumber,
                        line.packSize?.ToString(),
                        null,
                        "Unknown article/packSize: " + line.articleNumber + "/" + line.packSize,
                        ["E-AKO-MAST-0001"]));
                }
            }
        }

        inboundStore.CreateNew(request);
        lifecycle.BookExpectedStock(request);

        var reply = new InboundDeliveryReply(
            request.clientNumber,
            request.inboundDeliveryNumber,
            request.businessCase ?? "GOODS_IN",
            "HOST",
            "NEW",
            KiSoftTime.Now(),
            request.inboundDeliveryLines);
        replyCallbackService.SendInboundDeliveryReply(reply);

        return Ok(new OneApiOkResponse(200, "OK", "Inbound delivery created successfully"));
    }

    [HttpPatch("inboundDelivery")]
    public IActionResult PatchInboundDelivery([FromBody, Valid] UpdateInboundDelivery request)
    {
        var existing = inboundStore.Find(request.clientNumber, request.inboundDeliveryNumber);
        if (existing is null)
        {
            return BadRequest(new OneApiErrorResponse(
                request.clientNumber,
                request.inboundDeliveryNumber,
                null,
                null,
                null,
                null,
                null,
                ["E-AKO-MOVM-0003"]));
        }

        var status = existing.ProcessingStatus;
        if (status != "NEW")
        {
            return StatusCode(409, new OneApiErrorResponse(
                request.clientNumber,
                request.inboundDeliveryNumber,
                null,
                null,
                null,
                null,
                "Inbound delivery is active (status=" + status + "); patch not allowed",
                ["E-AKO-MOVM-0005"]));
        }

        var current = inboundStore.ReadPayload(existing);
        var priority = request.priority ?? current.priority ?? 1;
        var lines = new List<InboundDeliveryLine?>(current.inboundDeliveryLines ?? []);
        if (request.addInboundDeliveryLines is not null)
        {
            lines.AddRange(request.addInboundDeliveryLines);
        }
        if (request.deleteLinesByReference is not null)
        {
            lines.RemoveAll(l => l is not null && request.deleteLinesByReference.Contains(l.lineReference));
        }

        var updated = new InboundDelivery(
            current.clientNumber,
            current.inboundDeliveryNumber,
            current.supplierNumber,
            priority,
            current.businessCase,
            request.vasTasks ?? current.vasTasks,
            request.additionalProperties ?? current.additionalProperties,
            lines.Count == 0 ? null : lines);
        inboundStore.Update(existing, updated);
        return Ok(new OneApiOkResponse(200, "OK", "Inbound delivery updated"));
    }

    [HttpDelete("inboundDelivery")]
    public IActionResult DeleteInboundDelivery([FromBody, Valid] InboundDeliveryRef request)
    {
        var existing = inboundStore.Find(request.clientNumber, request.inboundDeliveryNumber);
        if (existing is null)
        {
            return BadRequest(new OneApiErrorResponse(
                request.clientNumber,
                request.inboundDeliveryNumber,
                null,
                null,
                null,
                null,
                null,
                ["E-AKO-MOVM-0003"]));
        }

        var status = existing.ProcessingStatus;
        if (status != "NEW")
        {
            return StatusCode(409, new OneApiErrorResponse(
                request.clientNumber,
                request.inboundDeliveryNumber,
                null,
                null,
                null,
                null,
                "Inbound delivery is active (status=" + status + "); delete not allowed",
                ["E-AKO-MOVM-0005"]));
        }

        var delivery = inboundStore.ReadPayload(existing);
        lifecycle.ReleaseExpectedStock(delivery);
        inboundStore.Delete(request.clientNumber, request.inboundDeliveryNumber);

        var reply = new InboundDeliveryReply(
            delivery.clientNumber,
            delivery.inboundDeliveryNumber,
            delivery.businessCase ?? "GOODS_IN",
            "KISOFT",
            "CANCELLED",
            KiSoftTime.Now(),
            delivery.inboundDeliveryLines);
        replyCallbackService.SendInboundDeliveryReply(reply);
        return Ok(new OneApiOkResponse(200, "OK", "Inbound delivery cancelled"));
    }

    private InboundDeliveryRead ToRead(InboundDeliveryEntity entity) =>
        new(entity.ProcessingStatus, inboundStore.ReadPayload(entity));
}
