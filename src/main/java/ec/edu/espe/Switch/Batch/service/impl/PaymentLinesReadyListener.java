package ec.edu.espe.Switch.Batch.service.impl;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import ec.edu.espe.Switch.Batch.event.PaymentLinesReadyEvent;
import ec.edu.espe.Switch.Batch.service.IPaymentLinePublisher;

import java.time.Instant;

@Component
public class PaymentLinesReadyListener {

    private final IPaymentLinePublisher paymentLinePublisher;
    private final TaskScheduler taskScheduler;

    public PaymentLinesReadyListener(IPaymentLinePublisher paymentLinePublisher,
                                     TaskScheduler taskScheduler) {
        this.paymentLinePublisher = paymentLinePublisher;
        this.taskScheduler = taskScheduler;
    }

    @Async
    @EventListener
    public void onPaymentLinesReady(PaymentLinesReadyEvent event) {
        if (event.scheduledProcessAt().isAfter(Instant.now())) {
            taskScheduler.schedule(
                    () -> paymentLinePublisher.publish(event.batchId(), event.scheduledProcessAt(), event.messages()),
                    event.scheduledProcessAt());
            return;
        }

        paymentLinePublisher.publish(event.batchId(), event.scheduledProcessAt(), event.messages());
    }
}
