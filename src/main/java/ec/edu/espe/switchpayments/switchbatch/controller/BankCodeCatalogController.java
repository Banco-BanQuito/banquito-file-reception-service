package ec.edu.espe.switchpayments.switchbatch.controller;

import ec.edu.espe.switchpayments.switchbatch.model.SwitchParameter;
import ec.edu.espe.switchpayments.switchbatch.service.IBankCodeCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/v1/routing-codes", "/api/v2/payments/routing-codes"})
public class BankCodeCatalogController {

    private final IBankCodeCatalogService catalogService;

    public BankCodeCatalogController(IBankCodeCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<SwitchParameter> listAll() {
        return catalogService.listAll();
    }

    @GetMapping("/{code}/classify")
    public ResponseEntity<BankClassificationResponse> classify(@PathVariable String code) {
        String classification = catalogService.classify(code);
        if (classification == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Código bancario no reconocido: " + code);
        }
        return ResponseEntity.ok(new BankClassificationResponse(code, classification));
    }

    @PostMapping
    public ResponseEntity<SwitchParameter> register(@Valid @RequestBody BankCodeRequest request) {
        if (catalogService.isValid(request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El código " + request.code() + " ya existe en el catálogo.");
        }
        SwitchParameter parameter = catalogService.register(
                request.code(), request.name(), request.classification(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(parameter);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        if (!catalogService.isValid(code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Código " + code + " no encontrado en el catálogo.");
        }
        catalogService.delete(code);
        return ResponseEntity.noContent().build();
    }

    public record BankClassificationResponse(String code, String classification) {}

    public record BankCodeRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "ON_US|OFF_US") String classification,
            @Size(max = 250) String description) {}
}
