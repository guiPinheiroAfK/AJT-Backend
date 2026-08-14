package AJTBackend.service;

import AJTBackend.dto.VeiculoRequestDTO;
import AJTBackend.dto.VeiculoResponseDTO;
import AJTBackend.exception.PlacaJaCadastradaException;
import AJTBackend.exception.VeiculoNaoEncontradoException;
import AJTBackend.model.Veiculo;
import AJTBackend.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VeiculoService {

    private final VeiculoRepository veiculoRepository;

    public List<VeiculoResponseDTO> listarTodos() {
        return veiculoRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public VeiculoResponseDTO buscarPorId(Long id) {
        Veiculo veiculo = veiculoRepository.findById(id)
                .orElseThrow(() -> new VeiculoNaoEncontradoException(id));
        return toResponseDTO(veiculo);
    }

    public VeiculoResponseDTO buscarPorPlaca(String placa) {
        Veiculo veiculo = veiculoRepository.findByPlaca(placa)
                .orElseThrow(() -> new VeiculoNaoEncontradoException(0L));
        return toResponseDTO(veiculo);
    }

    public VeiculoResponseDTO criar(VeiculoRequestDTO dto) {
        if (veiculoRepository.existsByPlaca(dto.placa())) {
            throw new PlacaJaCadastradaException(dto.placa());
        }

        Veiculo veiculo = Veiculo.builder()
                .label(dto.label())
                .placa(dto.placa())
                .capacidade(dto.capacidade())
                .tipo(dto.tipo())
                .marca(dto.marca())
                .build();

        return toResponseDTO(veiculoRepository.save(veiculo));
    }

    public VeiculoResponseDTO atualizar(Long id, VeiculoRequestDTO dto) {
        Veiculo veiculo = veiculoRepository.findById(id)
                .orElseThrow(() -> new VeiculoNaoEncontradoException(id));

        veiculo.setLabel(dto.label());
        veiculo.setPlaca(dto.placa());
        veiculo.setCapacidade(dto.capacidade());
        veiculo.setTipo(dto.tipo());
        veiculo.setMarca(dto.marca());

        return toResponseDTO(veiculoRepository.save(veiculo));
    }

    public VeiculoResponseDTO atualizarParcial(Long id, VeiculoRequestDTO dto) {
        Veiculo veiculo = veiculoRepository.findById(id)
                .orElseThrow(() -> new VeiculoNaoEncontradoException(id));

        if (dto.label() != null) {
            veiculo.setLabel(dto.label());
        }
        if (dto.placa() != null) {
            if (!dto.placa().equals(veiculo.getPlaca()) && veiculoRepository.existsByPlaca(dto.placa())) {
                throw new PlacaJaCadastradaException(dto.placa());
            }
            veiculo.setPlaca(dto.placa());
        }
        if (dto.capacidade() != null) {
            veiculo.setCapacidade(dto.capacidade());
        }
        if (dto.tipo() != null) {
            veiculo.setTipo(dto.tipo());
        }
        if (dto.marca() != null) {
            veiculo.setMarca(dto.marca());
        }

        return toResponseDTO(veiculoRepository.save(veiculo));
    }

    public void deletar(Long id) {
        if (!veiculoRepository.existsById(id)) {
            throw new VeiculoNaoEncontradoException(id);
        }
        veiculoRepository.deleteById(id);
    }

    private VeiculoResponseDTO toResponseDTO(Veiculo veiculo) {
        return new VeiculoResponseDTO(
                veiculo.getId(),
                veiculo.getLabel(),
                veiculo.getPlaca(),
                veiculo.getCapacidade(),
                veiculo.getTipo(),
                veiculo.getMarca()
        );
    }
}