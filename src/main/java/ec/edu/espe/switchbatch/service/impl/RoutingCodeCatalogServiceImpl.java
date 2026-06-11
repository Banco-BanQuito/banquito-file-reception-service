package ec.edu.espe.switchbatch.service.impl;

import org.springframework.stereotype.Service;

import ec.edu.espe.switchbatch.repository.SwitchParameterRepository;
import ec.edu.espe.switchbatch.service.IRoutingCodeCatalogService;

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
}
