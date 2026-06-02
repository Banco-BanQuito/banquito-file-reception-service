package ec.edu.espe.Switch.Batch.service;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import ec.edu.espe.Switch.Batch.dto.FileReceptionResponse;

public interface IFileReceptionService {

    FileReceptionResponse receive(MultipartFile file, String serviceType, String clientRuc) throws IOException;
}
