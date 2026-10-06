using System.Globalization;

namespace KnappKiSoftMock.Services;

/// <summary>
/// Timestamps in the exact format of Java <c>Instant.now().toString()</c> (DateTimeFormatter.ISO_INSTANT):
/// UTC with a <c>Z</c> suffix and the fraction printed in groups of three digits only as far as needed
/// (<c>2026-10-06T06:41:00Z</c>, <c>…00.123Z</c>, <c>…00.123456Z</c>, <c>…00.123456700Z</c>).
/// </summary>
public static class KiSoftTime
{
    public static string Now() => Format(DateTime.UtcNow);

    public static string Format(DateTime utc)
    {
        var seconds = utc.ToString("yyyy-MM-dd'T'HH:mm:ss", CultureInfo.InvariantCulture);
        var nanos = utc.Ticks % TimeSpan.TicksPerSecond * 100;
        if (nanos == 0)
        {
            return seconds + "Z";
        }
        if (nanos % 1_000_000 == 0)
        {
            return seconds + "." + (nanos / 1_000_000).ToString("000", CultureInfo.InvariantCulture) + "Z";
        }
        if (nanos % 1_000 == 0)
        {
            return seconds + "." + (nanos / 1_000).ToString("000000", CultureInfo.InvariantCulture) + "Z";
        }
        return seconds + "." + nanos.ToString("000000000", CultureInfo.InvariantCulture) + "Z";
    }
}
