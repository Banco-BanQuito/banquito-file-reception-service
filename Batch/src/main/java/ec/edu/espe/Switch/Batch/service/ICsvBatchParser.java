package ec.edu.espe.Switch.Batch.service;

import java.io.IOException;
import java.io.InputStream;

import ec.edu.espe.Switch.Batch.dto.ParsedBatch;

public interface ICsvBatchParser {

    ParsedBatch parse(InputStream inputStream, String serviceType, String clientRuc) throws IOException;
}
