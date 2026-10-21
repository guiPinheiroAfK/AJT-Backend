package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.model.Motorista;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.Usuario;
import com.AJTBackend.model.enums.Role;
import com.AJTBackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * regra da HU07: o MOTORISTA so enxerga as proprias ordens de servico (e as paradas delas).
 * "proprias" = OS cujo motorista e o cadastro ligado ao usuario dele (usuarios.motorista_id, V11).
 * motorista sem cadastro ligado nao enxerga nenhuma OS.
 * os outros perfis nao tem restricao: aqui tudo devolve "pode".
 */
@Component
@RequiredArgsConstructor
public class AcessoMotorista {

    private final UsuarioLogado usuarioLogado;
    private final UsuarioRepository usuarioRepository;

    /** true quando o usuario logado e MOTORISTA, ou seja, ve so o que e dele. */
    public boolean restrito() {
        return usuarioLogado.temPerfil(Role.MOTORISTA);
    }

    /** id do cadastro de motorista ligado ao usuario logado (vazio se nao houver vinculo). */
    public Optional<Long> motoristaId() {
        return usuarioRepository.findByUsername(usuarioLogado.usernameOuSistema())
                .map(Usuario::getMotorista)
                .map(Motorista::getId);
    }

    /** a OS e visivel pro usuario logado? (sempre, pra quem nao e motorista) */
    public boolean podeVer(OrdemServico ordemServico) {
        if (!restrito()) {
            return true;
        }
        Long meu = motoristaId().orElse(null);
        return meu != null && ordemServico.getMotorista() != null && meu.equals(ordemServico.getMotorista().getId());
    }
}
