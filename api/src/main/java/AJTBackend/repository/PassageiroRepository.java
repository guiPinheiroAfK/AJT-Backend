package AJTBackend.repository;

import AJTBackend.model.Passageiro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PassageiroRepository extends JpaRepository<Passageiro, Long> {

    List<Passageiro> findByNacionalidadeIgnoreCase(String nacionalidade);
}