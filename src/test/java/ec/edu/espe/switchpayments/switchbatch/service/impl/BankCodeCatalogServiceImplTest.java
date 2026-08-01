package ec.edu.espe.switchpayments.switchbatch.service.impl;

import ec.edu.espe.switchpayments.switchbatch.model.SwitchParameter;
import ec.edu.espe.switchpayments.switchbatch.repository.SwitchParameterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankCodeCatalogServiceImplTest {

    @Mock
    private SwitchParameterRepository switchParameterRepository;

    private BankCodeCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BankCodeCatalogServiceImpl(switchParameterRepository);
    }

    @Test
    void isValidReturnsTrueWhenCodeExists() {
        when(switchParameterRepository.existsById("0010")).thenReturn(true);

        assertThat(service.isValid("0010")).isTrue();
    }

    @Test
    void isValidReturnsFalseWhenCodeIsBlank() {
        assertThat(service.isValid(" ")).isFalse();
    }

    @Test
    void classifyReturnsValueStringWhenFound() {
        SwitchParameter parameter = new SwitchParameter();
        parameter.setValueString("ON_US");
        when(switchParameterRepository.findById("0010")).thenReturn(Optional.of(parameter));

        assertThat(service.classify("0010")).isEqualTo("ON_US");
    }

    @Test
    void classifyReturnsNullWhenNotFound() {
        when(switchParameterRepository.findById("9999")).thenReturn(Optional.empty());

        assertThat(service.classify("9999")).isNull();
    }

    @Test
    void listAllDelegatesToRepository() {
        when(switchParameterRepository.findAll()).thenReturn(List.of(new SwitchParameter()));

        assertThat(service.listAll()).hasSize(1);
    }

    @Test
    void registerBuildsAndSavesSwitchParameterWithBankRoutingCodeDataType() {
        when(switchParameterRepository.save(org.mockito.ArgumentMatchers.any(SwitchParameter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SwitchParameter result = service.register("0020", "Banco Externo", "OFF_US", "desc");

        assertThat(result.getDataType()).isEqualTo("BANK_ROUTING_CODE");
    }

    @Test
    void deleteDelegatesToRepository() {
        service.delete("0010");

        verify(switchParameterRepository).deleteById("0010");
    }
}
