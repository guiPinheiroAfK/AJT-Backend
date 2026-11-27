package com.ajtbackend.model;

import com.ajtbackend.model.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OptimisticLock;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // lock otimista (RNF008, V9): o UPDATE so passa se ninguem salvou o registro antes
    @Version
    private Long versao;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String senha; // hash BCrypt, nunca em texto plano

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    // obriga o usuario a definir uma nova senha (seed do admin / senha criada por outro usuario)
    @Column(name = "trocar_senha", nullable = false)
    @Builder.Default
    private Boolean trocarSenha = false;

    // tokens emitidos antes deste instante deixam de valer
    @Column(name = "senha_alterada_em")
    private LocalDateTime senhaAlteradaEm;

    // gravado a cada login: nao conta como edicao do cadastro (senao o admin que estivesse
    // editando o usuario levaria 409 so porque a pessoa entrou no sistema)
    @OptimisticLock(excluded = true)
    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    // HU07: cadastro de motorista deste usuario (so perfil MOTORISTA); define quais OS ele ve (V11)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motorista_id", unique = true)
    private Motorista motorista;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now(ZoneId.systemDefault());
}