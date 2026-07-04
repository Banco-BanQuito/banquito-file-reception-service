package ec.edu.espe.switchpayments.switchbatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ec.edu.espe.switchpayments.switchbatch.model.PaymentFileValidation;

public interface PaymentFileValidationRepository extends JpaRepository<PaymentFileValidation, Long> {
}
