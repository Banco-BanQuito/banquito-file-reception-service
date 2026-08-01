# BanQuito File Reception Service

> Nota de arquitectura actual: RabbitMQ fue reemplazado por Google Cloud Pub/Sub como broker administrado de nube. El bounded context objetivo de este microservicio es ingesta de archivos: recibir, validar estructura, registrar lote y publicar eventos. El procesamiento/dispatch debe migrarse a un consumidor dedicado.

Microservicio del Switch encargado de recibir archivos de pagos masivos, validar su estructura, registrar el lote y publicar cada línea en RabbitMQ. El mismo despliegue contiene consumidores que procesan las líneas On-Us, Off-Us e inválidas.

La documentación adicional está disponible en:

- [README-ARQUITECTURA.md](README-ARQUITECTURA.md): referencia detallada archivo por archivo.
- [README-COMPONENTES.md](README-COMPONENTES.md): explicación directa de `controller`, `event`, `service/impl` y `dispatch`.

Este proyecto ya no depende de `routing-service`. La palabra `routing` que aparece en el catálogo se refiere al código bancario y a la clasificación `ON_US` o `OFF_US`, no a otro microservicio.

## Flujo general

```text
Cliente
  |
  | POST /api/v1/payments/batches
  v
FileReceptionController
  v
FileReceptionServiceImpl
  |-- CsvBatchParserImpl
  |-- BusinessDayServiceImpl
  |-- CoreBankingClientImpl
  |-- persistencia MySQL/MongoDB
  v
PaymentLinesReadyEvent
  v
PaymentLinesReadyListener
  |-- clasifica el código bancario
  v
PaymentLinePublisherImpl
  v
RabbitMQ: payment.exchange
  |-- onus    -> payment.lines.onus.queue
  |-- offus   -> payment.lines.offus.queue
  `-- invalid -> payment.lines.invalid.queue
  v
PaymentDispatchService
  |-- On-Us  -> crédito en Account Core
  |-- Off-Us -> clearing.exchange
  `-- cierre -> tarifa, devolución, notificación y estado final
```

RabbitMQ es el canal asíncrono entre la recepción y el procesamiento. `PaymentLinePublisherImpl` publica los mensajes y `PaymentDispatchService` los consume.

## Organización principal

```text
controller/          API REST de recepción, salud y catálogo bancario
service/             Contratos de la capa de servicio
service/impl/        Implementaciones de recepción y publicación
event/               Evento interno que inicia el procesamiento asíncrono
dispatch/            Consumidores RabbitMQ y procesamiento financiero
config/              RabbitMQ, HTTP, gRPC, scheduling y propiedades
model/ y dto/        Entidades persistentes y objetos de transferencia
repository/          Repositorios MySQL y MongoDB
```

## Controladores

### FileReceptionController

Expone la entrada principal del microservicio.

| Método | Ruta | Responsabilidad |
|---|---|---|
| `POST` | `/api/v1/payments/batches` | Recibe el archivo y devuelve `202 Accepted` |
| `POST` | `/api/v2/payments/batches` | Alias versionado del endpoint anterior |
| `GET` | `/api/v1/payments/health` | Salud básica del servicio |
| `GET` | `/api/v2/payments/health` | Alias versionado de salud |

El endpoint de recepción espera `multipart/form-data`:

| Campo | Tipo | Descripción |
|---|---|---|
| `file` | Archivo | CSV o TXT del lote |
| `serviceType` | Texto | Tipo de servicio, por ejemplo `NOMINA` |
| `clientRuc` | Texto | RUC de la empresa |

### BankCodeCatalogController

Administra el catálogo local de códigos bancarios. No consume ni representa al antiguo `routing-service`.

| Método | Ruta | Responsabilidad |
|---|---|---|
| `GET` | `/api/v1/routing-codes` | Lista los códigos registrados |
| `GET` | `/api/v2/payments/routing-codes` | Alias de la lista |
| `GET` | `/api/v1/routing-codes/{code}/classify` | Devuelve `ON_US` u `OFF_US` |
| `POST` | `/api/v1/routing-codes` | Registra un código bancario |
| `DELETE` | `/api/v1/routing-codes/{code}` | Elimina un código |

Ejemplo conceptual:

```text
001 -> Banco BanQuito -> ON_US
002 -> Otro banco     -> OFF_US
```

### PaymentBatchStatusController

Pertenece al paquete `dispatch` porque consulta el estado producido durante el procesamiento de las líneas.

| Método | Ruta | Responsabilidad |
|---|---|---|
| `GET` | `/api/v2/payments/batches/{batchId}/status` | Consulta contadores, montos y estado final del lote |

### ApiExceptionHandler

Centraliza respuestas para parámetros multipart faltantes y lotes duplicados. Un duplicado devuelve HTTP `409`.

## Contratos de servicio

Las interfaces del paquete `service` definen qué necesita el caso de uso sin acoplar los controladores a implementaciones concretas.

### IFileReceptionService

Contrato principal de recepción. Recibe el archivo, el tipo de servicio y el RUC; devuelve la identificación y programación del lote.

Implementación: `FileReceptionServiceImpl`.

### ICsvBatchParser

Convierte el contenido CSV/TXT en un `ParsedBatch`. Valida cabecera, detalles, pie, número de registros y totales monetarios.

Implementación: `CsvBatchParserImpl`.

### IBusinessDayService

Determina si una fecha es hábil y calcula el siguiente día hábil para lotes recibidos después de la hora de corte.

Implementación: `BusinessDayServiceImpl`.

### ICoreBankingClient

Define las operaciones REST requeridas del Account Core:

- Validar cuentas.
- Consultar la cuenta favorita de la empresa.
- Confirmar que el servicio de pagos masivos esté activo.
- Consultar saldo suficiente.
- Ejecutar crédito masivo On-Us.
- Ejecutar débito corporativo.
- Ejecutar devolución corporativa.

Implementación: `CoreBankingClientImpl`.

### IBankCodeCatalogService

Consulta el catálogo local para validar un código bancario y clasificarlo como `ON_US` o `OFF_US`.

Implementación: `BankCodeCatalogServiceImpl`.

### IPaymentLinePublisher

Publica en RabbitMQ las líneas ya validadas y clasificadas.

Implementación: `PaymentLinePublisherImpl`.

## Implementaciones de servicio

### FileReceptionServiceImpl

Orquesta la recepción inicial:

1. Valida que el archivo exista y tenga un formato permitido.
2. Ejecuta `CsvBatchParserImpl`.
3. Comprueba totales y saldo de la cuenta de origen.
4. Detecta duplicados mediante el hash del archivo.
5. Calcula la fecha de procesamiento según la hora de corte.
6. Persiste el lote y su historial de estado.
7. Publica `PaymentLinesReadyEvent`.
8. Devuelve la respuesta HTTP sin esperar el procesamiento financiero completo.

### CsvBatchParserImpl

Interpreta el archivo con esta estructura:

```text
ruc,servicio,fecha_generacion,cuenta_matriz,total_registros,monto_total
secuencial,routing_code,identificacion,nombre,cuenta_destino,monto,referencia,email
codigo_seguridad,total_registros,monto_total
```

### BusinessDayServiceImpl

Consulta el calendario del Core. Si el Core no está disponible, aplica la regla local de lunes a viernes.

### CoreBankingClientImpl

Implementa con HTTP las operaciones declaradas en `ICoreBankingClient`. El comportamiento puede desactivarse localmente mediante `APP_CORE_VALIDATION_ENABLED=false`.

### BankCodeCatalogServiceImpl

Lee `switch_parameter` y devuelve la clasificación asociada a cada banco. Esta clasificación determina la routing key utilizada al publicar en RabbitMQ.

### PaymentLinesReadyListener

Escucha el evento interno `PaymentLinesReadyEvent`. Valida el servicio de pagos masivos y la cuenta favorita, transforma las líneas en `BatchLineMessage` y solicita su publicación inmediata o programada.

### PaymentLinePublisherImpl

Usa `RabbitTemplate.convertAndSend()` para publicar cada línea en `payment.exchange`.

| Clasificación | Routing key | Cola |
|---|---|---|
| `ON_US` | `onus` | `payment.lines.onus.queue` |
| `OFF_US` | `offus` | `payment.lines.offus.queue` |
| No reconocida | `invalid` | `payment.lines.invalid.queue` |

No existe un transporte alternativo hacia `routing-service` ni una llamada gRPC para publicar líneas.

## Módulo dispatch

`dispatch` contiene la etapa posterior a RabbitMQ. No es otro microservicio: forma parte de esta misma aplicación.

### PaymentDispatchService

Es el consumidor de las tres colas mediante `@RabbitListener`:

- `processOnUsLine`: consume pagos On-Us y solicita el crédito al Account Core.
- `processOffUsLine`: consume pagos Off-Us y los publica en `clearing.exchange`.
- `processInvalidLine`: registra como rechazada una línea cuyo código no fue reconocido.

Además, coordina:

- Idempotencia por lote y número de línea.
- Débito inicial del total declarado.
- Persistencia de detalles y contadores en MongoDB.
- Cálculo de tarifa mediante `TariffGrpcClient`.
- Devolución del monto rechazado.
- Notificación mediante `NotificationGrpcClient`.
- Cierre del lote como completado, completado con novedades o fallido.

RabbitMQ solo transporta mensajes. `PaymentDispatchService` es necesario porque actúa como consumidor y ejecuta el trabajo asociado a cada mensaje.

### Clientes dispatch

- `TariffGrpcClient`: consulta la tarifa del lote al servicio de tarifas mediante gRPC.
- `NotificationGrpcClient`: solicita el envío de notificaciones mediante gRPC.

### Persistencia dispatch

- `PaymentDispatchBatchRepository`: estado agregado del lote.
- `PaymentDispatchDetailRepository`: resultado individual de cada línea.

## RabbitMQ

La topología se declara en `RabbitMqConfig`:

```text
payment.exchange (direct)
  |-- onus    -> payment.lines.onus.queue
  |-- offus   -> payment.lines.offus.queue
  `-- invalid -> payment.lines.invalid.queue

clearing.exchange (direct)
  `-- clearing.outbound -> clearing.outbound.queue
```

Variables principales:

```properties
APP_RABBIT_ENABLED=true
APP_RABBIT_QUEUE_ONUS=payment.lines.onus.queue
APP_RABBIT_QUEUE_OFFUS=payment.lines.offus.queue
APP_RABBIT_QUEUE_INVALID=payment.lines.invalid.queue
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_VHOST=/
RABBITMQ_CLEARING_EXCHANGE=clearing.exchange
RABBITMQ_CLEARING_ROUTING_KEY=clearing.outbound
```

## Persistencia

MySQL almacena información paramétrica y de validación:

- `switch_parameter`
- `payment_file_validation`
- `batch_status_log`

MongoDB almacena la recepción, el estado agregado y el detalle del procesamiento de los lotes.

## Ejecución local

```powershell
.\mvnw.cmd spring-boot:run
```

Salud:

```powershell
Invoke-RestMethod http://localhost:8084/actuator/health
```

Recepción de un lote:

```powershell
curl.exe -X POST "http://localhost:8084/api/v1/payments/batches" `
  -F "file=@test-files/batch-valid.csv" `
  -F "serviceType=NOMINA" `
  -F "clientRuc=0912345678"
```

## Docker Compose

El `docker-compose.yml` incluye PostgreSQL, MongoDB, RabbitMQ y el microservicio:

```powershell
docker compose up --build
```

Existe una inconsistencia pendiente: la aplicación vigente usa el driver y una URL JDBC de MySQL, mientras el Compose local todavía configura PostgreSQL mediante variables `POSTGRES_*`. Antes de utilizarlo como entorno completo debe reemplazarse PostgreSQL por MySQL o restaurarse el driver y la configuración PostgreSQL en la aplicación. MongoDB y RabbitMQ sí coinciden con la configuración actual.

| Componente | Dirección |
|---|---|
| API | `http://localhost:8084` |
| Health | `http://localhost:8084/actuator/health` |
| RabbitMQ | `localhost:5672` |
| Consola RabbitMQ | `http://localhost:15672` |
| MongoDB | `mongodb://localhost:27017/file_reception` |

## Pruebas

```powershell
.\mvnw.cmd test
```

Archivos manuales disponibles:

```text
test-files/batch-valid.csv
test-files/batch-invalid-routing.csv
test-files/batch-bad-amount.csv
test-files/batch-grpc-offus.csv
```

La colección Postman está en `postman/switch-payment-batches.postman_collection.json`.
