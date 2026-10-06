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

```bash
dotnet publish dotnet/KnappKiSoftMock/KnappKiSoftMock.csproj -p:PublishProfile=linux-x64
sudo systemctl stop knapp-kisoft-mock
sudo install -o knapp-mock -g knapp-mock -m 755 dist/linux-x64/knapp-kisoft-mock /opt/knapp-kisoft-mock/knapp-kisoft-mock
sudo cp deploy/knapp-kisoft-mock-dotnet.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now knapp-kisoft-mock-dotnet
```

`ASPNETCORE_ENVIRONMENT=Production` is set by the unit, so UI login and the Bearer check stay on unless the env file disables them. A Mac build for local use is `dotnet publish ... -p:PublishProfile=osx-arm64` (`dist/osx-arm64/knapp-kisoft-mock`).

Persistent H2 data lives under `/opt/knapp-kisoft-mock/data/` (`kisoftmock.mv.db`). Upgrades keep that directory; only the JAR is replaced. To wipe state: stop the service and delete `data/kisoftmock.mv.db` (and related `.lock.db` / `.trace.db` if present).

## Upgrade

```bash
sudo systemctl stop knapp-kisoft-mock
sudo cp target/knapp-kisoft-mock-4.0.10.jar /opt/knapp-kisoft-mock/knapp-kisoft-mock.jar
sudo chown knapp-mock:knapp-mock /opt/knapp-kisoft-mock/knapp-kisoft-mock.jar
sudo systemctl start knapp-kisoft-mock
```

