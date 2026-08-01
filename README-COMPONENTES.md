# Explicación de componentes

Este documento explica de forma directa las carpetas `controller`, `event`, `service/impl` y `dispatch` del microservicio.

## Flujo principal

```text
Cliente HTTP
    |
    v
Controller
    |
    v
Service/impl
    |
    v
PaymentLinesReadyEvent
    |
    v
PaymentLinesReadyListener
    |
    v
PaymentLinePublisherImpl
    |
    v
RabbitMQ
    |
    v
Dispatch
```

La primera mitad recibe y prepara el lote. La segunda mitad consume los mensajes y procesa los pagos.

## Controller

La carpeta `controller` contiene los puntos de entrada HTTP.

### FileReceptionController

Recibe el archivo de pagos masivos.

```http
POST /api/v1/payments/batches
POST /api/v2/payments/batches
```

Recibe:

- `file`: archivo CSV o TXT.
- `serviceType`: tipo de pago masivo.
- `clientRuc`: RUC de la empresa.

Delega el trabajo a `IFileReceptionService`. Si el archivo es aceptado, responde HTTP `202` sin esperar que terminen todos los pagos.

También expone:

```http
GET /api/v1/payments/health
GET /api/v2/payments/health
```

### BankCodeCatalogController

Administra el catálogo de códigos bancarios.

Permite:

- Listar códigos.
- Clasificar un código como `ON_US` u `OFF_US`.
- Registrar un banco.
- Eliminar un banco.

Aunque conserva rutas llamadas `/routing-codes`, no utiliza el antiguo `routing-service`. El nombre de la ruta se mantiene para no romper los frontends ni Kong.

Delega el trabajo a `IBankCodeCatalogService`.

### ApiExceptionHandler

Transforma excepciones en respuestas HTTP controladas.

Maneja:

- Parámetros faltantes.
- Partes multipart faltantes.
- Archivos duplicados.

Evita que el cliente reciba errores internos de Java sin formato.

## Event

### PaymentLinesReadyEvent

Es un evento interno de Spring. No es todavía un mensaje RabbitMQ.

Contiene:

| Campo | Significado |
|---|---|
| `batchId` | Identificador único del lote |
| `scheduledProcessAt` | Momento programado para procesar |
| `batch` | Archivo ya analizado y convertido |
| `duplicateValid` | Resultado de la validación de duplicados |

Lo publica `FileReceptionServiceImpl` después de guardar el lote. Lo escucha `PaymentLinesReadyListener`.

Su propósito es desacoplar la recepción HTTP de la preparación y publicación en RabbitMQ.

```text
FileReceptionServiceImpl
        |
        | publica evento interno
        v
PaymentLinesReadyListener
```

## Service/impl

La carpeta `service/impl` contiene las implementaciones concretas de los servicios de recepción.

### FileReceptionServiceImpl

Coordina la recepción completa del archivo.

Realiza:

1. Validación del archivo.
2. Lectura mediante `CsvBatchParserImpl`.
3. Validación de totales y saldo.
4. Detección de duplicados.
5. Cálculo de la fecha de procesamiento.
6. Persistencia inicial del lote.
7. Registro del historial.
8. Publicación de `PaymentLinesReadyEvent`.

No procesa directamente créditos On-Us ni pagos Off-Us.

### CsvBatchParserImpl

Lee el archivo CSV/TXT y crea un `ParsedBatch`.

Valida:

- Cabecera.
- Líneas de detalle.
- Pie del archivo.
- Cantidad de registros.
- Suma de montos.
- Formato de fechas y números.

Su responsabilidad termina cuando devuelve el lote interpretado.

### BusinessDayServiceImpl

Determina si una fecha es hábil.

Consulta el calendario del Core. Si la consulta falla, aplica una regla local de lunes a viernes. Se utiliza para programar lotes recibidos después de la hora de corte.

### CoreBankingClientImpl

Implementa las llamadas REST hacia Account Core.

Se utiliza para:

- Validar cuentas.
- Consultar cuenta favorita.
- Consultar servicio de pagos masivos.
- Consultar saldo.
- Ejecutar débito.
- Ejecutar crédito On-Us.
- Ejecutar devolución.

### BankCodeCatalogServiceImpl

Consulta y modifica el catálogo bancario mediante `SwitchParameterRepository`.

Devuelve:

- `ON_US` cuando el banco pertenece a BanQuito.
- `OFF_US` cuando pertenece a otra institución.
- `null` cuando el código no existe.

### PaymentLinesReadyListener

Escucha `PaymentLinesReadyEvent`.

Antes de publicar:

- Confirma que el servicio de pagos masivos esté activo.
- Valida la cuenta de origen favorita.
- Clasifica cada código bancario.
- Convierte cada detalle en `BatchLineMessage`.
- Programa el envío si la fecha está en el futuro.

Después invoca `IPaymentLinePublisher`.

### PaymentLinePublisherImpl

Publica cada `BatchLineMessage` en RabbitMQ mediante `RabbitTemplate`.

```text
payment.exchange
  |-- onus    -> payment.lines.onus.queue
  |-- offus   -> payment.lines.offus.queue
  `-- invalid -> payment.lines.invalid.queue
```

Este archivo únicamente publica. El procesamiento financiero ocurre en `dispatch`.

## Dispatch

`dispatch` contiene los consumidores de RabbitMQ y el estado financiero del lote.

No es otro microservicio. Es un módulo interno del mismo proyecto.

### dispatch/service/PaymentDispatchService

Es el consumidor principal.

#### Flujo On-Us

Consume:

```text
payment.lines.onus.queue
```

Luego:

1. Registra la línea.
2. Garantiza el débito inicial del lote.
3. Solicita el crédito al Account Core.
4. Actualiza el resultado.
5. Solicita la notificación.

#### Flujo Off-Us

Consume:

```text
payment.lines.offus.queue
```

Luego transforma el mensaje y lo publica en:

```text
clearing.exchange
  -> clearing.outbound
  -> clearing.outbound.queue
```

#### Flujo inválido

Consume:

```text
payment.lines.invalid.queue
```

Registra la línea como rechazada por código bancario inválido.

#### Cierre del lote

Cuando todas las líneas terminaron:

- Calcula registros exitosos y rechazados.
- Calcula la tarifa.
- Devuelve montos rechazados.
- Actualiza el estado final.

### dispatch/controller/PaymentBatchStatusController

Consulta el resultado agregado:

```http
GET /api/v2/payments/batches/{batchId}/status
```

Devuelve estado, contadores, montos, fechas y motivo de falla.

### dispatch/client/TariffGrpcClient

Cliente gRPC del servicio de tarifas. Solicita la comisión correspondiente a las transacciones exitosas.

### dispatch/client/NotificationGrpcClient

Cliente gRPC del servicio de notificaciones. Solicita el envío de mensajes al beneficiario.

### dispatch/dto/BatchStatusResponse

Representa el JSON devuelto por el endpoint de estado del lote.

### dispatch/model/PaymentBatch

Documento MongoDB con la información acumulada del lote:

- Estado.
- Cantidad declarada.
- Exitosos y rechazados.
- Montos.
- Fechas.
- Motivo de falla.

### dispatch/model/PaymentDetail

Documento MongoDB con el resultado de una línea individual.

### dispatch/model/OffUsClearingMessage

Formato del mensaje enviado al clearinghouse para una transferencia hacia otro banco.

### dispatch/repository/PaymentDispatchBatchRepository

Acceso MongoDB a los lotes procesados.

### dispatch/repository/PaymentDispatchDetailRepository

Acceso MongoDB a los detalles individuales. Ayuda a evitar que la misma línea sea procesada dos veces.

## Diferencia entre las carpetas

| Carpeta | Función |
|---|---|
| `controller` | Recibe y responde solicitudes HTTP |
| `event` | Comunica internamente que el lote está listo |
| `service/impl` | Valida, prepara y publica líneas |
| `dispatch` | Consume RabbitMQ y procesa pagos |

## Resumen final

```text
FileReceptionController
  -> FileReceptionServiceImpl
  -> PaymentLinesReadyEvent
  -> PaymentLinesReadyListener
  -> PaymentLinePublisherImpl
  -> RabbitMQ
  -> PaymentDispatchService
  -> Account Core o Clearinghouse
  -> PaymentBatchStatusController
```
> Nota de arquitectura actual: RabbitMQ fue reemplazado por Google Cloud Pub/Sub. Las referencias historicas a RabbitMQ en este documento describen el diseno anterior. El bounded context objetivo de `file-reception-service` es ingesta de archivos; el procesamiento/dispatch debe vivir en un consumidor dedicado.
