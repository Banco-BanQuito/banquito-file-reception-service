package ec.edu.espe.Switch.Batch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ec.edu.espe.Switch.Batch.model.PaymentFileValidation;

public interface PaymentFileValidationRepository extends JpaRepository<PaymentFileValidation, Long> {
}
