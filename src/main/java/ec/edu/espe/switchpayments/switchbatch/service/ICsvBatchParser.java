package ec.edu.espe.switchpayments.switchbatch.service;

import java.io.IOException;
import java.io.InputStream;

import ec.edu.espe.switchpayments.switchbatch.dto.ParsedBatch;

public interface ICsvBatchParser {

    ParsedBatch parse(InputStream inputStream, String serviceType, String clientRuc) throws IOException;
}
