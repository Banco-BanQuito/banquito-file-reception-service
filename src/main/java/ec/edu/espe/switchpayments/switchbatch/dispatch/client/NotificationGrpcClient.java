package ec.edu.espe.switchpayments.switchbatch.dispatch.client;

import com.banquito.payswitch.notification.NotificationRequest;
import com.banquito.payswitch.notification.NotificationServiceGrpc;
import org.springframework.stereotype.Component;

@Component
public class NotificationGrpcClient {

    private final NotificationServiceGrpc.NotificationServiceBlockingStub blockingStub;

    public NotificationGrpcClient(NotificationServiceGrpc.NotificationServiceBlockingStub blockingStub) {
        this.blockingStub = blockingStub;
    }

    public void sendNotification(NotificationRequest request) {
        blockingStub.sendNotification(request);
    }
}
