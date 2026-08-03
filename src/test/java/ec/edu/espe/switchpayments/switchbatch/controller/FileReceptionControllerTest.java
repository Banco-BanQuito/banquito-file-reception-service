package ec.edu.espe.switchpayments.switchbatch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import ec.edu.espe.switchpayments.switchbatch.dto.FileReceptionResponse;
import ec.edu.espe.switchpayments.switchbatch.service.IFileReceptionService;

class FileReceptionControllerTest {

    @Test
    void returnsHttp202ForAcceptedBatch() throws Exception {
        IFileReceptionService service = org.mockito.Mockito.mock(IFileReceptionService.class);
        MockMultipartFile file = new MockMultipartFile("file", "archivo.csv", "text/csv", "data".getBytes());
        when(service.receive(file, "NOMINA", "0912345678")).thenReturn(new FileReceptionResponse(
                "batch-1",
                "RECEIVED",
                "Lote recibido, procesando en segundo plano",
                Instant.parse("2026-05-30T14:00:00Z"),
                1,
                BigDecimal.TEN));

        var response = new FileReceptionController(service).receiveBatch(file, "NOMINA", "0912345678");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    void returnsBadRequestWhenServiceThrowsIllegalArgumentException() throws Exception {
        IFileReceptionService service = org.mockito.Mockito.mock(IFileReceptionService.class);
        MockMultipartFile file = new MockMultipartFile("file", "archivo.csv", "text/csv", "data".getBytes());
        when(service.receive(file, "NOMINA", "0912345678"))
                .thenThrow(new IllegalArgumentException("Tipo de servicio invalido"));

        var response = new FileReceptionController(service).receiveBatch(file, "NOMINA", "0912345678");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void returnsBadRequestWhenServiceThrowsIOException() throws Exception {
        IFileReceptionService service = org.mockito.Mockito.mock(IFileReceptionService.class);
        MockMultipartFile file = new MockMultipartFile("file", "archivo.csv", "text/csv", "data".getBytes());
        when(service.receive(file, "NOMINA", "0912345678")).thenThrow(new IOException("disco lleno"));

        var response = new FileReceptionController(service).receiveBatch(file, "NOMINA", "0912345678");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void healthReturnsUpStatus() {
        IFileReceptionService service = org.mockito.Mockito.mock(IFileReceptionService.class);

        assertThat(new FileReceptionController(service).health().status()).isEqualTo("UP");
    }

    @Test
    void getBatchStatusReturnsOkWithBodyWhenBatchExists() {
        IFileReceptionService service = org.mockito.Mockito.mock(IFileReceptionService.class);
        var statusResponse = new ec.edu.espe.switchpayments.switchbatch.dto.BatchStatusResponse(
                "batch-1", "PROCESSING", 10, 0, 0, 10,
                BigDecimal.ZERO, BigDecimal.ZERO, Instant.parse("2026-05-30T14:00:00Z"), null, null,
                "Registrando lineas del archivo: 0/10", null);
        when(service.getStatus("batch-1")).thenReturn(statusResponse);

        var response = new FileReceptionController(service).getBatchStatus("batch-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("PROCESSING");
    }

    @Test
    void getBatchStatusReturnsNotFoundWhenBatchDoesNotExist() {
        IFileReceptionService service = org.mockito.Mockito.mock(IFileReceptionService.class);
        when(service.getStatus("batch-x")).thenThrow(new IllegalArgumentException("Lote no encontrado: batch-x"));

        var response = new FileReceptionController(service).getBatchStatus("batch-x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
