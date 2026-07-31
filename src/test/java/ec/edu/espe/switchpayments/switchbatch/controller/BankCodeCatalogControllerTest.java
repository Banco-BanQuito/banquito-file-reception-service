package ec.edu.espe.switchpayments.switchbatch.controller;

import ec.edu.espe.switchpayments.switchbatch.model.SwitchParameter;
import ec.edu.espe.switchpayments.switchbatch.service.IBankCodeCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankCodeCatalogControllerTest {

    @Mock
    private IBankCodeCatalogService catalogService;

    private BankCodeCatalogController controller;

    @BeforeEach
    void setUp() {
        controller = new BankCodeCatalogController(catalogService);
    }

    @Test
    void listAllDelegatesToCatalogService() {
        when(catalogService.listAll()).thenReturn(List.of(new SwitchParameter()));

        assertThat(controller.listAll()).hasSize(1);
    }

    @Test
    void classifyReturnsClassificationWhenCodeIsKnown() {
        when(catalogService.classify("0010")).thenReturn("ON_US");

        var response = controller.classify("0010");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().classification()).isEqualTo("ON_US");
    }

    @Test
    void registerCreatesNewBankCodeWhenNotAlreadyRegistered() {
        when(catalogService.isValid("0020")).thenReturn(false);
        SwitchParameter created = new SwitchParameter();
        when(catalogService.register("0020", "Banco Externo", "OFF_US", "desc")).thenReturn(created);
        BankCodeCatalogController.BankCodeRequest request =
                new BankCodeCatalogController.BankCodeRequest("0020", "Banco Externo", "OFF_US", "desc");

        ResponseEntity<SwitchParameter> response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(created);
    }

    @Test
    void classifyThrowsNotFoundWhenCodeIsUnknown() {
        when(catalogService.classify("9999")).thenReturn(null);

        assertThatThrownBy(() -> controller.classify("9999"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void registerThrowsConflictWhenCodeAlreadyExists() {
        when(catalogService.isValid("0010")).thenReturn(true);
        BankCodeCatalogController.BankCodeRequest request =
                new BankCodeCatalogController.BankCodeRequest("0010", "Banco Existente", "ON_US", null);

        assertThatThrownBy(() -> controller.register(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deleteRemovesExistingCode() {
        when(catalogService.isValid("0010")).thenReturn(true);

        ResponseEntity<Void> response = controller.delete("0010");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(catalogService).delete("0010");
    }
}
