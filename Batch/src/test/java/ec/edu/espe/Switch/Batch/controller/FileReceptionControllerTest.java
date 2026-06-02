package ec.edu.espe.Switch.Batch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import ec.edu.espe.Switch.Batch.dto.FileReceptionResponse;
import ec.edu.espe.Switch.Batch.service.IFileReceptionService;

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
}
