# BanQuito File Reception Service

Microservicio Switch para recibir lotes de pagos masivos en archivo CSV/TXT desde el Portal Banca Web Empresas, expuesto por Kong mediante:

```text
POST /api/v1/payments/batches
```

El servicio valida el archivo, registra la recepcion del lote, guarda auditoria de validacion en PostgreSQL, guarda el lote en MongoDB y publica las lineas aceptadas por RabbitMQ o gRPC.

## Funcionalidad

- Recibe archivos `multipart/form-data`.
- Soporta `ROUTING_CODE` por linea de detalle.
- Valida catalogo de bancos en `switch_parameter`.
- Codigo `001`: BanQuito / On-Us.
- Otros codigos catalogados: Off-Us.
- Codigo no reconocido: rechaza esa linea, no todo el lote.
- Valida estructura, cabecera, pie, totales y suma de montos.
- Registra resultados en `payment_file_validation`.
- Detecta duplicados por SHA-256 del archivo en los ultimos 30 dias.
- Responde `202 Accepted` inmediatamente si la validacion del lote es correcta.
- Publica lineas aceptadas por RabbitMQ o gRPC segun `APP_PAYMENT_LINE_TRANSPORT`.
- Aplica horario de corte 18:00 y agenda para el siguiente dia habil si llega despues del corte.
- Consulta feriados en Core Banking mediante `CORE_HOLIDAY_ENDPOINT`; si Core no responde, usa regla lunes-viernes.
- Valida la cuenta matriz contra la cuenta favorita del cliente mediante `CORE_FAVORITE_ACCOUNT_ENDPOINT`.
- Expone `GET /actuator/health`.

## Endpoint Principal

```http
POST /api/v1/payments/batches
Content-Type: multipart/form-data
```

Campos form-data:

| Campo | Tipo | Descripcion |
|---|---|---|
| `file` | File | Archivo `.csv` o `.txt` |
| `serviceType` | Text | Tipo de servicio, por ejemplo `NOMINA` |
| `clientRuc` | Text | RUC del cliente empresa |

Respuesta exitosa:

```json
{
  "batch_id": "uuid",
  "status": "RECEIVED",
  "message": "Lote recibido, procesando en segundo plano"
}
```

Errores relevantes:

| HTTP | Caso |
|---|---|
| `400` | Archivo invalido, cabecera/pie descuadrados, monto incorrecto |
| `409` | Lote duplicado |

## Formato CSV/TXT

El delimitador por defecto es coma `,`.

Cabecera:

```text
ruc,servicio,fecha_generacion,cuenta_matriz,total_registros,monto_total
```

Detalle:

```text
secuencial,routing_code,identificacion,nombre,cuenta_destino,monto,referencia,email
```

Pie:

```text
codigo_seguridad,total_registros,monto_total
```

Ejemplo:

```csv
0912345678,NOMINA,2026-06-01T14:00:00,1234567890,2,30.00
1,001,1757158215,Ana Perez,9876543210,10.00,REF-1,ana@example.com
2,002,1757158216,Luis Mora,9876543211,20.00,REF-2,luis@example.com
SEC-1,2,30.00
```

## Persistencia

PostgreSQL:

- `switch_parameter`
- `payment_file_validation`
- `batch_status_log`

MongoDB:

- Base: `file_reception`
- Coleccion: `payment_batch`

El servicio crea automaticamente la base PostgreSQL configurada en `POSTGRES_DB` si no existe, usando `POSTGRES_MAINTENANCE_DB=postgres`. El usuario PostgreSQL debe tener permiso `CREATEDB`.

MongoDB crea la base cuando se crea la coleccion o se inserta el primer documento. El servicio inicializa la coleccion `payment_batch` al arrancar.

## Variables De Entorno

El archivo de referencia esta en:

```text
Batch/.env.example
```

Para correr local, crea:

```text
Batch/.env
```

Variables principales:

```properties
SERVER_PORT=8084

POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=file_reception
POSTGRES_USER=postgres
POSTGRES_PASSWORD=123
POSTGRES_AUTO_CREATE_DB=true
POSTGRES_MAINTENANCE_DB=postgres

MONGO_URI=mongodb://localhost:27017/file_reception

APP_PAYMENT_LINE_TRANSPORT=rabbitmq
APP_RABBIT_ENABLED=false
MANAGEMENT_HEALTH_RABBIT_ENABLED=false

APP_CORE_VALIDATION_ENABLED=false
CORE_BASE_URL=http://localhost:8080
CORE_HOLIDAY_ENDPOINT=/api/v2/calendar/holidays/check
CORE_ACCOUNT_VALIDATION_ENDPOINT=/api/v1/accounts/validate
CORE_FAVORITE_ACCOUNT_ENDPOINT=/api/v2/accounts/customer/{customerId}/favorite
CORE_MASS_PAYMENT_SERVICE_ENDPOINT=/api/v1/customers/mass-payments/active
```

Para enviar las lineas por gRPC en lugar de RabbitMQ:

```properties
APP_PAYMENT_LINE_TRANSPORT=grpc
APP_GRPC_HOST=localhost
APP_GRPC_PORT=9090
APP_GRPC_DEADLINE_SECONDS=10
```

Para integracion real con Core/Party/Rabbit:

```properties
APP_RABBIT_ENABLED=true
MANAGEMENT_HEALTH_RABBIT_ENABLED=true
APP_CORE_VALIDATION_ENABLED=true
CORE_BASE_URL=http://localhost:8080
CORE_HOLIDAY_ENDPOINT=/api/v2/calendar/holidays/check
CORE_ACCOUNT_VALIDATION_ENDPOINT=/api/v1/accounts/validate
CORE_FAVORITE_ACCOUNT_ENDPOINT=/api/v2/accounts/customer/{customerId}/favorite
CORE_MASS_PAYMENT_SERVICE_ENDPOINT=/api/v1/customers/mass-payments/active
```

Endpoints esperados en Core:

| Variable | Uso |
|---|---|
| `CORE_HOLIDAY_ENDPOINT` | Consulta si una fecha es dia habil. Recibe `date` como query param. |
| `CORE_ACCOUNT_VALIDATION_ENDPOINT` | Valida cada cuenta destino. Recibe `accountNumber` y `clientRuc` como query params. |
| `CORE_FAVORITE_ACCOUNT_ENDPOINT` | Consulta la cuenta favorita del cliente usando `{customerId}` en la ruta. |
| `CORE_MASS_PAYMENT_SERVICE_ENDPOINT` | Valida si el cliente tiene activo el servicio de pagos masivos. Recibe `clientRuc` y `serviceType` como query params. |

## Ejecutar Local

Desde `Batch`:

```powershell
cd "C:\Users\User\Desktop\swith con microservicios7\banquito-file-reception-service\Batch"
.\mvnw.cmd spring-boot:run
```

Health:

```powershell
Invoke-RestMethod http://localhost:8084/actuator/health
```

## Ejecutar Con Docker

El `Dockerfile` esta en la raiz del repositorio y construye el modulo `Batch`.

Desde `banquito-file-reception-service`:

```powershell
docker build -t banquito-file-reception-service .
```

Si PostgreSQL, MongoDB y RabbitMQ corren en tu maquina host, usa `host.docker.internal` dentro del contenedor:

```powershell
docker run --rm --name banquito-file-reception-service `
  -p 8084:8084 `
  -e POSTGRES_HOST=host.docker.internal `
  -e MONGO_URI=mongodb://host.docker.internal:27017/file_reception `
  -e APP_RABBIT_ENABLED=false `
  -e MANAGEMENT_HEALTH_RABBIT_ENABLED=false `
  -e APP_CORE_VALIDATION_ENABLED=false `
  banquito-file-reception-service
```

## Ejecutar Todo Con Docker Compose

El repositorio incluye un `docker-compose.yml` que levanta:

- PostgreSQL
- MongoDB
- RabbitMQ con consola web
- `banquito-file-reception-service`

Desde la raiz del repositorio:

```powershell
cd "C:\Users\User\Desktop\swith con microservicios7\banquito-file-reception-service"
docker compose up --build
```

Servicios expuestos:

| Servicio | URL / Puerto |
|---|---|
| Microservicio | `http://localhost:8084` |
| Health | `http://localhost:8084/actuator/health` |
| PostgreSQL | `localhost:5432` |
| MongoDB | `mongodb://localhost:27017/file_reception` |
| RabbitMQ AMQP | `localhost:5672` |
| RabbitMQ Console | `http://localhost:15672` |

Credenciales RabbitMQ:

```text
guest / guest
```

Credenciales PostgreSQL:

```text
postgres / 123
```

En Docker Compose se usa:

```properties
APP_CORE_VALIDATION_ENABLED=false
APP_RABBIT_ENABLED=true
MANAGEMENT_HEALTH_RABBIT_ENABLED=true
```

Esto permite probar el flujo completo con RabbitMQ real, pero sin depender de Core/Party Service.

Para apagar y conservar datos:

```powershell
docker compose down
```

Para apagar y borrar volumenes de PostgreSQL, MongoDB y RabbitMQ:

```powershell
docker compose down -v
```

## RabbitMQ Local

Con Docker Desktop encendido:

```powershell
docker run -it --rm --name rabbitmq `
  -p 5672:5672 `
  -p 15672:15672 `
  rabbitmq:4-management
```

Consola web:

```text
http://localhost:15672
```

Credenciales:

```text
guest / guest
```

Si no vas a usar Rabbit localmente:

```properties
APP_RABBIT_ENABLED=false
MANAGEMENT_HEALTH_RABBIT_ENABLED=false
```

## Pruebas Manuales

Archivos de prueba:

```text
Batch/test-files/batch-valid.csv
Batch/test-files/batch-invalid-routing.csv
Batch/test-files/batch-bad-amount.csv
```

Lote valido:

```powershell
curl.exe -X POST "http://localhost:8084/api/v1/payments/batches" `
  -F "file=@Batch/test-files/batch-valid.csv" `
  -F "serviceType=NOMINA" `
  -F "clientRuc=0912345678"
```

Duplicado:

```powershell
# Ejecutar de nuevo el request del lote valido.
# Debe responder HTTP 409 con "Lote duplicado".
```

Routing invalido:

```powershell
curl.exe -X POST "http://localhost:8084/api/v1/payments/batches" `
  -F "file=@Batch/test-files/batch-invalid-routing.csv" `
  -F "serviceType=NOMINA" `
  -F "clientRuc=0912345678"
```

Monto descuadrado:

```powershell
curl.exe -X POST "http://localhost:8084/api/v1/payments/batches" `
  -F "file=@Batch/test-files/batch-bad-amount.csv" `
  -F "serviceType=NOMINA" `
  -F "clientRuc=0912345678"
```

## Postman

Coleccion:

```text
Batch/postman/switch-payment-batches.postman_collection.json
```

Importala en Postman y valida que el campo `file` este como tipo `File`. Si Postman no resuelve la ruta relativa, selecciona manualmente los archivos desde `Batch/test-files`.

## Notas De Desarrollo

- `APP_CORE_VALIDATION_ENABLED=false` permite probar local sin Core/Party Service.
- Cuando Core esta desactivado, la validacion de cuentas y servicio masivo se considera valida si los campos vienen informados.
- Cuando Core esta desactivado, el calendario de dias habiles usa regla lunes-viernes.
- Cuando Core esta activo, el calendario usa `CORE_HOLIDAY_ENDPOINT` y cae a regla lunes-viernes si la consulta falla.
- Cuando Core esta activo, la cuenta matriz del archivo debe coincidir con la cuenta favorita retornada por `CORE_FAVORITE_ACCOUNT_ENDPOINT`.
- Despues de las 18:00 el lote queda con `scheduled_process_at` del siguiente dia habil.
- `payment_batch.status` inicia como `RECEIVED`.
- RabbitMQ publica en la cola `payment.lines.queue`.
- gRPC usa el contrato `Batch/src/main/proto/payment_line_ingestion.proto`.
- Para activar gRPC, configura `APP_PAYMENT_LINE_TRANSPORT=grpc` y apunta `APP_GRPC_HOST` / `APP_GRPC_PORT` al microservicio receptor.
