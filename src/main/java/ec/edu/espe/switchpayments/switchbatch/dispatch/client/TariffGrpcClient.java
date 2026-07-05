package ec.edu.espe.switchpayments.switchbatch.dispatch.client;

import ec.edu.espe.banquito.banquitotariffservice.grpc.TariffCalculationGrpcRequest;
import ec.edu.espe.banquito.banquitotariffservice.grpc.TariffCalculationGrpcResponse;
import ec.edu.espe.banquito.banquitotariffservice.grpc.TariffGrpcServiceGrpc;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class TariffGrpcClient {

    private final TariffGrpcServiceGrpc.TariffGrpcServiceBlockingStub blockingStub;

    public TariffGrpcClient(TariffGrpcServiceGrpc.TariffGrpcServiceBlockingStub blockingStub) {
        this.blockingStub = blockingStub;
    }

    public TariffCalculationGrpcResponse calculateTariff(TariffCalculationGrpcRequest request) {
        return blockingStub
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .calculateTariff(request);
    }
}
