package ec.edu.espe.switchbatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import ec.edu.espe.switchbatch.config.FileReceptionProperties;
import ec.edu.espe.switchbatch.config.PostgreSqlDatabaseInitializer;

@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties({FileReceptionProperties.class, PostgreSqlDatabaseInitializer.class})
public class BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchApplication.class, args);
    }
}
