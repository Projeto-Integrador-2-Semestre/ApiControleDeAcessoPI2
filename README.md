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
| GET | `/dispositivos/estacoes` e `/dispositivos/estacoes/{id}` | Monitoramento: totem online/offline, IP, reconhecimento (face_api) e passagens de hoje |
| GET | `/dispositivos/estacoes/{id}/logs` | Syslog da estacao: passagens, liberacoes manuais e eventos do totem |
| POST | `/dispositivos/estacoes/{id}/ping` | Ultimo contato do totem e latencia da face_api |
| POST | `/dispositivos/estacoes/{id}/sincronizar-facial` | Pede para a face_api reler as fotos agora (502 se ela nao responder) |
| POST | `/dispositivos/estacoes/{id}/testar-giro` e `.../reiniciar` | 501: dependem do rele da catraca, que o totem ainda nao aciona |
| GET / POST | `/dispositivos/bloqueio-geral` | Estado / ativa (`{"ativo": true}`) ou desativa (`{"ativo": false}`) o bloqueio geral |

### Monitoramento de dispositivos

- **Totem online:** o tablet ja consulta `POST /catracas/{id}/liberacao/confirmar` a cada ~1,5 s; cada consulta conta como
  sinal de vida. Sem contato ha mais de 10 s, ele aparece offline. Fica em memoria (volta em 2 s depois de um reinicio da API).
- **Reconhecimento:** `GET /health` da face_api (`FACE_API_URL`): `reconhecendo`, `carregando`, `offline` ou `nao_configurado`.
- **statusGeral:** `operacional` (totem online e reconhecimento pronto), `sincronizando` (face_api carregando) ou `alerta`.
- **Bloqueio geral:** gravado na tabela `configuracao` (chave `bloqueio_geral`). Enquanto ativo, a face_api recusa todos os
  rostos (`motivo: "bloqueio_geral"`) e `POST /catracas/{id}/liberar` responde 409. O travamento fisico depende do rele.

```json
{"id": 1, "nome": "Catraca 01", "local": "Portaria Principal (Entrada A)", "statusGeral": "operacional",
 "totem": {"online": true, "ultimoContato": "2026-11-27T01:18:03Z", "segundosDesdeContato": 1, "ip": "177.10.20.30"},
 "reconhecimento": {"status": "reconhecendo", "rostosEmCache": 3, "antiSpoofing": true, "latenciaMs": 230},
 "passagensHoje": 12, "ultimaPassagem": "2026-11-27T01:10:00Z", "bloqueioGeral": false}
```

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
| 409 | CPF ja cadastrado, ou liberacao manual com o bloqueio geral ativo |
| 501 | Comando de estacao que ainda depende do rele (testar giro, reiniciar) |
| 502 | face_api nao respondeu ao pedido de sincronizacao |
| 413 | Foto maior que o limite de upload |
| 503 | Cloudflare D1 indisponivel |

## Testes

```bash
./mvnw test
```

Os testes nao acessam o D1: usam credenciais falsas (`src/test/resources/application.properties`) e mocks.

No GitHub, o workflow `.github/workflows/testes.yml` roda os testes em cada pull request e push para `main` e `develop`.

### Rodando os testes no Windows

Se o Maven falhar com `Unable to establish loopback connection`, rode com
`./mvnw test -DargLine="-Djdk.net.unixdomain.tmpdir=C:/tmp/uds"` (a pasta precisa existir; o problema e o
caminho da pasta temporaria do usuario).
