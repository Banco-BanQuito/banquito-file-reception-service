package ec.edu.espe.switchpayments.switchbatch.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import ec.edu.espe.switchpayments.switchbatch.model.SwitchParameter;
import ec.edu.espe.switchpayments.switchbatch.repository.SwitchParameterRepository;
import ec.edu.espe.switchpayments.switchbatch.service.IBankCodeCatalogService;

@Service
public class BankCodeCatalogServiceImpl implements IBankCodeCatalogService {

    private final SwitchParameterRepository switchParameterRepository;

    public BankCodeCatalogServiceImpl(SwitchParameterRepository switchParameterRepository) {
        this.switchParameterRepository = switchParameterRepository;
    }

    @Override
    public boolean isValid(String bankCode) {
        if (bankCode == null || bankCode.isBlank()) {
            return false;
        }
        return switchParameterRepository.existsById(bankCode.trim());
    }

    @Override
    public String classify(String bankCode) {
        if (bankCode == null || bankCode.isBlank()) {
            return null;
        }
        return switchParameterRepository.findById(bankCode.trim())
                .map(SwitchParameter::getValueString)
                .orElse(null);
    }

    @Override
    public List<SwitchParameter> listAll() {
        return switchParameterRepository.findAll();
    }

    @Override
    public SwitchParameter register(String code, String name, String classification, String description) {
        SwitchParameter parameter = new SwitchParameter();
        parameter.setCode(code);
        parameter.setName(name);
        parameter.setValueString(classification);
        parameter.setDataType("BANK_ROUTING_CODE");
        parameter.setDescription(description);
        return switchParameterRepository.save(parameter);
    }

    @Override
    public void delete(String code) {
        switchParameterRepository.deleteById(code);
    }
}
