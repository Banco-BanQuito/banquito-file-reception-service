package ec.edu.espe.Switch.Batch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ec.edu.espe.Switch.Batch.model.SwitchParameter;

public interface SwitchParameterRepository extends JpaRepository<SwitchParameter, String> {
}
