package ec.edu.espe.Switch.Batch.service.impl;

import org.springframework.stereotype.Service;

import ec.edu.espe.Switch.Batch.repository.SwitchParameterRepository;
import ec.edu.espe.Switch.Batch.service.IRoutingCodeCatalogService;

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
