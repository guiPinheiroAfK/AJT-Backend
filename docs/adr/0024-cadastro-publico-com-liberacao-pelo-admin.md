# ADR-0024: Cadastro público de usuários com liberação pelo administrador

- **Status:** Aceito
- **Data:** 2026-11-27

## Contexto

A entrega final pede "um fluxo para cadastro de novos usuários". Até aqui o único jeito de criar uma conta era o
admin, em `/api/usuarios` ([ADR-0002](0002-matriz-de-permissoes-por-perfil.md)): não havia nada que uma pessoa
nova pudesse fazer sozinha a partir da tela de login.

O sistema é interno de uma agência: quem entra vê passageiros com documento (LGPD,
[ADR-0003](0003-criptografia-documento-passageiro.md)), transfers e valores. Um "cadastre-se e já entre",
comum em aplicações abertas, daria esse acesso a qualquer pessoa na internet que achasse o IP da EC2.

## Decisão

- **Rota pública `POST /api/auth/registro`** com nome, username e senha (`RegistroRequestDTO`, mesmas regras de
  formato do cadastro pelo admin). O corpo **não aceita** perfil, `ativo` nem motorista: quem decide isso é o admin.
- A conta nasce **inativa** e com perfil **Atendente** (`UsuarioService.registrar`). A senha foi escolhida pela
  própria pessoa, então não obriga troca no primeiro acesso (diferente do cadastro pelo admin, que marca
  `trocarSenha`). A resposta é `201` com o usuário criado, **sem token**: o cadastro não abre sessão.
- **Liberação pelo admin**, sem tela nova: na lista de Usuários a conta aparece com o selo "Aguardando liberação"
  (inativa e sem nenhum login). O admin edita, liga "Usuário ativo" e, se for o caso, troca o perfil (Gerente,
  Motorista com o vínculo ao cadastro de motorista etc.).
- **O login não diferencia conta pendente.** Enquanto inativa, a pessoa recebe o mesmo 401 genérico de
  usuário/senha errados ([ADR-0004](0004-seguranca-de-login-e-sessao.md)). A tela "Criar conta" é que avisa, logo
  depois do cadastro, que o acesso depende da liberação.
- Username repetido dá **400** com a mesma mensagem do cadastro pelo admin.

## Alternativas consideradas

- **Conta já ativa no cadastro:** o fluxo de demonstração ficaria mais curto, mas qualquer pessoa ganharia acesso
  aos dados dos passageiros. Descartado.
- **Conta ativa sem perfil, ou com um perfil novo "Visitante" que não vê nada:** exigiria mexer no enum, na
  matriz do `SecurityConfig` e em todas as telas para tratar um perfil que não faz nada. A conta inativa resolve
  com o que já existe.
- **Login respondendo "conta aguardando liberação":** mais claro para a pessoa, mas deixa de ser um erro único
  para todos os casos de falha. Como a tela de cadastro já dá o aviso, preferimos manter o 401 genérico.
- **Confirmação por e-mail:** o sistema não guarda e-mail nem tem servidor de envio; ficaria fora do escopo.

## Consequências

- O fluxo completo na demonstração: Criar conta → tentar entrar (recusado) → admin libera em Usuários → entrar.
- A rota é pública e grava no banco. As contas ficam inativas e não dão acesso a nada, mas não há limite de
  cadastros por IP: se virar problema, dá para reaproveitar o contador do `LoginAttemptService`.
- O admin precisa olhar a lista de Usuários de vez em quando: não há notificação de conta nova.
- Uma conta criada pelo admin já inativa e que nunca entrou também aparece como "Aguardando liberação" na lista,
  o que é coerente: ela também espera alguém ligá-la.

## Onde encontrar no código

- Rota: `controller/AuthController.registrar`; liberada sem token em `config/SecurityConfig`
- Regra: `service/UsuarioService.registrar`; corpo: `dto/RegistroRequestDTO`
- Testes: `UsuarioServiceCrudTest`, `UsuarioErrosWebTest` (corpos inválidos e duplicado), `AutorizacaoWebTest` (rota
  pública) e `AutenticacaoIntegrationTest` (cadastra, login recusado, admin libera, login aceito)
- Front (`AJT-Frontend`): `features/auth/criar-conta/`, link no login e o selo em `features/usuarios/usuarios.component.ts`
