package ec.edu.espe.switchpayments.switchbatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import ec.edu.espe.switchpayments.switchbatch.config.FileReceptionProperties;

@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties(FileReceptionProperties.class)
public class BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchApplication.class, args);
    }
}
