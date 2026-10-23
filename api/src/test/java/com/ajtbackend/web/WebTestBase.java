package com.ajtbackend.web;

import com.ajtbackend.config.JsonAuthErrorHandler;
import com.ajtbackend.config.JwtService;
import com.ajtbackend.config.SecurityConfig;
import com.ajtbackend.repository.UsuarioRepository;
import com.ajtbackend.service.AuditoriaService;
import com.ajtbackend.service.AuthService;
import com.ajtbackend.service.CotacaoService;
import com.ajtbackend.service.MotoristaService;
import com.ajtbackend.service.OrdemServicoService;
import com.ajtbackend.service.PainelService;
import com.ajtbackend.service.ParadaOsService;
import com.ajtbackend.service.PassageiroService;
import com.ajtbackend.service.PontoColetaService;
import com.ajtbackend.service.RelatorioOrdemServicoService;
import com.ajtbackend.service.TransferService;
import com.ajtbackend.service.UsuarioService;
import com.ajtbackend.service.VeiculoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/*
 * classe base pros testes de camada web (AutorizacaoWebTest,
 * ValidacaoWebTest). sobe so os controllers + spring security + handler de
 * erros via @WebMvcTest, com todos os services mockados (@MockitoBean) —
 * nao precisa de banco nem de docker, roda em segundos com "mvn test".
 *
 * por que existe: separa "a permissao/validacao da rota esta certa" (testado
 * aqui, rapido) de "a regra de negocio por tras dela esta certa" (testado
 * nos *ServiceTest). extraida pra classe base pra nao repetir a lista de
 * @MockitoBean em cada classe de teste que usa os controllers.
 *
 * de proposito sem @WebMvcTest(controllers = ...): toda classe web usa esta
 * mesma configuracao, entao o spring monta o contexto uma vez e reaproveita
 * (cache de contexto). restringir por controller criaria um contexto novo
 * por classe e deixaria a suite mais lenta sem ganho de isolamento.
 */
@WebMvcTest(properties = "ajt.cors.allowed-origins=http://localhost:4200")
@Import({SecurityConfig.class, JsonAuthErrorHandler.class})
abstract class WebTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean protected JwtService jwtService;
    @MockitoBean protected UsuarioRepository usuarioRepository;
    @MockitoBean protected AuthService authService;
    @MockitoBean protected UsuarioService usuarioService;
    @MockitoBean protected MotoristaService motoristaService;
    @MockitoBean protected VeiculoService veiculoService;
    @MockitoBean protected PassageiroService passageiroService;
    @MockitoBean protected TransferService transferService;
    @MockitoBean protected PontoColetaService pontoColetaService;
    @MockitoBean protected OrdemServicoService ordemServicoService;
    @MockitoBean protected ParadaOsService paradaOsService;
    @MockitoBean protected CotacaoService cotacaoService;
    @MockitoBean protected AuditoriaService auditoriaService;
    @MockitoBean protected PainelService painelService;
    @MockitoBean protected RelatorioOrdemServicoService relatorioOrdemServicoService;

    protected static final String TRANSFER_VALIDO = """
            {"dataTransfer":"2026-09-20","horaTransfer":"14:30:00","origem":"Aeroporto GRU","destino":"Hotel"}
            """;
}
