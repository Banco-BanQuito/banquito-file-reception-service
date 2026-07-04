package ec.edu.espe.switchpayments.switchbatch.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import ec.edu.espe.switchpayments.switchbatch.model.SwitchParameter;
import ec.edu.espe.switchpayments.switchbatch.repository.SwitchParameterRepository;
import ec.edu.espe.switchpayments.switchbatch.service.IRoutingCodeCatalogService;

@Service
public class RoutingCodeCatalogServiceImpl implements IRoutingCodeCatalogService {

    private final SwitchParameterRepository switchParameterRepository;

    public RoutingCodeCatalogServiceImpl(SwitchParameterRepository switchParameterRepository) {
        this.switchParameterRepository = switchParameterRepository;
    }

    @Override
    public boolean isValid(String routingCode) {
        if (routingCode == null || routingCode.isBlank()) {
            return false;
        }
        return switchParameterRepository.existsById(routingCode.trim());
    }

    @Override
    public String classify(String routingCode) {
        if (routingCode == null || routingCode.isBlank()) {
            return null;
        }
        return switchParameterRepository.findById(routingCode.trim())
                .map(SwitchParameter::getValueString)
                .orElse(null);
    }

    @Override
    public List<SwitchParameter> listAll() {
        return switchParameterRepository.findAll();
    }
}
