package ec.edu.espe.switchpayments.switchbatch.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public Queue onUsQueue(FileReceptionProperties properties) {
        return new Queue(properties.getRabbitQueueOnUs(), true);
    }

    @Bean
    public Queue offUsQueue(FileReceptionProperties properties) {
        return new Queue(properties.getRabbitQueueOffUs(), true);
    }

    @Bean
    public Queue invalidRoutingQueue(FileReceptionProperties properties) {
        return new Queue(properties.getRabbitQueueInvalid(), true);
    }

    @Bean
    public DirectExchange paymentExchange(FileReceptionProperties properties) {
        return new DirectExchange(properties.getRabbitExchange(), true, false);
    }

    @Bean
    public Binding onUsBinding(Queue onUsQueue, DirectExchange paymentExchange,
                               FileReceptionProperties properties) {
        return BindingBuilder.bind(onUsQueue).to(paymentExchange).with(properties.getRabbitRoutingKeyOnUs());
    }

    @Bean
    public Binding offUsBinding(Queue offUsQueue, DirectExchange paymentExchange,
                                FileReceptionProperties properties) {
        return BindingBuilder.bind(offUsQueue).to(paymentExchange).with(properties.getRabbitRoutingKeyOffUs());
    }

    @Bean
    public Binding invalidRoutingBinding(Queue invalidRoutingQueue, DirectExchange paymentExchange,
                                         FileReceptionProperties properties) {
        return BindingBuilder.bind(invalidRoutingQueue).to(paymentExchange).with(properties.getRabbitRoutingKeyInvalid());
    }

    @Bean
    public Queue clearingOutboundQueue(FileReceptionProperties properties) {
        return new Queue("clearing.outbound.queue", true);
    }

    @Bean
    public DirectExchange clearingExchange(FileReceptionProperties properties) {
        return new DirectExchange(properties.getClearingExchange(), true, false);
    }

    @Bean
    public Binding clearingOutboundBinding(Queue clearingOutboundQueue, DirectExchange clearingExchange,
                                           FileReceptionProperties properties) {
        return BindingBuilder.bind(clearingOutboundQueue).to(clearingExchange).with(properties.getClearingRoutingKey());
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}
