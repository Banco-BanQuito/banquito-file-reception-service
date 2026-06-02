package ec.edu.espe.Switch.Batch.service.impl;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import ec.edu.espe.Switch.Batch.event.PaymentLinesReadyEvent;
import ec.edu.espe.Switch.Batch.service.IPaymentLinePublisher;

@Component
public class PaymentLinesReadyListener {

    private final IPaymentLinePublisher paymentLinePublisher;

    public PaymentLinesReadyListener(IPaymentLinePublisher paymentLinePublisher) {
        this.paymentLinePublisher = paymentLinePublisher;
    }

    @Async
    @EventListener
    public void onPaymentLinesReady(PaymentLinesReadyEvent event) {
        paymentLinePublisher.publish(event.batchId(), event.scheduledProcessAt(), event.messages());
    }
}
