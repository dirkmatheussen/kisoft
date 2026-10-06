using KnappKiSoftMock.Api.Validation;
using KnappKiSoftMock.Api.Dtos;
using KnappKiSoftMock.Options;
using KnappKiSoftMock.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace KnappKiSoftMock.Api;

/// <summary>
/// Mock-only bulk inventory load for APIC load tests (not part of KiSoft One API).
/// </summary>
[ApiController]
[Route("oneapi/v1/inventoryItem/operator")]
public sealed class InventoryImportController(
    InventoryImportService importService,
    IOptions<MockOptions> options) : ControllerBase
{
    private readonly MockOptions _options = options.Value;

    [HttpPost("import")]
    [Consumes("application/json")]
    public OneApiOkResponse ImportReport(
        [FromBody, Valid] InventoryReport report,
        [FromQuery] bool uniquifyArticles = true,
        [FromQuery] bool replaceAll = false)
    {
        var result = importService.ImportItems(report.stockInventory, uniquifyArticles, replaceAll);
        return new OneApiOkResponse(
            200,
            "OK",
            "Imported " + result.RowsWritten + " of " + result.RowsRead
            + " stockInventory rows (uniquifyArticles=" + result.UniquifyArticles
            + ", replaceAll=" + result.ReplaceAll + ")");
    }

    [HttpPost("importFile")]
    [Consumes("application/json")]
    public IActionResult ImportFile(
        [FromBody] Dictionary<string, string> body,
        [FromQuery] bool uniquifyArticles = true,
        [FromQuery] bool replaceAll = false)
    {
        var pathValue = body.TryGetValue("path", out var p) ? p : null;
        if (string.IsNullOrWhiteSpace(pathValue))
        {
            return BadRequest(new OneApiOkResponse(
                400, "BAD_REQUEST", "Body must include non-blank \"path\""));
        }

        var path = ResolveWithinImportDir(pathValue);
        if (path is null)
        {
            return BadRequest(new OneApiOkResponse(
                400, "BAD_REQUEST", "path must resolve inside the configured import directory"));
        }

        if (!System.IO.File.Exists(path) || Directory.Exists(path))
        {
            return BadRequest(new OneApiOkResponse(
                400, "BAD_REQUEST", "No importable file at the requested path"));
        }

        try
        {
            var result = importService.ImportFromFile(path, uniquifyArticles, replaceAll);
            return Ok(new OneApiOkResponse(
                200,
                "OK",
                "Imported " + result.RowsWritten + " of " + result.RowsRead
                + " stockInventory rows"
                + " (uniquifyArticles=" + result.UniquifyArticles
                + ", replaceAll=" + result.ReplaceAll + ")"));
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("importFile failed for a path under the import directory: " + ex.Message);
            return BadRequest(new OneApiOkResponse(
                400, "BAD_REQUEST", "Import failed: file is not a valid InventoryReport JSON"));
        }
    }

    /// <summary>
    /// Java: <c>Path.of(importDir).toAbsolutePath().normalize()</c> (relative to the process working
    /// directory, blank → working directory), <c>base.resolve(pathValue).normalize()</c>, and a
    /// case-sensitive, path-component based <c>startsWith</c> check.
    /// </summary>
    private string? ResolveWithinImportDir(string pathValue)
    {
        var baseDir = TrimSeparators(Path.GetFullPath(ImportDirectory()));
        var resolved = TrimSeparators(Path.IsPathRooted(pathValue)
            ? Path.GetFullPath(pathValue)
            : Path.GetFullPath(Path.Combine(baseDir, pathValue)));

        return resolved.Equals(baseDir, StringComparison.Ordinal)
               || resolved.StartsWith(baseDir + Path.DirectorySeparatorChar, StringComparison.Ordinal)
            ? resolved
            : null;
    }

    private static string TrimSeparators(string path)
    {
        var trimmed = path.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        return trimmed.Length == 0 ? Path.DirectorySeparatorChar.ToString() : trimmed;
    }

    private string ImportDirectory()
    {
        var configured = _options.ImportDir;
        if (string.IsNullOrEmpty(configured)) return Directory.GetCurrentDirectory();
        return Path.IsPathRooted(configured)
            ? configured
            : Path.Combine(Directory.GetCurrentDirectory(), configured);
    }
}
