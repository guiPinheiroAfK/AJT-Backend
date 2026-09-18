package com.AJTBackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * registro de auditoria (tabela logs_transfers, criada na V3 e ajustada na V7).
 * populada pela camada de servico (AuditoriaService), nao por trigger de banco.
 */
@Entity
@Table(name = "logs_transfers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tabela_afetada", length = 50)
    private String tabelaAfetada;

    @Column(name = "registro_id")
    private Long registroId;

    @Column(columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "data_hora")
    private LocalDateTime dataHora;
}
