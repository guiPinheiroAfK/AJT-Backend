AJT Backend — Sistema Receptivo (AJT Viagens e Turismo)
API REST para gestão de receptivo turístico da AJT Viagens e Turismo (antiga SOS Viale): cadastros de passageiros, motoristas, veículos e pontos de coleta, agendamento de transfers, geração de ordens de serviço, controle de acesso por perfil e relatórios.

Este backend é a evolução do sistema desktop original (SOSViale---Sistema-Receptivo, Java + Swing) para uma arquitetura web, com o front-end em repositório separado (Angular + Tailwind, a criar).


Stack
Camada
Tecnologia
Backend
Java 17 + Spring Boot (Web, Security, Data JPA)
Frontend
Angular + Tailwind CSS (repositório separado)
Banco de dados
PostgreSQL
Migrações
Flyway
Autenticação
JWT
Build
Maven
Containerização
Docker / Docker Compose



Sobre a migração
O projeto original era uma aplicação desktop (Java Swing, arquitetura View → Service → Repository, persistência via Hibernate/JPA e modo offline com snapshot local). Nesta reescrita:

A camada View (Swing) é substituída pelo frontend Angular, que passa a consumir a API via HTTP.
A camada Service dá lugar aos services do Spring, expostos por controllers REST (@RestController).
O modelo de dados (passageiros, motoristas, veículos, pontos de coleta, transfers, ordens de serviço, usuários/perfis) é mantido como base e evolui via migrações Flyway.
Autenticação segue com JWT; perfis de usuário (ADMIN, GERENTE, MOTORISTA) continuam controlando o acesso a funcionalidades, agora via Spring Security.
Funcionalidades específicas de desktop (modo offline com snapshot local) não fazem parte do escopo inicial da versão web.


Estrutura do repositório
ajt-backend/

├── src/main/java/br/com/ajt/

│   ├── auth/            # autenticação e JWT

│   ├── config/           # configurações (segurança, CORS, banco)

│   ├── controller/       # controllers REST por domínio

│   ├── dto/               # objetos de entrada/saída da API

│   ├── model/             # entidades JPA

│   ├── repository/        # repositórios Spring Data

│   └── service/           # regras de negócio

├── src/main/resources/

│   ├── application.yml

│   └── db/migration/      # migrações Flyway

├── docker-compose.yml      # PostgreSQL para desenvolvimento

├── .env.example

└── pom.xml

Estrutura de referência — ajuste conforme o código for evoluindo.


Requisitos
Ferramenta
Versão
JDK
17+
Maven
3.8+
PostgreSQL
15+ (via Docker Compose)
Docker
opcional, recomendado



Configuração rápida
1. Variáveis de ambiente
cp .env.example .env

Defina pelo menos:

AJT_JWT_SECRET — segredo usado para assinar os tokens JWT.
AJT_DB_URL, AJT_DB_USER, AJT_DB_PASSWORD — credenciais do PostgreSQL.
2. Banco de dados
docker compose up -d

As migrações Flyway rodam automaticamente na subida da aplicação.
3. Executar a aplicação
mvn spring-boot:run

A API sobe por padrão em http://localhost:8080.


Domínio principal
Usuários e perfis — ADMIN, GERENTE, MOTORISTA, com controle de acesso por perfil.
Passageiros — cadastro com documento (CPF, RG, CNH, Passaporte) e nacionalidade.
Motoristas e veículos — frota disponível para atendimento.
Pontos de coleta — locais de embarque/desembarque.
Transfers — agendamento de deslocamentos, com origem, destino, horário e valor.
Ordens de serviço (OS) — agrupam transfers de um motorista/veículo em um dia, com paradas.


Repositórios relacionados
Frontend (Angular + Tailwind): AJT-Frontend (link a definir)
Sistema legado (Java Swing): SOSViale---Sistema-Receptivo


Status
🚧 Em desenvolvimento — reescrita do sistema receptivo original para arquitetura web.
