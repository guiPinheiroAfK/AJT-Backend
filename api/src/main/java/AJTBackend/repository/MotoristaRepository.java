package AJTBackend.repository;

import AJTBackend.model.Motorista;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MotoristaRepository extends JpaRepository<Motorista, Long> {

    Optional<Motorista> findByCnh(String cnh);

    boolean existsByCnh(String cnh);
}