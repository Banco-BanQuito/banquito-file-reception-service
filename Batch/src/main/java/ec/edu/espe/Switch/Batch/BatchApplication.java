package ec.edu.espe.Switch.Batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import ec.edu.espe.Switch.Batch.config.FileReceptionProperties;
import ec.edu.espe.Switch.Batch.config.PostgreSqlDatabaseInitializer;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(FileReceptionProperties.class)
public class BatchApplication {

	public static void main(String[] args) {
		PostgreSqlDatabaseInitializer.ensureDatabaseExists();
		SpringApplication.run(BatchApplication.class, args);
	}

}
