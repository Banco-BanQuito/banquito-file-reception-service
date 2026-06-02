package ec.edu.espe.Switch.Batch.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ec.edu.espe.Switch.Batch.dto.FileReceptionResponse;
import ec.edu.espe.Switch.Batch.dto.HealthResponse;
import ec.edu.espe.Switch.Batch.service.IFileReceptionService;

@RestController
@RequestMapping({"/api/v1/payments", "/api/v2/payments"})
public class FileReceptionController {

    private final IFileReceptionService fileReceptionService;

    public FileReceptionController(IFileReceptionService fileReceptionService) {
        this.fileReceptionService = fileReceptionService;
    }

    @PostMapping("/batches")
    public ResponseEntity<?> receiveBatch(@RequestParam("file") MultipartFile file,
                                          @RequestParam("serviceType") String serviceType,
                                          @RequestParam("clientRuc") String clientRuc) {
        try {
            FileReceptionResponse response = fileReceptionService.receive(file, serviceType, clientRuc);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo leer el archivo: " + e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "file-reception-service", "2.0");
    }
}
