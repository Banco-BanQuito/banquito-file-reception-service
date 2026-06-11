package ec.edu.espe.switchbatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ec.edu.espe.switchbatch.model.BatchStatusLog;

public interface BatchStatusLogRepository extends JpaRepository<BatchStatusLog, Long> {
}
