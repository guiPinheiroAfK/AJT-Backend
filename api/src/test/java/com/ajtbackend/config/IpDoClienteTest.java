package com.ajtbackend.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * o que testa: de onde sai o IP usado no bloqueio de login — o X-Real-IP do nginx so vale
 * quando a conexao vem da rede interna (loopback ou faixa privada); de fora, vale o IP da conexao.
 *
 * como rodar: teste unitario com MockHttpServletRequest. sem spring, sem banco. "mvn test".
 *
 * por que existe: sem o cabecalho, atras do nginx todo mundo teria o mesmo IP (um atacante
 * bloquearia o login do admin pra todos); confiando nele sempre, qualquer um forjaria o IP
 * pra fugir do bloqueio.
 */
class IpDoClienteTest {

    @ParameterizedTest(name = "conexao {0}, X-Real-IP [{1}] -> {2}")
    @CsvSource({
            "127.0.0.1,       200.1.2.3,  200.1.2.3",   // nginx na propria EC2
            "0:0:0:0:0:0:0:1, 200.1.2.3,  200.1.2.3",   // loopback IPv6
            "172.18.0.4,      200.1.2.3,  200.1.2.3",   // nginx na rede do docker compose
            "10.0.0.7,        ' 200.1.2.3 ', 200.1.2.3", // rede interna, com espacos
            "192.168.0.10,    200.1.2.3,  200.1.2.3",
            "200.9.9.9,       200.1.2.3,  200.9.9.9",   // direto da internet: cabecalho forjado e ignorado
            "172.32.0.1,      200.1.2.3,  172.32.0.1",  // fora da faixa privada 172.16-31
            "127.0.0.1,       ,           127.0.0.1",   // sem cabecalho
            "127.0.0.1,       '   ',      127.0.0.1"    // cabecalho em branco
    })
    void deveUsarOCabecalhoSoDaRedeInterna_quandoDescobreOIpDoCliente(String conexao, String cabecalho, String esperado) {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(conexao);
        if (cabecalho != null) {
            request.addHeader(IpDoCliente.CABECALHO, cabecalho);
        }

        // when
        String ip = IpDoCliente.de(request);

        // then
        assertThat(ip).isEqualTo(esperado);
    }
}
