package com.AJTBackend.model;

import jakarta.persistence.*;
import com.AJTBackend.model.enums.Role;
import lombok.*;

import java.time.LocalDateTime;

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

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();
}