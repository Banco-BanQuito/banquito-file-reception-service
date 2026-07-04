package ec.edu.espe.switchpayments.switchbatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ec.edu.espe.switchpayments.switchbatch.model.SwitchParameter;

public interface SwitchParameterRepository extends JpaRepository<SwitchParameter, String> {
}
