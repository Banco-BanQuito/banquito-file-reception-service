package ec.edu.espe.switchbatch.service;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import ec.edu.espe.switchbatch.dto.FileReceptionResponse;

public interface IFileReceptionService {

    FileReceptionResponse receive(MultipartFile file, String serviceType, String clientRuc) throws IOException;
}
