package com.AJTBackend.web;

import com.AJTBackend.config.JsonAuthErrorHandler;
import com.AJTBackend.config.JwtService;
import com.AJTBackend.config.SecurityConfig;
import com.AJTBackend.repository.UsuarioRepository;
import com.AJTBackend.service.AuthService;
import com.AJTBackend.service.CotacaoService;
import com.AJTBackend.service.MotoristaService;
import com.AJTBackend.service.OrdemServicoService;
import com.AJTBackend.service.ParadaOsService;
import com.AJTBackend.service.PassageiroService;
import com.AJTBackend.service.PontoColetaService;
import com.AJTBackend.service.TransferService;
import com.AJTBackend.service.UsuarioService;
import com.AJTBackend.service.VeiculoService;
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

    protected static final String TRANSFER_VALIDO = """
            {"dataTransfer":"2026-09-20","horaTransfer":"14:30:00","origem":"Aeroporto GRU","destino":"Hotel"}
            """;
}
