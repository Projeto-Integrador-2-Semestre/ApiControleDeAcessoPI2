# ControleDeAcesso

API Spring Boot do Projeto Integrador. Os dados ficam no **Cloudflare D1** (banco `projetointegrador`),
acessado pela API REST do Cloudflare.

## Configuracao do banco

1. Copie `.env.example` para `.env` na raiz do projeto.
2. Preencha `CLOUDFLARE_API_TOKEN` com o token da conta (o `.env` nao e versionado).
3. Suba a aplicacao com `./mvnw spring-boot:run`. Na inicializacao e feito um `SELECT 1` para validar a conexao.

As variaveis tambem podem ser definidas como variaveis de ambiente do sistema.
O schema das tabelas esta em `db/schema.sql`.

As tabelas `catraca` e `liberacao_catraca` sao criadas automaticamente na subida (com 4 catracas padrao
quando a tabela estiver vazia). Desligue com `cloudflare.d1.criar-tabelas=false`.

## Variaveis de ambiente

| Variavel | Padrao | O que faz |
| :--- | :--- | :--- |
| `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_D1_DATABASE_ID`, `CLOUDFLARE_API_TOKEN` | - | Acesso ao D1 |
| `ADMIN_USUARIO` / `ADMIN_SENHA` / `ADMIN_NOME` | `admin` / `1234` / `Administrador` | Operador do painel. **Troque a senha em producao** |
| `JWT_SEGREDO` | vazio | Chave do token (minimo 32 caracteres). Vazio = chave sorteada na subida: os logins caem quando a API reinicia |
| `JWT_VALIDADE_HORAS` | `8` | Validade do token |
| `SEGURANCA_HABILITADA` | `true` | `false` deixa tudo aberto (so para desenvolvimento) |
| `FACE_API_URL` | vazio | URL da face_api. Se definida, a API chama `POST /faces/reload` depois de cada cadastro, edicao, bloqueio ou exclusao, e a mudanca vale na hora no totem |
| `CORS_ALLOWED_ORIGINS` | `*` | Origens do front-end, separadas por virgula |
| `LIBERACAO_VALIDADE_SEGUNDOS` | `30` | Tempo que uma liberacao manual espera o tablet buscar |

Na subida a API cria as tabelas `catraca` e `liberacao_catraca` e adiciona, sem apagar dados, as colunas novas de
`pessoa` (`status`, `motivo_bloqueio`, `data_cadastro`) e de `logs` (`catraca_id`). Desligue com
`cloudflare.d1.criar-tabelas=false`.

## Autenticacao

O painel faz login em `POST /auth/login` e recebe um JWT. Todas as outras rotas exigem o header
`Authorization: Bearer <token>`, exceto:

- `GET /health`;
- `POST /auth/login`;
- `POST /catracas/{id}/liberacao/confirmar` (consulta do tablet: ele so recebe uma liberacao que o operador ja pediu logado).

Sem token, ou com token vencido, a resposta e `401 {"detail": "Faça login para continuar."}`.

```http
POST /auth/login
Content-Type: application/json

{"usuario": "admin", "senha": "1234"}
```

```json
{"token": "<token JWT>", "tipo": "Bearer", "expiraEm": "2026-11-27T08:00:00Z", "usuario": "admin", "nome": "Administrador"}
```

## Endpoints

| Metodo | Rota | Uso |
| :---: | :--- | :--- |
| GET | `/health` | Healthcheck (aberto) |
| POST | `/auth/login` | Login (aberto). 400 sem usuario/senha, 401 se errar |
| GET | `/auth/me` | Quem esta logado (serve para conferir se o token ainda vale) |
| GET | `/pessoa/listar?foto=false` | Lista pessoas (`foto=false` nao envia a foto base64) |
| GET | `/pessoa/{cpf}` | Busca uma pessoa (CPF com ou sem pontuacao). 404 se nao existir |
| POST | `/pessoa/cadastro` | Cadastro multipart (`cpf`, `nome`, `foto`). 400 dados invalidos, 409 CPF ja existe |
| PUT | `/pessoa/atualizar` | Atualiza nome e, se vier, a foto (multipart `cpf`, `nome`, `foto`). 400 sem nome, 404 CPF inexistente |
| PATCH | `/pessoa/{cpf}/status` | Bloqueia/libera: `{"status": "bloqueado", "motivo": "..."}` ou `{"status": "liberado"}`. Devolve a pessoa |
| DELETE | `/pessoa/{cpf}` | Remove a pessoa. 404 se nao existir |
| GET | `/logs?cpf=&tipo=entrada\|saida&de=&ate=&limite=` | Logs de entrada/saida (gravados pela face_api). `de`/`ate` em ISO 8601 com fuso |
| GET | `/logs/{id}` e `/logs/pessoa/{cpf}` | Log por id / logs de uma pessoa |
| GET | `/catracas` | Lista as catracas |
| POST | `/catracas` | Cadastra catraca (`{"nome": "...", "local": "..."}`) |
| POST | `/catracas/{id}/liberar` | Liberacao manual pedida pelo painel (`{"operador": "...", "motivo": "..."}`) |
| POST | `/catracas/{id}/liberacao/confirmar` | Consulta do tablet (aberto): 200 com a liberacao pendente ou 204 |
| GET | `/catracas/liberacoes` e `/catracas/liberacoes/{id}` | Historico / status de uma liberacao (`pendente`, `confirmada`, `expirada`) |

### Formatos de resposta

Pessoa na listagem (`/pessoa/listar?foto=false`):

```json
{"cpf": "47811936844", "nome": "Maria", "possuiFoto": true, "status": "bloqueado",
 "motivoBloqueio": "Matrícula trancada", "dataCadastro": "2026-11-26T15:30:00Z"}
```

Com `foto=true` (padrao) vem `foto` (base64) no lugar de `possuiFoto`. `dataCadastro` e nula para quem foi
cadastrado antes dessa coluna existir.

Log (`/logs`):

```json
{"id": 38, "cpf": "47811936844", "nome": "Maria", "tipo": "entrada", "dataHora": "2026-11-13T00:52:58.377Z",
 "catracaId": 1, "catracaNome": "Catraca 01", "catracaLocal": "Portaria Principal (Entrada A)"}
```

`catracaId`, `catracaNome` e `catracaLocal` ficam nulos quando o totem nao informou a catraca.

### Erros

Todos os erros, inclusive os do proprio Spring (JSON malformado, parametro invalido, rota inexistente), voltam como:

```json
{"detail": "Já existe uma pessoa cadastrada com este CPF."}
```

| Status | Quando |
| :---: | :--- |
| 400 | Dados invalidos (CPF sem 11 digitos, nome vazio, foto faltando, status invalido, JSON malformado) |
| 401 | Sem token, token invalido ou vencido, ou login errado |
| 404 | Pessoa, catraca ou rota nao encontrada |
| 409 | CPF ja cadastrado |
| 413 | Foto maior que o limite de upload |
| 503 | Cloudflare D1 indisponivel |

## Testes

```bash
./mvnw test
```

Os testes nao acessam o D1: usam credenciais falsas (`src/test/resources/application.properties`) e mocks.

### Rodando os testes no Windows

Se o Maven falhar com `Unable to establish loopback connection`, rode com
`./mvnw test -DargLine="-Djdk.net.unixdomain.tmpdir=C:/tmp/uds"` (a pasta precisa existir; o problema e o
caminho da pasta temporaria do usuario).
