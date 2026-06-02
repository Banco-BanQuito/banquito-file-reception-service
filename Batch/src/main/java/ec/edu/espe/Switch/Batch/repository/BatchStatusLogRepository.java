package ec.edu.espe.Switch.Batch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ec.edu.espe.Switch.Batch.model.BatchStatusLog;

public interface BatchStatusLogRepository extends JpaRepository<BatchStatusLog, Long> {
}
