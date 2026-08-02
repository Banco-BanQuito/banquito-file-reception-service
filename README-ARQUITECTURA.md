# Arquitectura del File Reception Service

## Decision arquitectonica

`banquito-file-reception-service` queda limitado al bounded context de recepcion de archivos. Esto evita que el mismo microservicio reciba archivos, clasifique lineas, publique mensajes, consuma colas y ejecute pagos.

## Contexto delimitado

| Pregunta | Respuesta |
| --- | --- |
| Que recibe | Archivos CSV/TXT de pagos masivos. |
| Que valida | Estructura, totales, duplicados, cuenta principal, saldo y servicio activo de pagos masivos. |
| Que persiste | Lote, lineas y estado inicial. |
| Que entrega | Datos registrados para que otros servicios continuen el flujo. |
| Que no hace | Publicar, suscribirse, debitar, acreditar, compensar, cobrar tarifas o notificar. |

## Flujo actual

```text
Frontend Empresas / Apigee
  -> FileReceptionController
  -> FileReceptionServiceImpl
     -> CsvBatchParserImpl
     -> BusinessDayServiceImpl
     -> CoreBankingClientImpl
     -> PaymentBatchRepository
     -> PaymentBatchLineRepository
     -> BatchStatusLogRepository
```

## Flujo externo al servicio

```text
Lote registrado
  -> payment-line-classifier-service
  -> payment-line-publisher-service
  -> Google Cloud Pub/Sub
  -> payment-line-subscriber-service
  -> internal-payment / clearinghouse
```

## Justificacion

La separacion mejora:

- Cohesion: cada servicio tiene una razon clara para cambiar.
- Escalabilidad: recepcion, clasificacion, publicacion y consumo pueden escalar distinto.
- Mantenibilidad: los errores de procesamiento no afectan directamente la carga del archivo.
- Trazabilidad: el lote queda persistido antes de entrar al flujo asincrono.
- Cumplimiento cloud: Pub/Sub queda como broker administrado, pero fuera del microservicio de recepcion.

## Estado del codigo

Se retiraron del repo:

- Paquete `dispatch`.
- Evento `PaymentLinesReadyEvent`.
- DTO `BatchLineMessage`.
- Interfaces e implementaciones de publicacion.
- Subscribers de Pub/Sub.
- Clientes gRPC de tarifa/notificacion.
- Dependencias de Pub/Sub, gRPC y protobuf.
