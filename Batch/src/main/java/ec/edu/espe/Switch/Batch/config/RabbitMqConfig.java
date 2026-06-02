package ec.edu.espe.Switch.Batch.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public Queue paymentLinesQueue(FileReceptionProperties properties) {
        return new Queue(properties.getRabbitQueue(), true);
    }
}
