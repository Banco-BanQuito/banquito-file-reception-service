# Componentes del File Reception Service

Este documento resume los componentes vigentes despues de separar las responsabilidades de procesamiento.

## Controladores

| Archivo | Funcion |
| --- | --- |
| `FileReceptionController` | Recibe archivos y delega al servicio de recepcion. |
| `BankCodeCatalogController` | Mantiene el catalogo de codigos bancarios usado por el ecosistema Switch. |
| `ApiExceptionHandler` | Convierte errores conocidos en respuestas HTTP controladas. |

## Servicios

| Archivo | Funcion |
| --- | --- |
| `IFileReceptionService` | Contrato de recepcion de lotes. |
| `FileReceptionServiceImpl` | Valida archivo, verifica condiciones iniciales y registra lote/lineas. |
| `ICsvBatchParser` | Contrato de lectura de archivo. |
| `CsvBatchParserImpl` | Valida encabezado, detalles, trailer, totales y hash. |
| `ICoreBankingClient` | Contrato para consultas de validacion contra Core. |
| `CoreBankingClientImpl` | Consulta cuenta, saldo, cuenta favorita y servicio de pagos masivos. |
| `IBusinessDayService` | Contrato para calendario operativo. |
| `BusinessDayServiceImpl` | Determina dia habil y proxima fecha de procesamiento. |
| `IBankCodeCatalogService` | Contrato del catalogo bancario. |
| `BankCodeCatalogServiceImpl` | Administra codigos bancarios. |

## Persistencia

| Archivo | Funcion |
| --- | --- |
| `PaymentBatchDocument` | Documento del lote recibido. |
| `PaymentBatchLineDocument` | Documento de cada linea del archivo. |
| `BatchStatusLog` | Historial de estado del lote. |
| `PaymentBatchRepository` | Acceso a lotes. |
| `PaymentBatchLineRepository` | Acceso a lineas. |
| `BatchStatusLogRepository` | Acceso al historial de estados. |

## Lo que ya no pertenece aqui

Estas responsabilidades fueron retiradas del servicio:

- Publicar mensajes en Pub/Sub.
- Suscribirse a Pub/Sub.
- Procesar pagos ON-US.
- Enviar OFF-US a clearing.
- Cobrar tarifas.
- Enviar notificaciones.
- Ejecutar debitos, creditos o reversos.

Esas responsabilidades viven en microservicios separados del Switch.
