# Ubuntu deployment (systemd)

Requires **Java 17+** (`openjdk-17-jre-headless` or similar).

## Install

```bash
# Build on the server or copy the JAR from CI
mvn -q clean package -DskipTests

sudo useradd --system --home /opt/knapp-kisoft-mock --shell /usr/sbin/nologin knapp-mock 2>/dev/null || true
sudo mkdir -p /opt/knapp-kisoft-mock/data /etc/knapp-kisoft-mock
sudo cp target/knapp-kisoft-mock-4.0.3.jar /opt/knapp-kisoft-mock/knapp-kisoft-mock.jar
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

Persistent H2 data lives under `/opt/knapp-kisoft-mock/data/` (`kisoftmock.mv.db`). Upgrades keep that directory; only the JAR is replaced. To wipe state: stop the service and delete `data/kisoftmock.mv.db` (and related `.lock.db` / `.trace.db` if present).

## Upgrade

```bash
sudo systemctl stop knapp-kisoft-mock
sudo cp target/knapp-kisoft-mock-4.0.3.jar /opt/knapp-kisoft-mock/knapp-kisoft-mock.jar
sudo chown knapp-mock:knapp-mock /opt/knapp-kisoft-mock/knapp-kisoft-mock.jar
sudo systemctl start knapp-kisoft-mock
```

