# ADR-0003: Criptografia do documento do passageiro (AES-256-GCM)

- **Status:** Aceito
- **Data:** 2026-09-16

## Contexto

A coluna `documento` da tabela `passageiros` já tinha um comentário na migration
original (`V1__Create_Base.sql`) dizendo "AES-256-GCM (cifrado na camada de
serviço)" — mas isso nunca foi implementado. O campo era salvo e lido em texto
puro pelo `PassageiroService`. Documento de identificação (CPF, RG, passaporte) é
dado sensível: expor isso em um vazamento de banco é um problema de LGPD.

## Decisão

Criar um `AttributeConverter` do JPA (`CriptografiaConverter`) que cifra o valor
com AES-256-GCM antes de gravar e decifra ao ler, de forma transparente pra
qualquer código que use a entidade `Passageiro` — o service não sabe que o campo
é cifrado, só vê a `String` normal.

Formato gravado no banco: `"v1:" + Base64(IV de 12 bytes + texto cifrado + tag)`.
O prefixo `v1:` serve pra versionar o formato (se um dia trocar o algoritmo) e
pra distinguir de valores legados gravados antes desta ADR, que ficam em texto
puro sem esse prefixo — esses continuam sendo lidos normalmente (sem quebrar
dados existentes) e passam a ser cifrados automaticamente na próxima vez que o
registro for salvo.

A chave (`AJT_CRYPTO_KEY`, Base64 de 32 bytes) é obrigatória: sem ela, a
aplicação recusa subir — ver ADR-0009 sobre variáveis de ambiente obrigatórias.

## Alternativas consideradas

- **Criptografar em nível de banco (`pgcrypto` do PostgreSQL):** move a
  responsabilidade pro banco, mas a chave também precisaria estar acessível ao
  banco, e passa a exigir uma extensão específica de PostgreSQL — menos portável,
  e mistura lógica de segurança de aplicação com a camada de infraestrutura.
- **Cifrar no DTO/controller em vez de na entidade:** descartado porque criaria
  múltiplos pontos onde alguém poderia esquecer de cifrar (qualquer novo endpoint
  que manipule `Passageiro` diretamente). O `AttributeConverter` garante que é
  impossível gravar sem passar pela cifra — está amarrado ao mapeamento JPA, não
  a uma chamada que pode ser esquecida.
- **IV fixo por registro (não aleatório):** descartado porque permitiria comparar
  se dois registros têm o mesmo documento sem decifrar (vazamento de informação
  por padrão repetido). O IV é gerado aleatoriamente a cada gravação.

## Consequências

- Um `SELECT documento FROM passageiros` direto no banco mostra só o texto
  cifrado — útil pra confirmar que a cifra está funcionando, mas também significa
  que qualquer ferramenta de admin de banco (pgAdmin, DBeaver) não mostra o dado
  em claro, o que é o objetivo.
- Trocar a chave `AJT_CRYPTO_KEY` depois de já existir dado cifrado **quebra a
  leitura** desses registros (a decifragem falha com `IllegalStateException`).
  Não existe rotação de chave implementada — se for necessário no futuro, precisa
  de uma migração de dados (decifrar com a chave antiga, recifrar com a nova).
- Nenhuma mudança de contrato de API: o endpoint continua devolvendo o documento
  legível, porque a decifragem acontece na leitura da entidade, antes de virar
  DTO de resposta.
- Custo de CPU desprezível (AES-GCM é rápido), mas cada leitura/gravação de
  passageiro agora passa por essa camada extra — não é um problema de performance
  observado, só um trade-off que existe.

## Onde encontrar no código

- Converter: `api/src/main/java/com/AJTBackend/config/CriptografiaConverter.java`
- Uso na entidade: `model/Passageiro.java`, campo `documento` com
  `@Convert(converter = CriptografiaConverter.class)`
- Configuração da chave: `application.yml`, propriedade `ajt.crypto.key`, e
  `.env.example` com instrução de como gerar (`openssl rand -base64 32`)
- Testes: `api/src/test/java/com/AJTBackend/config/CriptografiaConverterTest.java`
  (ida e volta, chave errada, dado adulterado, valor legado sem prefixo) e
  `api/src/test/java/com/AJTBackend/integracao/PersistenciaIntegrationTest.java`,
  método `documentoDoPassageiroFicaCifradoNoBancoMasVoltaLegivelNaApi` (prova
  com banco real que o valor no banco é diferente do valor na API)
