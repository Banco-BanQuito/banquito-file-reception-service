package ec.edu.espe.switchpayments.switchbatch.service;

import ec.edu.espe.switchpayments.switchbatch.model.SwitchParameter;
import java.util.List;

public interface IBankCodeCatalogService {

    boolean isValid(String bankCode);

    String classify(String bankCode);

    List<SwitchParameter> listAll();

    SwitchParameter register(String code, String name, String classification, String description);

    void delete(String code);
}
