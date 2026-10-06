using System.Diagnostics;
using System.Globalization;
using System.Reflection;
using System.Text;
using KnappKiSoftMock.Options;
using Microsoft.Data.Sqlite;

namespace KnappKiSoftMock.Services;

/// <summary>
/// One-time copy of the Java H2 file (<c>kisoftmock.mv.db</c>) into the SQLite database.
/// Runs only when SQLite has no business rows yet. Later starts leave both files alone.
/// </summary>
public static class H2ToSqliteImporter
{
    private const string NullToken = "§NULL§";
    private const string JarResource = "KnappKiSoftMock.h2-2.2.224.jar";

    private sealed record TableMap(string H2Table, string SqliteTable, string[] H2Columns, string[] Columns, int[] Integers);

    private static readonly TableMap[] Tables =
    [
        new("pack_unit", "PackUnits",
            ["id", "client_number", "article_number", "pack_size", "payload_json"],
            ["Id", "ClientNumber", "ArticleNumber", "PackSize", "PayloadJson"],
            [0]),
        new("masterdata_session_delta", "MasterdataDeltas",
            ["id", "domain", "client_number", "key_value"],
            ["Id", "Domain", "ClientNumber", "KeyValue"],
            [0]),
        new("inbound_delivery", "InboundDeliveries",
            ["id", "client_number", "inbound_delivery_number", "processing_status", "payload_json"],
            ["Id", "ClientNumber", "InboundDeliveryNumber", "ProcessingStatus", "PayloadJson"],
            [0]),
        new("inbound_delivery_progress", "InboundProgress",
            ["id", "client_number", "inbound_delivery_number", "line_reference", "article_number", "pack_size", "expected_quantity", "received_quantity"],
            ["Id", "ClientNumber", "InboundDeliveryNumber", "LineReference", "ArticleNumber", "PackSize", "ExpectedQuantity", "ReceivedQuantity"],
            [0, 6, 7]),
        new("tote_compartment", "ToteCompartments",
            ["id", "client_number", "load_unit_code", "compartment", "article_number", "pack_size", "quantity"],
            ["Id", "ClientNumber", "LoadUnitCode", "Compartment", "ArticleNumber", "PackSize", "Quantity"],
            [0, 6]),
        new("asrs_stock", "AsrsStock",
            ["id", "client_number", "article_number", "pack_size", "quantity", "stock_type", "lot_number", "date_mark", "serial_number", "reservation_code", "stock_lock_reasons"],
            ["Id", "ClientNumber", "ArticleNumber", "PackSize", "Quantity", "StockType", "LotNumber", "DateMark", "SerialNumber", "ReservationCode", "StockLockReasonsJson"],
            [0, 4]),
        new("goods_out_order", "GoodsOutOrders",
            ["id", "client_number", "order_number", "sheet_number", "processing_status", "payload_json", "pick_result_json"],
            ["Id", "ClientNumber", "OrderNumber", "SheetNumber", "ProcessingStatus", "PayloadJson", "PickResultJson"],
            [0]),
        new("inventory_request", "InventoryRequests",
            ["id", "client_number", "request_number", "processing_status", "payload_json"],
            ["Id", "ClientNumber", "RequestNumber", "ProcessingStatus", "PayloadJson"],
            [0]),
    ];

    private static readonly HashSet<string> NullableText = new(StringComparer.Ordinal)
    {
        "StockType", "LotNumber", "DateMark", "SerialNumber", "StockLockReasonsJson", "PickResultJson"
    };

    public static void ImportIfNeeded(string? connectionString)
    {
        var sqlitePath = DeployConfiguration.SqliteDataSource(connectionString);
        if (string.IsNullOrWhiteSpace(sqlitePath)
            || sqlitePath.Equals(":memory:", StringComparison.OrdinalIgnoreCase)
            || string.IsNullOrWhiteSpace(connectionString))
        {
            return;
        }

        try
        {
            using var connection = new SqliteConnection(connectionString);
            connection.Open();
            EnsureMarkerTable(connection);
            if (AlreadyImported(connection)) return;

            var h2File = FindH2File(sqlitePath);
            if (h2File == null) return;

            if (SqliteHasRows(connection))
            {
                MarkImported(connection, h2File, "skipped: SQLite already had rows");
                Console.WriteLine("H2 database left in place; SQLite already has rows (" + h2File + ")");
                return;
            }

            var summary = Copy(connection, h2File);
            Console.WriteLine("Copied H2 database " + h2File + " into SQLite: " + summary);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("H2 to SQLite copy failed and will be retried on the next start: " + ex.Message);
        }
    }

    private static string Copy(SqliteConnection connection, string h2File)
    {
        var tempDir = Directory.CreateTempSubdirectory("knapp-h2-import-");
        try
        {
            var columnsPath = Path.Combine(tempDir.FullName, "columns.csv");
            var columnsScript = Path.Combine(tempDir.FullName, "columns.sql");
            File.WriteAllText(columnsScript,
                "CALL CSVWRITE(" + SqlString(columnsPath)
                + ", " + SqlString("SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC'")
                + ", " + SqlString("charset=UTF-8 writeColumnHeader=false null=" + NullToken) + ");\n");
            RunH2Export(h2File, columnsScript);
            var present = ReadPresentColumns(columnsPath);

            var scriptPath = Path.Combine(tempDir.FullName, "export.sql");
            var csvPaths = new Dictionary<TableMap, string>();
            var script = new StringBuilder();
            foreach (var table in Tables)
            {
                var csv = Path.Combine(tempDir.FullName, table.H2Table + ".csv");
                csvPaths[table] = csv;
                script.Append("CALL CSVWRITE(")
                    .Append(SqlString(csv))
                    .Append(", ")
                    .Append(SqlString("SELECT " + SelectList(table, present) + " FROM " + table.H2Table))
                    .Append(", ")
                    .Append(SqlString("charset=UTF-8 writeColumnHeader=false null=" + NullToken))
                    .Append(");\n");
            }

            File.WriteAllText(scriptPath, script.ToString());
            RunH2Export(h2File, scriptPath);

            using var tx = connection.BeginTransaction();
            var counts = new Dictionary<string, long>();
            foreach (var table in Tables)
            {
                counts[table.H2Table] = InsertCsv(connection, tx, table, csvPaths[table]);
            }

            var summary = string.Join(", ", counts.Select(pair => pair.Key + "=" + pair.Value.ToString(CultureInfo.InvariantCulture)));
            MarkImported(connection, h2File, summary, tx);
            tx.Commit();
            return summary;
        }
        finally
        {
            try
            {
                tempDir.Delete(recursive: true);
            }
            catch (IOException)
            {
                // The copy already committed. A leftover temp directory is harmless.
            }
        }
    }

    private static string SelectList(TableMap table, Dictionary<string, HashSet<string>> present)
    {
        present.TryGetValue(table.H2Table.ToUpperInvariant(), out var columns);
        var parts = new string[table.H2Columns.Length];
        for (var i = 0; i < table.H2Columns.Length; i++)
        {
            var name = table.H2Columns[i];
            parts[i] = columns != null && columns.Contains(name.ToUpperInvariant())
                ? name
                : "CAST(NULL AS VARCHAR)";
        }

        return string.Join(", ", parts);
    }

    private static Dictionary<string, HashSet<string>> ReadPresentColumns(string csvPath)
    {
        var present = new Dictionary<string, HashSet<string>>(StringComparer.OrdinalIgnoreCase);
        if (!File.Exists(csvPath)) return present;
        foreach (var row in ReadCsv(csvPath))
        {
            if (row.Count < 2) continue;
            var tableName = row[0];
            var columnName = row[1];
            if (tableName == null || columnName == null) continue;
            if (!present.TryGetValue(tableName, out var columns))
            {
                columns = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                present[tableName] = columns;
            }

            columns.Add(columnName);
        }

        return present;
    }

    private static void RunH2Export(string h2File, string scriptPath)
    {
        var jar = ExtractH2Jar();
        var basePath = h2File[..^".mv.db".Length].Replace('\\', '/');
        var url = "jdbc:h2:file:" + basePath + ";ACCESS_MODE_DATA=r;IFEXISTS=TRUE";
        using var process = new Process();
        process.StartInfo = new ProcessStartInfo
        {
            FileName = "java",
            RedirectStandardError = true,
            RedirectStandardOutput = true,
            UseShellExecute = false
        };
        process.StartInfo.ArgumentList.Add("-cp");
        process.StartInfo.ArgumentList.Add(jar);
        process.StartInfo.ArgumentList.Add("org.h2.tools.RunScript");
        process.StartInfo.ArgumentList.Add("-url");
        process.StartInfo.ArgumentList.Add(url);
        process.StartInfo.ArgumentList.Add("-user");
        process.StartInfo.ArgumentList.Add("sa");
        process.StartInfo.ArgumentList.Add("-script");
        process.StartInfo.ArgumentList.Add(scriptPath);
        process.Start();
        var stderrTask = process.StandardError.ReadToEndAsync();
        var stdoutTask = process.StandardOutput.ReadToEndAsync();
        if (!process.WaitForExit(TimeSpan.FromMinutes(10)))
        {
            try { process.Kill(entireProcessTree: true); } catch (Exception) { /* already gone */ }
            throw new InvalidOperationException("H2 export timed out");
        }

        var stderr = stderrTask.GetAwaiter().GetResult();
        var stdout = stdoutTask.GetAwaiter().GetResult();
        if (process.ExitCode != 0)
        {
            var detail = string.IsNullOrWhiteSpace(stderr) ? stdout : stderr;
            if (detail.Length > 500) detail = detail[..500];
            throw new InvalidOperationException("H2 export failed: " + detail.Trim());
        }
    }

    private static string ExtractH2Jar()
    {
        var dir = Path.Combine(Path.GetTempPath(), "knapp-kisoft-mock");
        Directory.CreateDirectory(dir);
        var path = Path.Combine(dir, "h2-2.2.224.jar");
        if (File.Exists(path) && new FileInfo(path).Length > 1_000_000) return path;

        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(JarResource)
            ?? throw new InvalidOperationException("Embedded H2 library " + JarResource + " was not found");
        using var file = File.Create(path);
        stream.CopyTo(file);
        return path;
    }

    private static long InsertCsv(SqliteConnection connection, SqliteTransaction tx, TableMap table, string csvPath)
    {
        var columnList = string.Join(", ", table.Columns.Select(column => "\"" + column + "\""));
        var parameterList = string.Join(", ", table.Columns.Select((_, index) => "$p" + index.ToString(CultureInfo.InvariantCulture)));
        using var command = connection.CreateCommand();
        command.Transaction = tx;
        command.CommandText = "INSERT INTO \"" + table.SqliteTable + "\" (" + columnList + ") VALUES (" + parameterList + ")";
        var parameters = new SqliteParameter[table.Columns.Length];
        for (var i = 0; i < table.Columns.Length; i++)
        {
            parameters[i] = command.Parameters.Add("$p" + i.ToString(CultureInfo.InvariantCulture), SqliteType.Text);
        }

        if (!File.Exists(csvPath)) return 0;

        var integers = new HashSet<int>(table.Integers);
        long count = 0;
        long maxId = 0;
        foreach (var row in ReadCsv(csvPath))
        {
            if (row.Count == 0) continue;
            if (row.Count != table.Columns.Length)
            {
                throw new InvalidOperationException(
                    table.H2Table + " row has " + row.Count + " values, expected " + table.Columns.Length);
            }

            for (var i = 0; i < row.Count; i++)
            {
                var value = row[i];
                if (integers.Contains(i))
                {
                    parameters[i].SqliteType = SqliteType.Integer;
                    var number = string.IsNullOrEmpty(value) ? 0L : long.Parse(value, CultureInfo.InvariantCulture);
                    parameters[i].Value = number;
                    if (i == 0 && number > maxId) maxId = number;
                }
                else
                {
                    parameters[i].SqliteType = SqliteType.Text;
                    if (value == null && NullableText.Contains(table.Columns[i])) parameters[i].Value = DBNull.Value;
                    else parameters[i].Value = value ?? "";
                }
            }

            command.ExecuteNonQuery();
            count++;
        }

        if (count > 0)
        {
            using var sequence = connection.CreateCommand();
            sequence.Transaction = tx;
            sequence.Parameters.AddWithValue("$name", table.SqliteTable);
            sequence.Parameters.AddWithValue("$seq", maxId);
            sequence.CommandText = "UPDATE sqlite_sequence SET seq = $seq WHERE name = $name";
            if (sequence.ExecuteNonQuery() == 0)
            {
                sequence.CommandText = "INSERT INTO sqlite_sequence(name, seq) VALUES ($name, $seq)";
                sequence.ExecuteNonQuery();
            }
        }

        return count;
    }

    private static List<List<string?>> ReadCsv(string path)
    {
        var rows = new List<List<string?>>();
        using var reader = new StreamReader(path);
        var field = new StringBuilder();
        var row = new List<string?>();
        var quoted = false;
        var sawQuote = false;
        while (true)
        {
            var next = reader.Read();
            if (next < 0)
            {
                if (field.Length > 0 || sawQuote || row.Count > 0)
                {
                    row.Add(Field(field, sawQuote));
                    rows.Add(row);
                }

                return rows;
            }

            var ch = (char)next;
            if (quoted)
            {
                if (ch == '"')
                {
                    if (reader.Peek() == '"')
                    {
                        reader.Read();
                        field.Append('"');
                    }
                    else
                    {
                        quoted = false;
                    }
                }
                else
                {
                    field.Append(ch);
                }

                continue;
            }

            if (ch == '"')
            {
                quoted = true;
                sawQuote = true;
                continue;
            }

            if (ch == ',')
            {
                row.Add(Field(field, sawQuote));
                field.Clear();
                sawQuote = false;
                continue;
            }

            if (ch == '\n')
            {
                row.Add(Field(field, sawQuote));
                field.Clear();
                sawQuote = false;
                rows.Add(row);
                row = [];
                continue;
            }

            if (ch != '\r') field.Append(ch);
        }
    }

    private static string? Field(StringBuilder field, bool sawQuote)
    {
        var text = field.ToString();
        if (!sawQuote && text == NullToken) return null;
        return text;
    }

    private static void EnsureMarkerTable(SqliteConnection connection)
    {
        using var command = connection.CreateCommand();
        command.CommandText = """
            CREATE TABLE IF NOT EXISTS "H2ImportState" (
                "Id" INTEGER PRIMARY KEY,
                "SourcePath" TEXT NOT NULL,
                "ImportedAt" TEXT NOT NULL,
                "Summary" TEXT NOT NULL
            )
            """;
        command.ExecuteNonQuery();
    }

    private static bool AlreadyImported(SqliteConnection connection)
    {
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM \"H2ImportState\"";
        return Convert.ToInt64(command.ExecuteScalar(), CultureInfo.InvariantCulture) > 0;
    }

    private static bool SqliteHasRows(SqliteConnection connection)
    {
        foreach (var table in Tables)
        {
            using var command = connection.CreateCommand();
            command.CommandText = "SELECT COUNT(*) FROM \"" + table.SqliteTable + "\"";
            if (Convert.ToInt64(command.ExecuteScalar(), CultureInfo.InvariantCulture) > 0) return true;
        }

        return false;
    }

    private static void MarkImported(SqliteConnection connection, string h2File, string summary, SqliteTransaction? transaction = null)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO "H2ImportState" ("Id", "SourcePath", "ImportedAt", "Summary")
            VALUES (1, $path, $at, $summary)
            """;
        command.Parameters.AddWithValue("$path", h2File);
        command.Parameters.AddWithValue("$at", DateTimeOffset.UtcNow.ToString("O", CultureInfo.InvariantCulture));
        command.Parameters.AddWithValue("$summary", summary);
        command.ExecuteNonQuery();
    }

    private static string? FindH2File(string sqlitePath)
    {
        var candidates = new List<string>();
        var fromEnv = Environment.GetEnvironmentVariable("KNAPP_H2_FILE");
        if (!string.IsNullOrWhiteSpace(fromEnv)) candidates.Add(ToMvDb(fromEnv.Trim()));

        var jdbc = Environment.GetEnvironmentVariable("SPRING_DATASOURCE_URL");
        if (!string.IsNullOrWhiteSpace(jdbc))
        {
            const string marker = "jdbc:h2:file:";
            var idx = jdbc.IndexOf(marker, StringComparison.OrdinalIgnoreCase);
            if (idx >= 0)
            {
                var path = jdbc[(idx + marker.Length)..];
                var semi = path.IndexOf(';');
                if (semi >= 0) path = path[..semi];
                if (!string.IsNullOrWhiteSpace(path)) candidates.Add(ToMvDb(path.Trim()));
            }
        }

        candidates.Add(ToMvDb(sqlitePath));

        foreach (var candidate in candidates)
        {
            var full = Path.GetFullPath(candidate);
            if (File.Exists(full)) return full;
        }

        return null;
    }

    private static string ToMvDb(string path)
    {
        if (path.EndsWith(".mv.db", StringComparison.OrdinalIgnoreCase)) return path;
        if (path.EndsWith(".db", StringComparison.OrdinalIgnoreCase)) return path[..^".db".Length] + ".mv.db";
        return path + ".mv.db";
    }

    private static string SqlString(string value) => "'" + value.Replace("'", "''", StringComparison.Ordinal) + "'";
}
