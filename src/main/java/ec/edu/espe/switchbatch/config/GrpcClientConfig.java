package ec.edu.espe.switchbatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ec.edu.espe.switchbatch.grpc.PaymentLineIngestionServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel paymentLineManagedChannel(FileReceptionProperties properties) {
        return ManagedChannelBuilder
                .forAddress(properties.getGrpcHost(), properties.getGrpcPort())
                .usePlaintext()
                .build();
    }

    @Bean
    public PaymentLineIngestionServiceGrpc.PaymentLineIngestionServiceBlockingStub paymentLineIngestionStub(
            ManagedChannel paymentLineManagedChannel) {
        return PaymentLineIngestionServiceGrpc.newBlockingStub(paymentLineManagedChannel);
    }
}
