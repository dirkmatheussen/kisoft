namespace KnappKiSoftMock.Api.Dtos;

public record GoodsOutOrderReplyLine(
    string? uuid,
    string? prjContainerID,
    string? lineReference,
    int? processedQuantity,
    string? processingResult,
    string? processingError,
    List<PickedStock?>? pickedStock
);
