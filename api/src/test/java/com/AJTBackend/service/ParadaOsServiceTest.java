package com.AJTBackend.service;

import com.AJTBackend.config.UsuarioLogado;
import com.AJTBackend.dto.ParadaOsRequestDTO;
import com.AJTBackend.exception.TransferNaoEncontradoException;
import com.AJTBackend.model.OrdemServico;
import com.AJTBackend.model.ParadaOs;
import com.AJTBackend.model.Transfer;
import com.AJTBackend.model.enums.StatusParada;
import com.AJTBackend.repository.OrdemServicoRepository;
import com.AJTBackend.repository.ParadaOsRepository;
import com.AJTBackend.repository.TransferRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * o que testa: a regra que restringe o que o perfil MOTORISTA pode alterar
 * numa parada de OS (ParadaOsService) — so pode mudar o statusParada, nao
 * outros campos; GERENTE altera qualquer campo; e a busca em lote dos
 * transfers detectando id inexistente sem fazer uma query por transfer.
 *
 * como rodar: teste unitario com mocks (repositorios mockados; o perfil
 * logado e simulado no SecurityContextHolder). sem spring context, sem
 * banco, sem docker. roda com "mvn test".
 *
 * por que existe: o SecurityConfig libera o PATCH de paradas-os pro
 * MOTORISTA, mas so o service restringe quais campos ele pode mexer — sem
 * esse teste, um refactor podia abrir brecha pro motorista editar local,
 * horario ou transfers da parada, nao so o status dela.
 */
class ParadaOsServiceTest {

    private final ParadaOsRepository paradaOsRepository = mock(ParadaOsRepository.class);
    private final OrdemServicoRepository ordemServicoRepository = mock(OrdemServicoRepository.class);
    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final ParadaOsService service = new ParadaOsService(paradaOsRepository, ordemServicoRepository, transferRepository, new UsuarioLogado());

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void motoristaPodeAtualizarSoOStatusDaParada() {
        logadoComo("MOTORISTA");
        ParadaOs parada = parada();
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada));

        var resposta = service.atualizarParcial(1L, patch(null, StatusParada.CONCLUIDA, null));

        assertThat(resposta.statusParada()).isEqualTo(StatusParada.CONCLUIDA);
    }

    @Test
    void motoristaNaoPodeAlterarOutrosCamposDaParada() {
        logadoComo("MOTORISTA");

        assertThatThrownBy(() -> service.atualizarParcial(1L, patch("Outro local", StatusParada.CONCLUIDA, null)))
                .isInstanceOf(AccessDeniedException.class);
        verify(paradaOsRepository, never()).findById(anyLong());
    }

    @Test
    void gerentePodeAlterarQualquerCampo() {
        logadoComo("GERENTE");
        ParadaOs parada = parada();
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada));

        assertThat(service.atualizarParcial(1L, patch("Portao 9", null, null)).localParada()).isEqualTo("Portao 9");
    }

    @Test
    void buscaTransfersNumaUnicaConsultaEDetectaIdInexistente() {
        logadoComo("GERENTE");
        when(paradaOsRepository.findById(1L)).thenReturn(Optional.of(parada()));
        when(transferRepository.findAllById(any())).thenReturn(List.of(Transfer.builder().id(7L).build()));

        assertThatThrownBy(() -> service.atualizarParcial(1L, patch(null, null, Set.of(7L, 8L))))
                .isInstanceOf(TransferNaoEncontradoException.class)
                .hasMessageContaining("8");
        verify(transferRepository, never()).findById(anyLong());
    }

    private static ParadaOsRequestDTO patch(String local, StatusParada status, Set<Long> transferIds) {
        return new ParadaOsRequestDTO(null, null, local, null, null, null, null, status, transferIds);
    }

    private static ParadaOs parada() {
        OrdemServico os = OrdemServico.builder().id(3L).dataServico(LocalDate.now()).build();
        return ParadaOs.builder().id(1L).ordemServico(os).ordemParada(1).localParada("Portao 3").build();
    }

    private static void logadoComo(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
