package ec.edu.espe.switchpayments.switchbatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.banquito.payswitch.notification.NotificationServiceGrpc;
import ec.edu.espe.banquito.banquitotariffservice.grpc.TariffGrpcServiceGrpc;
import ec.edu.espe.switchpayments.switchbatch.grpc.clearing.ClearingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel tariffManagedChannel(FileReceptionProperties properties) {
        return ManagedChannelBuilder
                .forAddress(properties.getTariffGrpcHost(), properties.getTariffGrpcPort())
                .usePlaintext()
                .build();
    }

    @Bean
    public TariffGrpcServiceGrpc.TariffGrpcServiceBlockingStub tariffGrpcStub(ManagedChannel tariffManagedChannel) {
        return TariffGrpcServiceGrpc.newBlockingStub(tariffManagedChannel);
    }

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel notificationManagedChannel(FileReceptionProperties properties) {
        return ManagedChannelBuilder
                .forAddress(properties.getNotificationGrpcHost(), properties.getNotificationGrpcPort())
                .usePlaintext()
                .build();
    }

    @Bean
    public NotificationServiceGrpc.NotificationServiceBlockingStub notificationGrpcStub(ManagedChannel notificationManagedChannel) {
        return NotificationServiceGrpc.newBlockingStub(notificationManagedChannel);
    }

    // Fase 5 Parte 2 (TAREA B): canal gRPC hacia banquito-clearinghouse-service, reemplazando
    // la publicacion Pub/Sub legacy (PubSubClearingPublisher / banquito-clearing-events), que
    // no tiene consumidor Java activo (ver ClearinghouseClient en este mismo servicio).
    @Bean(destroyMethod = "shutdown")
    public ManagedChannel clearinghouseManagedChannel(FileReceptionProperties properties) {
        return ManagedChannelBuilder
                .forAddress(properties.getClearinghouseGrpcHost(), properties.getClearinghouseGrpcPort())
                .usePlaintext()
                .build();
    }

    @Bean
    public ClearingServiceGrpc.ClearingServiceBlockingStub clearingGrpcStub(ManagedChannel clearinghouseManagedChannel) {
        return ClearingServiceGrpc.newBlockingStub(clearinghouseManagedChannel);
    }
}
