# BanQuito File Reception Service

Microservicio del Switch de Pagos Masivos encargado unicamente de la ingesta del archivo.

## Responsabilidad del servicio

`banquito-file-reception-service` pertenece al bounded context de recepcion de archivos. Su trabajo termina cuando el lote queda validado y registrado.

Responsabilidades actuales:

- Recibir archivos CSV/TXT de pagos masivos.
- Validar estructura del encabezado, detalle y trailer.
- Validar que la cuenta principal tenga saldo suficiente para el monto declarado.
- Validar contra Core que el cliente tenga servicio de pagos masivos activo.
- Registrar el lote y sus lineas en persistencia.
- Dejar el lote disponible para que otros microservicios del Switch continúen el flujo.

Este servicio no publica mensajes, no consume mensajes y no procesa pagos.

## Flujo

```text
Cliente / Frontend Empresas
  -> POST /api/v2/payments/batches
  -> FileReceptionController
  -> FileReceptionServiceImpl
  -> CsvBatchParserImpl
  -> CoreBankingClientImpl
  -> MySQL / MongoDB
```

Luego, los microservicios separados del Switch toman el lote registrado:

```text
payment-line-classifier-service
  -> payment-line-publisher-service
  -> Google Cloud Pub/Sub
  -> payment-line-subscriber-service
  -> internal-payment / clearinghouse
```

## Componentes principales

| Componente | Responsabilidad |
| --- | --- |
| `FileReceptionController` | Expone endpoints HTTP para cargar lotes y consultar salud. |
| `FileReceptionServiceImpl` | Orquesta validacion, deteccion de duplicados, calendario y registro del lote. |
| `CsvBatchParserImpl` | Lee y valida estructura del archivo. |
| `CoreBankingClientImpl` | Consulta Core para validaciones necesarias antes de aceptar el archivo. |
| `PaymentBatchRepository` | Registra el lote. |
| `PaymentBatchLineRepository` | Registra las lineas del lote. |
| `BatchStatusLogRepository` | Guarda trazabilidad de cambios de estado. |

## Endpoints

| Metodo | Ruta | Uso |
| --- | --- | --- |
| `POST` | `/api/v1/payments/batches` | Carga de lote. |
| `POST` | `/api/v2/payments/batches` | Carga de lote versionada. |
| `GET` | `/api/v1/payments/health` | Salud basica. |
| `GET` | `/api/v2/payments/health` | Salud basica versionada. |

## Configuracion

Las variables sensibles deben venir desde Secret Manager/Kubernetes Secret. Las variables no sensibles se configuran desde ConfigMap.

Variables relevantes:

| Variable | Uso |
| --- | --- |
| `DB_URL` | Conexion MySQL del servicio. |
| `DB_USER` | Usuario MySQL. |
| `DB_PASS` | Password MySQL. |
| `MONGO_URI` | Conexion MongoDB. |
| `CORE_GATEWAY_URL` | URL del Core expuesta mediante Apigee/Gateway. |
| `APP_CORE_VALIDATION_ENABLED` | Habilita o deshabilita validaciones contra Core. |
| `APP_CUTOFF_HOUR` | Hora limite de recepcion del lote. |

## Validacion local

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q test
```
