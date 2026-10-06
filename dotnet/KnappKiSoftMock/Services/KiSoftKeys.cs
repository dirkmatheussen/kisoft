namespace KnappKiSoftMock.Services;

public static class ReservationCodes
{
    public static string Normalize(string? value) =>
        string.IsNullOrWhiteSpace(value) ? "" : value.Trim();
}

public static class PackSizeKeys
{
    public static string? ToKey(int? packSize) => packSize?.ToString();
}

public static class SheetNumbers
{
    public static string? ToKey(int? sheetNumber) => sheetNumber?.ToString();
}

public static class StockLockReasons
{
    public static readonly IReadOnlyList<string> All =
    [
        "DEFAULT",
        "EXPIRED",
        "HOST",
        "LOCATION_LOCKED",
        "LOCKED_FOR_VISION_CHECK",
        "LOST",
        "QS_REQ",
        "SRS_SYSTEM_BROKEN",
        "SUBSYSTEM_LOCKED",
        "TIME_TO_EXPIRE"
    ];

    public static List<string> Invalid(IReadOnlyList<string>? reasons)
    {
        if (reasons is null) return [];
        return reasons.Where(r => r is null || !All.Contains(r)).ToList();
    }
}

public static class PrjContainerIds
{
    public const string SitePrefix = "001";

    public static string ForLine(string? orderNumber, string? lineReference)
    {
        var key = (orderNumber ?? "") + "|" + (lineReference ?? "");
        var suffix = FloorMod(JavaStringHash(key), 100_000_000);
        return SitePrefix + suffix.ToString("D8");
    }

    private static int JavaStringHash(string value)
    {
        var hash = 0;
        foreach (var c in value)
        {
            hash = unchecked(31 * hash + c);
        }
        return hash;
    }

    private static int FloorMod(int value, int modulus)
    {
        var remainder = value % modulus;
        return remainder < 0 ? remainder + modulus : remainder;
    }
}

public record AsrsStockAttributes(
    string? StockType,
    string? LotNumber,
    string? DateMark,
    string? SerialNumber,
    string? ReservationCode,
    List<string>? StockLockReasons)
{
    public static AsrsStockAttributes Empty() => new(null, null, null, null, null, null);
}
