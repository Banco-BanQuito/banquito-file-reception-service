package ec.edu.espe.switchpayments.switchbatch.service;

import ec.edu.espe.switchpayments.switchbatch.dto.ParsedBatch;

public interface IBatchLineRegistrationService {

    void registerLinesAsync(String batchId, ParsedBatch batch, String readyStatus);
}
