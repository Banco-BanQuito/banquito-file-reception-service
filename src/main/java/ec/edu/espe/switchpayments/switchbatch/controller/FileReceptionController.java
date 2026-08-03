package ec.edu.espe.switchpayments.switchbatch.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ec.edu.espe.switchpayments.switchbatch.dto.BatchStatusResponse;
import ec.edu.espe.switchpayments.switchbatch.dto.FileReceptionResponse;
import ec.edu.espe.switchpayments.switchbatch.dto.HealthResponse;
import ec.edu.espe.switchpayments.switchbatch.service.IFileReceptionService;

@RestController
@RequestMapping({"/api/v1/payments", "/api/v2/payments"})
public class FileReceptionController {

    private static final String ERROR_FIELD = "error";

    private final IFileReceptionService fileReceptionService;

    public FileReceptionController(IFileReceptionService fileReceptionService) {
        this.fileReceptionService = fileReceptionService;
    }

    @PostMapping("/batches")
    public ResponseEntity<Object> receiveBatch(@RequestParam("file") MultipartFile file,
                                               @RequestParam("serviceType") String serviceType,
                                               @RequestParam("clientRuc") String clientRuc) {
        try {
            FileReceptionResponse response = fileReceptionService.receive(file, serviceType, clientRuc);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_FIELD, e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_FIELD, "No se pudo leer el archivo: " + e.getMessage()));
        }
    }

    @GetMapping("/batches/{batchId}/status")
    public ResponseEntity<BatchStatusResponse> getBatchStatus(@PathVariable String batchId) {
        try {
            return ResponseEntity.ok(fileReceptionService.getStatus(batchId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "file-reception-service", "2.0");
    }
}
