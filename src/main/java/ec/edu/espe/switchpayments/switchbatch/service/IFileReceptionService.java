package ec.edu.espe.switchpayments.switchbatch.service;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import ec.edu.espe.switchpayments.switchbatch.dto.BatchStatusResponse;
import ec.edu.espe.switchpayments.switchbatch.dto.FileReceptionResponse;

public interface IFileReceptionService {

    FileReceptionResponse receive(MultipartFile file, String serviceType, String clientRuc) throws IOException;

    BatchStatusResponse getStatus(String batchId);
}
