package ec.edu.espe.Switch.Batch.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import ec.edu.espe.Switch.Batch.model.SwitchParameter;
import ec.edu.espe.Switch.Batch.repository.SwitchParameterRepository;

@Component
public class SwitchParameterInitializer implements CommandLineRunner {

    private final SwitchParameterRepository switchParameterRepository;

    public SwitchParameterInitializer(SwitchParameterRepository switchParameterRepository) {
        this.switchParameterRepository = switchParameterRepository;
    }

    @Override
    public void run(String... args) {
        seedBank("001", "BanQuito", "ON_US", "Banco interno BanQuito");
        for (String code : List.of("002", "003", "004", "005")) {
            seedBank(code, "Banco externo " + code, "OFF_US", "Banco valido del sistema financiero");
        }
    }

    private void seedBank(String code, String name, String value, String description) {
        if (switchParameterRepository.existsById(code)) {
            return;
        }
        SwitchParameter parameter = new SwitchParameter();
        parameter.setCode(code);
        parameter.setName(name);
        parameter.setValueString(value);
        parameter.setDataType("BANK_ROUTING_CODE");
        parameter.setDescription(description);
        switchParameterRepository.save(parameter);
    }
}
