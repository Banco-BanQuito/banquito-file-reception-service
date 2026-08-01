# Guía de controladores, servicios, eventos y dispatch

Este documento explica la función de cada archivo de las carpetas `controller`, `service`, `service/impl`, `event` y `dispatch`.

## Visión general

La aplicación tiene dos etapas:

```text
ETAPA 1: recepción
HTTP -> controller -> service -> service/impl -> evento -> publicación RabbitMQ

ETAPA 2: procesamiento
RabbitMQ -> dispatch/service -> Core o Clearinghouse -> estado del lote
```

Por esta razón aparecen dos carpetas que contienen servicios:

- `service`: recepción, validación, clasificación y publicación de las líneas.
- `dispatch/service`: consumo de RabbitMQ y ejecución financiera de las líneas.

No son dos microservicios. Ambos paquetes se ejecutan dentro de `banquito-file-reception-service`.

## Controller

Los controladores reciben solicitudes HTTP. Deben validar la entrada HTTP, delegar el trabajo y construir la respuesta. No deberían implementar reglas de negocio ni acceder directamente a repositorios.

### BankCodeCatalogController.java

Administra el catálogo de códigos bancarios.

Responsabilidades:

- Listar los bancos registrados.
- Consultar si un código es `ON_US` u `OFF_US`.
- Registrar un código bancario.
- Eliminar un código bancario.
- Validar el formato del JSON recibido.

Delega el trabajo a `IBankCodeCatalogService`.

Mantiene las rutas `/routing-codes` porque los frontends y Kong ya consumen ese contrato. El nombre Java cambió a `BankCodeCatalogController` para evitar confusión con el antiguo `routing-service`.

### FileReceptionController.java

Es la entrada principal para cargar archivos.

Responsabilidades:

- Recibir `file`, `serviceType` y `clientRuc` como `multipart/form-data`.
- Delegar la recepción a `IFileReceptionService`.
- Responder HTTP `202 Accepted` cuando el lote fue aceptado.
- Responder HTTP `400` cuando el archivo no puede leerse o su contenido es inválido.
- Exponer un endpoint básico de salud.

No procesa directamente las líneas ni publica en RabbitMQ.

### ApiExceptionHandler.java

Convierte excepciones conocidas en respuestas HTTP uniformes.

Casos actuales:

- Parámetro obligatorio faltante: HTTP `400`.
- Parte multipart faltante: HTTP `400`.
- Archivo duplicado: HTTP `409`.

## Service

La carpeta `service` contiene interfaces. Una interfaz define qué operación necesita la aplicación, pero no cómo se ejecuta.

Esta separación permite que los controladores dependan de contratos y facilita reemplazar o simular implementaciones en pruebas.

### IBankCodeCatalogService.java

Contrato del catálogo bancario.

Operaciones:

- `isValid`: comprueba si existe un código.
- `classify`: devuelve `ON_US`, `OFF_US` o `null`.
- `listAll`: lista el catálogo.
- `register`: crea un registro.
- `delete`: elimina un registro.

Implementación: `BankCodeCatalogServiceImpl`.

### IBusinessDayService.java

Contrato para el calendario de procesamiento.

Operaciones:

- `isBusinessDay`: determina si una fecha es hábil.
- `nextBusinessDay`: obtiene el siguiente día hábil.

Implementación: `BusinessDayServiceImpl`.

### ICoreBankingClient.java

Contrato para comunicarse con Account Core mediante REST.

Operaciones:

- Validar una cuenta.
- Consultar la cuenta favorita de una empresa.
- Consultar si el servicio de pagos masivos está activo.
- Consultar saldo disponible.
- Ejecutar créditos On-Us.
- Ejecutar el débito corporativo del lote.
- Devolver montos rechazados.

Implementación: `CoreBankingClientImpl`.

### ICsvBatchParser.java

Contrato para convertir un archivo CSV/TXT en un `ParsedBatch`.

Implementación: `CsvBatchParserImpl`.

### IFileReceptionService.java

Contrato del caso de uso principal de recepción.

Recibe:

- Archivo multipart.
- Tipo de servicio.
- RUC del cliente.

Devuelve un `FileReceptionResponse` con la identificación y el estado inicial del lote.

Implementación: `FileReceptionServiceImpl`.

### IPaymentLinePublisher.java

Contrato para publicar las líneas aceptadas.

Recibe el identificador del lote, la fecha programada y una lista de `BatchLineMessage`.

Implementación: `PaymentLinePublisherImpl`, que utiliza RabbitMQ.

## Service/impl

La carpeta `service/impl` contiene el código concreto que cumple las interfaces de `service`. También contiene el listener del evento interno que conecta la recepción con RabbitMQ.

### BankCodeCatalogServiceImpl.java

Implementa `IBankCodeCatalogService` usando `SwitchParameterRepository`.

Lee y modifica el catálogo persistido en `switch_parameter`. Guarda la clasificación bancaria en `valueString` y utiliza el tipo `BANK_ROUTING_CODE`.

### BusinessDayServiceImpl.java

Implementa `IBusinessDayService`.

Primero consulta el calendario del Core. Si la consulta falla, usa una regla local de lunes a viernes. También calcula la próxima fecha hábil cuando el archivo llega después de la hora de corte.

### CoreBankingClientImpl.java

Implementa `ICoreBankingClient` con solicitudes HTTP.

Se usa tanto durante la validación como durante `dispatch`:

- Antes de publicar: valida servicio, cuenta favorita y saldo.
- Después de consumir: ejecuta débito, crédito y devolución.

Cuando `APP_CORE_VALIDATION_ENABLED=false`, permite ejecutar pruebas locales sin depender del Core para determinadas validaciones.

### CsvBatchParserImpl.java

Implementa `ICsvBatchParser`.

Responsabilidades:

- Leer cabecera, detalles y pie.
- Validar número de columnas.
- Convertir montos y fechas.
- Comprobar registros declarados.
- Comprobar totales declarados.
- Crear `ParsedBatch` y `ParsedPaymentLine`.

Este archivo interpreta el contenido; no guarda datos ni publica mensajes.

### FileReceptionServiceImpl.java

Implementa `IFileReceptionService` y coordina la primera etapa.

Secuencia:

1. Valida nombre, extensión y contenido del archivo.
2. Invoca `CsvBatchParserImpl`.
3. Comprueba saldo y condiciones de recepción.
4. Calcula el hash para detectar duplicados.
5. Determina si se procesa inmediatamente o el siguiente día hábil.
6. Guarda el lote inicial en MongoDB.
7. Guarda auditoría y estados.
8. Publica `PaymentLinesReadyEvent`.
9. Devuelve la respuesta al controlador.

La publicación del evento permite responder al cliente sin esperar todo el procesamiento financiero.

### PaymentLinesReadyListener.java

Escucha `PaymentLinesReadyEvent` mediante `@EventListener` y se ejecuta de forma asíncrona.

Responsabilidades:

- Validar que la empresa tenga activo el servicio de pagos masivos.
- Validar la cuenta de origen favorita.
- Clasificar cada código bancario con `IBankCodeCatalogService`.
- Crear los `BatchLineMessage`.
- Programar la publicación si la fecha de proceso es futura.
- Invocar `IPaymentLinePublisher`.

Es el puente entre el evento interno de Spring y RabbitMQ.

### PaymentLinePublisherImpl.java

Implementa `IPaymentLinePublisher` usando `RabbitTemplate`.

Publica cada línea en `payment.exchange` con una routing key según su clasificación:

| Clasificación | Routing key | Cola |
|---|---|---|
| `ON_US` | `onus` | `payment.lines.onus.queue` |
| `OFF_US` | `offus` | `payment.lines.offus.queue` |
| Inválida | `invalid` | `payment.lines.invalid.queue` |

Este archivo publica mensajes; no realiza créditos ni débitos.

## Event

### PaymentLinesReadyEvent.java

Es un evento interno de Spring, no un mensaje RabbitMQ.

Transporta:

- `batchId`: identificador del lote.
- `scheduledProcessAt`: fecha y hora en que debe publicarse.
- `batch`: archivo ya convertido a `ParsedBatch`.
- `duplicateValid`: resultado de la validación de duplicidad.

Lo publica `FileReceptionServiceImpl` y lo consume `PaymentLinesReadyListener`.

```text
FileReceptionServiceImpl
        |
        | evento interno
        v
PaymentLinesReadyListener
        |
        | mensaje externo
        v
RabbitMQ
```

El evento separa la respuesta HTTP de la preparación y publicación de las líneas.

## Dispatch

`dispatch` representa la segunda etapa. Empieza cuando RabbitMQ entrega una línea y termina cuando se actualiza su resultado o se envía al clearinghouse.

### dispatch/service/PaymentDispatchService.java

Es el consumidor principal de RabbitMQ.

Listeners:

- `processOnUsLine`: consume `payment.lines.onus.queue`.
- `processOffUsLine`: consume `payment.lines.offus.queue`.
- `processInvalidLine`: consume `payment.lines.invalid.queue`.

Responsabilidades adicionales:

- Evitar procesar dos veces la misma línea.
- Crear el estado agregado del lote.
- Ejecutar una vez el débito inicial.
- Acreditar cuentas BanQuito para pagos On-Us.
- Publicar pagos Off-Us en `clearing.exchange`.
- Registrar líneas inválidas o rechazadas.
- Actualizar contadores exitosos y rechazados.
- Calcular la tarifa final.
- Solicitar devolución de montos rechazados.
- Enviar notificaciones.
- Cerrar el lote.

RabbitMQ no ejecuta ninguna de estas operaciones. Solo entrega los mensajes a este consumidor.

### dispatch/controller/PaymentBatchStatusController.java

Expone:

```http
GET /api/v2/payments/batches/{batchId}/status
```

Consulta `PaymentDispatchBatchRepository` y devuelve el resultado acumulado del lote.

### dispatch/client/TariffGrpcClient.java

Cliente gRPC para solicitar al servicio de tarifas el valor de la comisión correspondiente al lote.

### dispatch/client/NotificationGrpcClient.java

Cliente gRPC para solicitar al servicio de notificaciones el envío de mensajes al beneficiario.

### dispatch/dto/BatchStatusResponse.java

DTO de respuesta del endpoint de estado. Incluye:

- Estado del lote.
- Registros declarados, exitosos y rechazados.
- Montos exitosos y rechazados.
- Fechas de creación, actualización y finalización.
- Motivo de falla.

### dispatch/model/PaymentBatch.java

Documento MongoDB con el estado agregado del procesamiento.

Conserva contadores, montos, cuenta de origen, estado, fechas y motivo de falla.

### dispatch/model/PaymentDetail.java

Documento MongoDB con el resultado de una línea individual.

Conserva banco, cuenta destino, monto, estado, error y fecha de procesamiento.

### dispatch/model/OffUsClearingMessage.java

Mensaje enviado a `clearing.exchange` cuando la cuenta destino pertenece a otro banco.

Contiene identificadores, código bancario, cuentas, monto, moneda, concepto y fecha valor.

### dispatch/repository/PaymentDispatchBatchRepository.java

Repositorio MongoDB para consultar y persistir `PaymentBatch`.

### dispatch/repository/PaymentDispatchDetailRepository.java

Repositorio MongoDB para consultar y persistir `PaymentDetail`. También participa en la idempotencia de las líneas.

## Diferencia entre service, service/impl y dispatch/service

| Carpeta | Propósito | Ejemplo |
|---|---|---|
| `service` | Define contratos | `IFileReceptionService` |
| `service/impl` | Implementa recepción y publicación | `FileReceptionServiceImpl` |
| `dispatch/service` | Consume RabbitMQ y procesa pagos | `PaymentDispatchService` |

El flujo entre estas carpetas es:

```text
controller
  -> service
  -> service/impl
  -> event
  -> service/impl/PaymentLinesReadyListener
  -> service/impl/PaymentLinePublisherImpl
  -> RabbitMQ
  -> dispatch/service/PaymentDispatchService
```

## Resumen de dependencias

```text
FileReceptionController
  -> IFileReceptionService
     -> FileReceptionServiceImpl
        -> ICsvBatchParser
        -> IBusinessDayService
        -> ICoreBankingClient
        -> PaymentLinesReadyEvent

PaymentLinesReadyListener
  -> IBankCodeCatalogService
  -> IPaymentLinePublisher
     -> PaymentLinePublisherImpl
        -> RabbitMQ

RabbitMQ
  -> PaymentDispatchService
     -> ICoreBankingClient
     -> TariffGrpcClient
     -> NotificationGrpcClient
     -> PaymentDispatchBatchRepository
     -> PaymentDispatchDetailRepository
     -> clearing.exchange
```
> Nota de arquitectura actual: RabbitMQ fue reemplazado por Google Cloud Pub/Sub. Las referencias historicas a RabbitMQ en este documento describen el diseno anterior. El bounded context objetivo de `file-reception-service` es ingesta de archivos; el procesamiento/dispatch debe vivir en un consumidor dedicado.
