package ec.edu.espe.switchbatch.service;

import java.io.IOException;
import java.io.InputStream;

import ec.edu.espe.switchbatch.dto.ParsedBatch;

public interface ICsvBatchParser {

    ParsedBatch parse(InputStream inputStream, String serviceType, String clientRuc) throws IOException;
}
