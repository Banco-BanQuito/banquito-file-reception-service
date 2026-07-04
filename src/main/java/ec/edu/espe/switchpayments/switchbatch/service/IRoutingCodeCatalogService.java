package ec.edu.espe.switchpayments.switchbatch.service;

import ec.edu.espe.switchpayments.switchbatch.model.SwitchParameter;
import java.util.List;

public interface IRoutingCodeCatalogService {

    boolean isValid(String routingCode);

    String classify(String routingCode);

    List<SwitchParameter> listAll();
}
