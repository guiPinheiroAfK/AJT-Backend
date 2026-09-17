# ADR-0001: Enums em vez de strings livres para status e perfil

- **Status:** Aceito
- **Data:** 2026-09-16

## Contexto

Campos como `role` do usuário, `status` do transfer, `status` da ordem de serviço,
`statusParada` e `acao` da parada eram `String` livre, tanto na entidade JPA quanto
no DTO. Isso permitia gravar qualquer valor no banco (inclusive erro de digitação
tipo `"CONFIRMDO"`), e a única validação existia em alguns DTOs via `@Pattern`,
não em todos.

## Decisão

Cada um desses campos virou um `enum` Java, mapeado com `@Enumerated(EnumType.STRING)`
na entidade (grava o nome do enum como texto no banco, não o índice numérico — isso
importa porque o índice mudaria de significado se a ordem do enum fosse alterada).
O Jackson (serialização JSON) valida automaticamente contra os valores do enum: um
valor fora da lista já dá erro antes de chegar no service.

## Alternativas consideradas

- **Manter `String` + `@Pattern` de regex em todo DTO:** foi o que já existia
  parcialmente. Descartado porque duplica a lista de valores válidos em cada DTO
  (fácil ficar desatualizado) e não impede erro de digitação em código que grava
  direto na entidade sem passar pelo DTO.
- **Tabela de domínio no banco (`status_transfer` com FK):** mais "correto"
  relacionalmente, mas over-engineering pro tamanho do projeto — os valores não
  mudam com frequência a ponto de justificar uma tabela e um join a mais em toda
  consulta.
- **Enum com `@Enumerated(EnumType.ORDINAL)` (grava número):** descartado porque
  qualquer reordenação futura do enum corrompe dados já gravados silenciosamente.
  `STRING` é menos compacto no banco, mas é seguro.

## Consequências

- Valor inválido em qualquer perfil/status agora dá `400` com a lista dos valores
  aceitos na mensagem de erro (ver `GlobalExceptionHandler`), em vez de gravar lixo
  no banco ou dar erro genérico.
- Adicionar um novo status exige alterar o enum Java (recompilar), não é mais
  "gravar uma string nova e pronto" — isso é proposital: força passar pelo código
  e pelos testes.
- Índices que usam esses campos (ver ADR-0009) continuam funcionando normalmente,
  porque o valor gravado no banco é texto (`'CONFIRMADO'`), igual antes.

## Onde encontrar no código

- Enums: `api/src/main/java/com/AJTBackend/model/enums/` (`Role`, `StatusTransfer`,
  `StatusOrdemServico`, `StatusParada`, `AcaoParada`)
- Uso nas entidades: `model/Usuario.java`, `model/Transfer.java`,
  `model/OrdemServico.java`, `model/ParadaOs.java` (campo anotado com `@Enumerated`)
- Tratamento do erro de valor inválido: `exception/GlobalExceptionHandler.java`,
  método `handleJsonInvalido`
- Teste que cobre a mensagem de erro: `api/src/test/java/com/AJTBackend/web/ValidacaoWebTest.java`,
  método `enumInvalidoListaValoresAceitos`
