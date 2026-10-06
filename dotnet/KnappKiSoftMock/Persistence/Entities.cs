namespace KnappKiSoftMock.Persistence;

public sealed class PackUnitEntity
{
    public long Id { get; set; }
    public string ClientNumber { get; set; } = "";
    public string ArticleNumber { get; set; } = "";
    public string PackSize { get; set; } = "";
    public string PayloadJson { get; set; } = "";
}

public sealed class MasterdataSessionDeltaEntity
{
    public long Id { get; set; }
    public string Domain { get; set; } = "";
    public string ClientNumber { get; set; } = "";
    public string KeyValue { get; set; } = "";
}

public sealed class InboundDeliveryEntity
{
    public long Id { get; set; }
    public string ClientNumber { get; set; } = "";
    public string InboundDeliveryNumber { get; set; } = "";
    public string ProcessingStatus { get; set; } = "";
    public string PayloadJson { get; set; } = "";
}

public sealed class InboundDeliveryProgressEntity
{
    public long Id { get; set; }
    public string ClientNumber { get; set; } = "";
    public string InboundDeliveryNumber { get; set; } = "";
    public string LineReference { get; set; } = "";
    public string ArticleNumber { get; set; } = "";
    public string PackSize { get; set; } = "";
    public int ExpectedQuantity { get; set; }
    public int ReceivedQuantity { get; set; }
    public int OpenQuantity => ExpectedQuantity - ReceivedQuantity;
}

public sealed class ToteCompartmentEntity
{
    public long Id { get; set; }
    public string ClientNumber { get; set; } = "";
    public string LoadUnitCode { get; set; } = "";
    public string Compartment { get; set; } = "";
    public string ArticleNumber { get; set; } = "";
    public string PackSize { get; set; } = "";
    public int Quantity { get; set; }
}

public sealed class AsrsStockEntity
{
    public long Id { get; set; }
    public string ClientNumber { get; set; } = "";
    public string ArticleNumber { get; set; } = "";
    public string PackSize { get; set; } = "";
    public int Quantity { get; set; }
    public string? StockType { get; set; }
    public string? LotNumber { get; set; }
    public string? DateMark { get; set; }
    public string? SerialNumber { get; set; }
    public string ReservationCode { get; set; } = "";
    public string? StockLockReasonsJson { get; set; }
}

public sealed class GoodsOutOrderEntity
{
    public long Id { get; set; }
    public string ClientNumber { get; set; } = "";
    public string OrderNumber { get; set; } = "";
    public string SheetNumber { get; set; } = "";
    public string ProcessingStatus { get; set; } = "";
    public string PayloadJson { get; set; } = "";
    public string? PickResultJson { get; set; }
}

public sealed class InventoryRequestEntity
{
    public long Id { get; set; }
    public string ClientNumber { get; set; } = "";
    public string RequestNumber { get; set; } = "";
    public string ProcessingStatus { get; set; } = "";
    public string PayloadJson { get; set; } = "";
}
