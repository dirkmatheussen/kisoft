namespace KnappKiSoftMock.Api.Dtos;

public record InventoryReport(
    string? requestNumber,
    List<StockInventory?>? stockInventory
);
