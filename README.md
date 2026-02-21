# KNAPP KiSoft Mock Server

Mock server on port **8084** that simulates KNAPP KiSoft API calls. OAuth2/Entra ID configuration present; Bearer token required (content is not validated).

## Requirements

- Java 17+
- Maven 3.8+

## Running

### Development (without Bearer token)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

With the `dev` profile, authentication is disabled (`knapp.mock.bypass-auth=true`).

### Production (with Bearer token)

```bash
mvn spring-boot:run
```

API calls must include a Bearer token (content is not validated):

```
Authorization: Bearer <token>
```

## API Endpoints (KiSoft One API)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/kisoft/oneapi/v1/packUnit/updateSession` | Open/close update session for packUnit masterdata |
| PUT | `/kisoft/oneapi/v1/packUnit` | Create or update pack units (article masterdata) |
| GET | `/kisoft/oneapi/v1/packUnit` | List all pack units |
| DELETE | `/kisoft/oneapi/v1/packUnit` | Delete all pack units |
| POST | `/kisoft/oneapi/v1/inboundDelivery` | Create inbound delivery (goods-in) |
| POST | `/kisoft/oneapi/v1/storageOrder` | Create storage order |
| GET | `/kisoft/oneapi/v1/storageOrder` | List all storage orders |
| DELETE | `/kisoft/oneapi/v1/storageOrder` | Delete all storage orders |

## Swagger UI

Available at: **https://wispelberg.eu/kisoft/swagger-ui.html**

## Configuration

- **Port**: 8084
- **OAuth2/Entra ID**: `AZURE_TENANT_ID` in application.yml (configuration present, token is not validated)
- **Bearer token**: required on `/kisoft/oneapi/**` – any Bearer token is accepted
- **Bypass auth**: `knapp.mock.bypass-auth=true` (for local testing only)
