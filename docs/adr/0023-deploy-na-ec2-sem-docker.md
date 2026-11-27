# ADR-0023: Deploy na AWS EC2 sem Docker, com script de provisionamento

- **Status:** Aceito
- **Data:** 2026-11-24

## Contexto

A entrega final pede a aplicação inteira (Angular + Spring Boot + PostgreSQL) no ar numa EC2, montada por
um script `.sh` com o mínimo de intervenção manual. Também pede o back-end em segundo plano (de
preferência systemd), o build do Angular servido com fallback de rotas SPA, a porta 5432 fechada para fora,
nenhum `localhost` fixo no front e nenhuma credencial no Git. Na demonstração, uma conta AWS roda o script
ao vivo, e o ambiente precisa caber numa `t2/t3.micro` (1 GB de RAM).

O projeto já tinha um `docker-compose.yml` ([ADR-0009](0009-infra-perfis-container-e-rede.md)), mas ele
existe para o ambiente local: build em container numa máquina de 1 GB é lento e frágil, e o critério de
avaliação cita systemd explicitamente.

## Decisão

- **Uma máquina só, sem Docker.** O script [`deploy/provisionar-ec2.sh`](../../deploy/provisionar-ec2.sh)
  instala Java 17, Maven, Node.js 24, PostgreSQL, nginx e git pelo apt (Node pelo NodeSource, porque o
  `package-lock` do front exige npm 11) e monta tudo na mesma EC2 (Ubuntu 22.04/24.04).
- **Back-end como serviço do systemd** (`ajt-backend`), rodando com o usuário de sistema `ajt` (sem shell),
  escutando só em `127.0.0.1:8080`, com `Restart=on-failure` e `-XX:MaxRAMPercentage=50`.
- **nginx na porta 80** serve o `dist/` do Angular e repassa `/api/` para o back-end. Front e API ficam
  no mesmo endereço: o build de produção usa `apiUrl: '/api'` (`environment.prod.ts`), então não há
  `localhost` no código e o navegador nem faz requisição cross-origin. Rotas do Angular caem no
  `try_files $uri $uri/ /index.html` (fallback de SPA); js/css com hash ganham cache longo e o
  `index.html` vai com `no-cache`.
- **PostgreSQL só em localhost** (`listen_addresses = 'localhost'` num `conf.d` próprio). Junto com o
  Security Group (só 22 do IP de quem administra e 80 aberta), a 5432 e a 8080 ficam inacessíveis de fora.
  O `ufw` liga como segunda camada (`USAR_UFW=0` desliga).
- **Segredos gerados na própria máquina**, uma vez só, em `/etc/ajt/ajt.env` (dono root, `chmod 600`,
  lido pelo systemd via `EnvironmentFile`): senha do banco, segredo do JWT, chave AES dos documentos e a
  origem do CORS (o IP público, lido do serviço de metadados da EC2 com IMDSv2, ou `DOMINIO`). Nada disso
  passa pelo Git nem pela linha de comando.
- **Idempotente.** Rodar de novo atualiza o código (`BRANCH`, padrão `main`), refaz os builds e reinicia,
  **sem regenerar os segredos**: trocar a `AJT_CRYPTO_KEY` deixaria os documentos dos passageiros ilegíveis
  ([ADR-0003](0003-criptografia-documento-passageiro.md)).
- **Swap de 2 GB** quando a máquina tem menos de 3 GB de RAM, senão o `mvn package` e o `ng build` morrem
  por falta de memória.
- **O script confere o próprio resultado**: espera `/api/auth/me` responder 401 (API no ar e protegida) e
  `/login` responder 200 pelo nginx (fallback funcionando). Se falhar, mostra o `journalctl` do serviço.

### IP do cliente atrás do nginx

Com o nginx na frente, toda conexão chega ao Spring vinda de `127.0.0.1`, e o bloqueio de tentativas de
login ([ADR-0004](0004-seguranca-de-login-e-sessao.md)) passaria a bloquear todo mundo de uma vez. A
primeira tentativa foi `server.forward-headers-strategy`, mas ele também reescreve porta e protocolo da
requisição, e com o front em outra porta (docker compose) o Spring passou a recusar o login como
cross-origin. A solução final é `config/IpDoCliente`: usa o `X-Real-IP` que o nginx preenche, **só quando a
conexão vem de loopback ou de rede privada** (RFC 1918). Direto da internet o cabeçalho é ignorado, então
ninguém forja um IP para escapar do bloqueio.

## Alternativas consideradas

- **Docker Compose na EC2:** reaproveitaria o `docker-compose.yml`, mas o build das imagens numa máquina de
  1 GB é lento, o critério pede systemd e a consulta de logs fica um nível mais distante
  (`docker logs` em vez de `journalctl`). Continua sendo o caminho para o ambiente local.
- **Front em outra EC2 (ou S3):** obrigaria a expor a API para a internet, configurar CORS para outra
  origem e colocar a URL da API no build do front. Mais peças para dar errado numa demonstração ao vivo, sem
  ganho para o tamanho do projeto.
- **RDS para o banco:** mais seguro e com backup automático, mas sai do free tier e foge do "tudo provisionado
  pelo script".
- **Segredos no AWS Secrets Manager / SSM:** é o ideal num ambiente real, mas exige papel IAM na instância e
  mais configuração manual antes do script. O arquivo `600` do root resolve o requisito sem isso.
- **Baixar o JAR pronto de um release em vez de compilar na EC2:** deixaria o deploy mais rápido, mas
  exigiria um pipeline de release que o projeto não tem. Compilar na máquina mantém o script autossuficiente.

## Consequências

- Uma instância nova fica no ar com dois comandos (`curl` do script e `sudo bash`). A primeira execução numa
  `t2.micro` leva uns 15 minutos, quase tudo no build.
- Logs da demonstração: `journalctl -u ajt-backend -f` (back-end) e `/var/log/nginx/` (acessos e erros).
- Sem HTTPS: o site sai em `http://`. Com um domínio apontado para a EC2, dá para adicionar o certbot depois
  sem mudar o resto (a porta 443 já está liberada no `ufw` pelo perfil `Nginx Full`).
- O primeiro acesso é o admin do seed (`admin`/`admin123`), que é obrigado a trocar a senha no primeiro login.
- O script segue a `main` dos dois repositórios: o que não foi mergeado na `main` não vai para a EC2 (ou
  rode com `BRANCH=<outra>`).
- Uma máquina só significa um ponto único de falha; para o escopo do projeto isso é aceitável.

## Onde encontrar no código

- Script de provisionamento: `deploy/provisionar-ec2.sh` (os passos são numerados nos comentários)
- IP real atrás do proxy: `api/src/main/java/com/ajtbackend/config/IpDoCliente.java` e o teste
  `IpDoClienteTest`
- URL da API no front: `src/environments/environment.prod.ts` (repositório `AJT-Frontend`)
- Passo a passo, Security Group (console e AWS CLI) e comandos de log: `README.md`, seção "Deploy na AWS (EC2)"
