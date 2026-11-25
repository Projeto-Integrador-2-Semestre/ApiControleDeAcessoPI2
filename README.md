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

## Endpoints

| Metodo | Rota | Uso |
| :---: | :--- | :--- |
| GET | `/health` | Healthcheck |
| GET | `/pessoa/listar?foto=false` | Lista pessoas (`foto=false` nao envia a foto base64) |
| GET | `/pessoa/{cpf}` | Busca uma pessoa |
| POST | `/pessoa/cadastro` | Cadastro multipart (`cpf`, `nome`, `foto`); CPF so com digitos; 409 se ja existir |
| PUT | `/pessoa/atualizar` | Atualiza nome/foto |
| DELETE | `/pessoa/{cpf}` | Remove a pessoa |
| GET | `/logs?cpf=&tipo=entrada\|saida&de=&ate=&limite=` | Logs de entrada/saida (gravados pela face_api). `de`/`ate` em ISO 8601 com fuso |
| GET | `/logs/{id}` e `/logs/pessoa/{cpf}` | Log por id / logs de uma pessoa |
| GET | `/catracas` | Lista as catracas |
| POST | `/catracas` | Cadastra catraca (`{"nome": "...", "local": "..."}`) |
| POST | `/catracas/{id}/liberar` | Liberacao manual pedida pelo painel (`{"operador": "...", "motivo": "..."}`) |
| POST | `/catracas/{id}/liberacao/confirmar` | Consulta do tablet: 200 com a liberacao pendente ou 204 |
| GET | `/catracas/liberacoes` e `/catracas/liberacoes/{id}` | Historico / status de uma liberacao (`pendente`, `confirmada`, `expirada`) |

Erros voltam como JSON `{"detail": "mensagem"}`. Variaveis opcionais: `CORS_ALLOWED_ORIGINS` (padrao `*`) e
`LIBERACAO_VALIDADE_SEGUNDOS` (padrao 30).

### Rodando os testes no Windows

Se o Maven falhar com `Unable to establish loopback connection`, rode com
`./mvnw test -DargLine="-Djdk.net.unixdomain.tmpdir=C:/tmp/uds"` (a pasta precisa existir; o problema e o
caminho da pasta temporaria do usuario).
