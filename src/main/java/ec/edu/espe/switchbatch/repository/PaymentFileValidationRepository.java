package ec.edu.espe.switchbatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ec.edu.espe.switchbatch.model.PaymentFileValidation;

public interface PaymentFileValidationRepository extends JpaRepository<PaymentFileValidation, Long> {
}
