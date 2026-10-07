# Ubuntu deployment (systemd)

Requires **Java 17+** (`openjdk-17-jre-headless` or similar).

## Install

```bash
# Build on the server or copy the JAR from CI
mvn -q clean package -DskipTests

sudo useradd --system --home /opt/knapp-kisoft-mock --shell /usr/sbin/nologin knapp-mock 2>/dev/null || true
sudo mkdir -p /opt/knapp-kisoft-mock/data /etc/knapp-kisoft-mock
sudo cp target/knapp-kisoft-mock-4.0.10.jar /opt/knapp-kisoft-mock/knapp-kisoft-mock.jar
sudo chown -R knapp-mock:knapp-mock /opt/knapp-kisoft-mock

sudo cp deploy/knapp-kisoft-mock.env.example /etc/knapp-kisoft-mock/env
sudo chmod 600 /etc/knapp-kisoft-mock/env
# Edit MOCK_UI_PASSWORD:
sudo nano /etc/knapp-kisoft-mock/env

sudo cp deploy/knapp-kisoft-mock.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now knapp-kisoft-mock
```



## Manage

```bash
sudo systemctl status knapp-kisoft-mock
sudo journalctl -u knapp-kisoft-mock -f
sudo systemctl restart knapp-kisoft-mock
```

Default listen address: `http://<host>:8084/kisoft/` (see `server.port` and `context-path` in `application.yml`).

## .NET service

One self-contained executable, same idea as the Spring Boot JAR: runtime, app, and native libraries are inside the file. The server does not need a separate .NET install. The binary is built for Ubuntu x64 (`dist/linux-x64/knapp-kisoft-mock`). Stop the Java unit first; both listen on port 8084 and both read `/etc/knapp-kisoft-mock/env`.

The .NET process uses the same variables as Java: `MOCK_UI_PASSWORD`, `KNAPP_REPLY_CALLBACK_URL`, `KNAPP_WEBHOOK_*`, `KNAPP_H2_FILE` (SQLite file `<path>.db` next to the H2 file), `KNAPP_MOCK_*`, `SERVER_PORT`, `SERVER_SERVLET_CONTEXT_PATH`, and `SPRING_APPLICATION_JSON`. Command-line `--knapp.mock.*` and `--server.port` still override those.

On the first start, when `kisoftmock.mv.db` is present and the SQLite file has no rows yet, the process copies the H2 tables into SQLite. That step runs `java` once and is skipped after it succeeds. Stop the Java service before this start so the H2 file is not locked.

### Build (on the Mac)

```bash
dotnet publish dotnet/KnappKiSoftMock/KnappKiSoftMock.csproj -p:PublishProfile=linux-x64
shasum -a 256 dist/linux-x64/knapp-kisoft-mock
```

Only two files go to the server; everything else (`appsettings*.json`, `wwwroot`, Swagger overlay, H2 jar, README) is inside the executable:

| Local file | Server path |
|---|---|
| `dist/linux-x64/knapp-kisoft-mock` | `/opt/knapp-kisoft-mock/knapp-kisoft-mock` |
| `deploy/knapp-kisoft-mock-dotnet.service` | `/etc/systemd/system/knapp-kisoft-mock-dotnet.service` |

Do **not** copy the local `deploy/knapp-kisoft-mock.env`: the server keeps its own `/etc/knapp-kisoft-mock/env`.

```bash
scp dist/linux-x64/knapp-kisoft-mock deploy/knapp-kisoft-mock-dotnet.service <user>@<server>:/tmp/
```

### Switch from the JAR to the .NET service (on the server)

Prerequisites already met by the Java setup: user `knapp-mock`, `/opt/knapp-kisoft-mock` (owned by `knapp-mock`), `/etc/knapp-kisoft-mock/env` with a non-empty `MOCK_UI_PASSWORD`, `java` on the PATH (used once for the H2 → SQLite copy). `libicu` and OpenSSL 3 must be present (`ldconfig -p | grep -E 'libicu|libssl'`; otherwise `apt install libicu74` or set `DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1` in the env file).

```bash
sha256sum /tmp/knapp-kisoft-mock                      # compare with the Mac

sudo systemctl disable --now knapp-kisoft-mock        # same port 8084, and the H2 file must not be locked

sudo install -o knapp-mock -g knapp-mock -m 755 /tmp/knapp-kisoft-mock /opt/knapp-kisoft-mock/knapp-kisoft-mock
sudo install -d -o knapp-mock -g knapp-mock -m 750 /opt/knapp-kisoft-mock/.net-extract
sudo install -m 644 /tmp/knapp-kisoft-mock-dotnet.service /etc/systemd/system/

sudo systemctl daemon-reload
sudo systemctl enable --now knapp-kisoft-mock-dotnet
```

Check:

```bash
sudo systemctl status knapp-kisoft-mock-dotnet --no-pager
sudo journalctl -u knapp-kisoft-mock-dotnet -n 40 --no-pager
curl -si http://localhost:8084/kisoft/ | head -3      # HTTP/1.1 401 + WWW-Authenticate: Basic realm="Realm" = OK
```

Expected log lines: `SQLite database Data Source=…/data/kisoftmock.db`, the H2 import (first start only), `Outgoing KiSoft → HOST webhooks ENABLED → …`, `Entra ID Bearer token: acquired`, `Now listening on: http://[::]:8084`.

`ASPNETCORE_ENVIRONMENT=Production` is set by the unit, so UI login and the Bearer check stay on unless the env file disables them. `JAVA_OPTS` in the env file is ignored. A Mac build for local use is `dotnet publish ... -p:PublishProfile=osx-arm64` (`dist/osx-arm64/knapp-kisoft-mock`).

### Upgrade the .NET service

```bash
sudo systemctl stop knapp-kisoft-mock-dotnet
sudo install -o knapp-mock -g knapp-mock -m 755 /tmp/knapp-kisoft-mock /opt/knapp-kisoft-mock/knapp-kisoft-mock
sudo rm -rf /opt/knapp-kisoft-mock/.net-extract/knapp-kisoft-mock   # drop the previously extracted bundle
sudo systemctl start knapp-kisoft-mock-dotnet
```

### Roll back to the JAR

```bash
sudo systemctl disable --now knapp-kisoft-mock-dotnet
sudo systemctl enable --now knapp-kisoft-mock
```

The JAR continues with its own H2 data; changes made in SQLite in the meantime are not copied back.

### Troubleshooting

- `GLIBC_2.33' not found (required by …/e_sqlite3.so)` with `status=6/ABRT`: the native SQLite library from `SQLitePCLRaw.bundle_e_sqlite3` 2.1.12 (pulled in by EF Core 10) is linked against glibc 2.34, which Ubuntu 20.04 (glibc 2.31) does not have. The csproj therefore pins `SQLitePCLRaw.bundle_e_sqlite3` to 2.1.11 (needs glibc 2.28). That version carries advisory [GHSA-2m69-gcr7-jv3q](https://github.com/advisories/GHSA-2m69-gcr7-jv3q), suppressed in the csproj via `NuGetAuditSuppress`. Once the server runs Ubuntu 22.04 or newer, remove the pin and the suppression. All other native parts of .NET 10 need glibc ≤ 2.27.
- `UI login is enabled but no password is set`: `MOCK_UI_PASSWORD` in `/etc/knapp-kisoft-mock/env` is empty (same rule as the JAR).

Persistent H2 data lives under `/opt/knapp-kisoft-mock/data/` (`kisoftmock.mv.db`). Upgrades keep that directory; only the JAR is replaced. To wipe state: stop the service and delete `data/kisoftmock.mv.db` (and related `.lock.db` / `.trace.db` if present).

## Upgrade

```bash
sudo systemctl stop knapp-kisoft-mock
sudo cp target/knapp-kisoft-mock-4.0.10.jar /opt/knapp-kisoft-mock/knapp-kisoft-mock.jar
sudo chown knapp-mock:knapp-mock /opt/knapp-kisoft-mock/knapp-kisoft-mock.jar
sudo systemctl start knapp-kisoft-mock
```

