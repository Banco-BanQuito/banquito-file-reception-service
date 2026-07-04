package ec.edu.espe.switchpayments.switchbatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ec.edu.espe.switchpayments.switchbatch.model.BatchStatusLog;

public interface BatchStatusLogRepository extends JpaRepository<BatchStatusLog, Long> {
}
