package ec.edu.espe.switchpayments.switchbatch.controller;

import ec.edu.espe.switchpayments.switchbatch.model.SwitchParameter;
import ec.edu.espe.switchpayments.switchbatch.repository.SwitchParameterRepository;
import ec.edu.espe.switchpayments.switchbatch.service.IRoutingCodeCatalogService;
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
public class RoutingCodeCatalogController {

    private final IRoutingCodeCatalogService catalogService;
    private final SwitchParameterRepository repository;

    public RoutingCodeCatalogController(IRoutingCodeCatalogService catalogService,
                                        SwitchParameterRepository repository) {
        this.catalogService = catalogService;
        this.repository = repository;
    }

    @GetMapping
    public List<SwitchParameter> listAll() {
        return catalogService.listAll();
    }

    @GetMapping("/{code}/classify")
    public ResponseEntity<RoutingClassificationResponse> classify(@PathVariable String code) {
        String classification = catalogService.classify(code);
        if (classification == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Código de enrutamiento no reconocido: " + code);
        }
        return ResponseEntity.ok(new RoutingClassificationResponse(code, classification));
    }

    @PostMapping
    public ResponseEntity<SwitchParameter> register(@Valid @RequestBody RoutingCodeRequest request) {
        if (repository.existsById(request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El código " + request.code() + " ya existe en el catálogo.");
        }
        SwitchParameter param = new SwitchParameter();
        param.setCode(request.code());
        param.setName(request.name());
        param.setValueString(request.classification());
        param.setDataType("BANK_ROUTING_CODE");
        param.setDescription(request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(param));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        if (!repository.existsById(code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Código " + code + " no encontrado en el catálogo.");
        }
        repository.deleteById(code);
        return ResponseEntity.noContent().build();
    }

    public record RoutingClassificationResponse(String code, String classification) {}

    public record RoutingCodeRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "ON_US|OFF_US") String classification,
            @Size(max = 250) String description) {}
}
