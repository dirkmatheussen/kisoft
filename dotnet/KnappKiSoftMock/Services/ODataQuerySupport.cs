using System.Text.RegularExpressions;
using KnappKiSoftMock.Api.Dtos;

namespace KnappKiSoftMock.Services;

public static partial class ODataQuerySupport
{
    private const int DefaultTop = 100;
    private const int MaxTop = 1000;

    /// <summary>
    /// Java: <c>filter.split("\\s+and\\s+", -1)</c> (case-sensitive <c>and</c>) and
    /// <c>EQ_FILTER.matcher(part.trim()).matches()</c> (whole part must be <c>field eq 'value'</c>, <c>eq</c> case-insensitive).
    /// </summary>
    public static Dictionary<string, string> ParseFilter(string? filter)
    {
        if (string.IsNullOrWhiteSpace(filter)) return [];
        var conditions = new Dictionary<string, string>();
        foreach (var part in AndSplitter().Split(filter))
        {
            var match = EqFilter().Match(part.Trim());
            if (match.Success)
            {
                conditions[match.Groups[1].Value] = match.Groups[2].Value;
            }
        }
        return conditions;
    }

    public static int ParseTop(string? top)
    {
        if (string.IsNullOrWhiteSpace(top) || !int.TryParse(top.Trim(), out var value) || value < 0)
        {
            return DefaultTop;
        }
        return Math.Min(value, MaxTop);
    }

    public static int ParseSkip(string? skip)
    {
        if (string.IsNullOrWhiteSpace(skip) || !int.TryParse(skip.Trim(), out var value))
        {
            return 0;
        }
        return Math.Max(value, 0);
    }

    public static bool ParseCount(string? count) =>
        count is not null && count.Trim().Equals("true", StringComparison.OrdinalIgnoreCase);

    public static ODataCollectionResponse<T> BuildPage<T>(
        string context,
        IReadOnlyList<T> source,
        IReadOnlyDictionary<string, string> filters,
        Func<T, IReadOnlyDictionary<string, string?>> fieldValues,
        int top,
        int skip,
        bool includeCount) =>
        BuildPage(context, source, filters, fieldValues, item => item, top, skip, includeCount);

    /// <summary>
    /// Filter and page on the source rows, then map only the returned page (Java maps the entity page
    /// after the database query, so mapping errors only surface for rows in the page).
    /// </summary>
    public static ODataCollectionResponse<TOut> BuildPage<TSource, TOut>(
        string context,
        IReadOnlyList<TSource> source,
        IReadOnlyDictionary<string, string> filters,
        Func<TSource, IReadOnlyDictionary<string, string?>> fieldValues,
        Func<TSource, TOut> map,
        int top,
        int skip,
        bool includeCount)
    {
        var matched = source.Where(item => Matches(filters, fieldValues(item))).ToList();
        var from = Math.Min(skip, matched.Count);
        var to = Math.Min(from + top, matched.Count);
        return new ODataCollectionResponse<TOut>(
            context,
            includeCount ? matched.Count : null,
            matched.GetRange(from, to - from).Select(map).ToList());
    }

    public static string MetadataContext(string? basePath, string entitySet)
    {
        var path = string.IsNullOrWhiteSpace(basePath) ? "" : basePath.TrimEnd('/');
        return path + "/oneapi/v1/$metadata#" + entitySet;
    }

    /// <summary>Java in-memory <c>fields()</c>: null values become "" (so <c>eq ''</c> matches a missing value).</summary>
    public static Dictionary<string, string?> Fields(params string?[] pairs)
    {
        var map = ColumnFields(pairs);
        foreach (var key in map.Keys.ToList())
        {
            map[key] ??= "";
        }
        return map;
    }

    /// <summary>Java SQL <c>equalitySpecification</c>: a NULL column never matches, not even <c>eq ''</c>.</summary>
    public static Dictionary<string, string?> ColumnFields(params string?[] pairs)
    {
        if (pairs.Length % 2 != 0)
        {
            throw new ArgumentException("fields() requires key/value pairs");
        }
        var map = new Dictionary<string, string?>();
        for (var i = 0; i < pairs.Length; i += 2)
        {
            map[pairs[i] ?? ""] = pairs[i + 1];
        }
        return map;
    }

    public static string Str(string? value) => value ?? "";
    public static string Str(int? value) => value?.ToString() ?? "";

    private static bool Matches(IReadOnlyDictionary<string, string> filters, IReadOnlyDictionary<string, string?> values)
    {
        foreach (var filter in filters)
        {
            if (!values.TryGetValue(filter.Key, out var actual) || actual is null || actual != filter.Value)
            {
                return false;
            }
        }
        return true;
    }

    [GeneratedRegex(@"\s+and\s+")]
    private static partial Regex AndSplitter();

    [GeneratedRegex(@"^([\w.]+)\s+eq\s+'([^']*)'$", RegexOptions.IgnoreCase)]
    private static partial Regex EqFilter();
}
