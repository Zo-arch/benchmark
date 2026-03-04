# Benchmark App

Projeto Java básico para testes e artigo técnico. Dados genéricos, sem regra de negócio.

## Estrutura

- `src/main/java/com/benchmark/app/` – aplicação Spring Boot
- `src/main/java/com/benchmark/app/model/` – entidade genérica (Item)
- `src/main/java/com/benchmark/app/dto/` – DTOs para JSON
- `src/main/proto/` – definições Protobuf (sync.proto)
- Código gerado do proto em `target/generated-sources/protobuf/java`
- `scripts/` – script Python para popular o banco com itens aleatórios

## Build

```bash
mvn clean compile
```

Gera as classes Java a partir dos `.proto` na pasta `src/main/proto`.

## Run

```bash
mvn spring-boot:run
```

App sobe na porta 8081. Endpoint: `GET /api/health`.

## Banco de dados e dados de teste

Com PostgreSQL no ar (ex.: `docker compose up db -d`) e `.env` configurado, a aplicação sobe normalmente: o Hibernate cria a tabela `item` automaticamente se ela não existir (`ddl-auto=update`). Com a tabela vazia, o snapshot na inicialização gera arquivos com 0 itens.

Para popular dados e testar com volume:

```bash
pip install -r scripts/requirements.txt
python scripts/gerar_itens_aleatorios.py 1000
```

O script lê `SPRING_DATASOURCE_*` do `.env` e usa `CREATE TABLE IF NOT EXISTS item` (compatível com a entidade JPA). Depois, reinicie o app para gerar snapshots com os dados.

## Snapshot na inicialização

Ao subir a aplicação (`mvn spring-boot:run`), é executado um snapshot dos dados:

1. Lê todos os itens do PostgreSQL.
2. Gera snapshots **full**: Protobuf (`snapshot.bin`), SQLite (`snapshot.sqlite`) e JSON (`snapshot.json`).
3. Gera snapshots **map** (payload reduzido): Protobuf (`snapshot-map.bin`), JSON (`snapshot-map.json`) e SQLite (`snapshot-map.sqlite`).
4. Cria o bucket no MinIO se não existir e envia os arquivos com nomes fixos (sempre sobrescreve a última versão).

É necessário ter PostgreSQL e MinIO no ar e configurar no `.env`: `SPRING_DATASOURCE_*`, `MINIO_URL`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MINIO_BUCKET`.

## Endpoints para medição (benchmark)

Endpoints de **download puro** (arquivo pré-gerado no MinIO):

| Endpoint | Descrição |
|----------|-----------|
| `GET /api/snapshot/download/bin` | Download do snapshot em Protobuf (`.bin`, attachment `snapshot.bin`) |
| `GET /api/snapshot/download/sqlite` | Download do snapshot em SQLite (`.sqlite`, attachment `snapshot.sqlite`) |
| `GET /api/snapshot/download/json` | Download do snapshot full em JSON (`.json`, attachment `snapshot.json`) |
| `GET /api/snapshot/download/map-bin` | Download do snapshot reduzido de mapa em Protobuf |
| `GET /api/snapshot/download/map-json` | Download do snapshot reduzido de mapa em JSON |
| `GET /api/snapshot/download/map-sqlite` | Download do snapshot reduzido de mapa em SQLite (`.sqlite`, attachment `snapshot-map.sqlite`) |

Endpoint de JSON dinâmico (útil para comparação, não para transporte puro):

| Endpoint | Descrição |
|----------|-----------|
| `GET /api/items` | Lista todos os itens em JSON montando payload em tempo real |

Endpoints de benchmark de geração no servidor (retornam métricas, não payload gigante):

| Endpoint | Descrição |
|----------|-----------|
| `GET /api/benchmark/generate/proto-full` | DB + serialização Protobuf full |
| `GET /api/benchmark/generate/sqlite-full` | DB + serialização SQLite full |
| `GET /api/benchmark/generate/json-full` | DB + serialização JSON full |
| `GET /api/benchmark/generate/proto-map` | DB + serialização Protobuf map |
| `GET /api/benchmark/generate/json-map` | DB + serialização JSON map |

Cada resposta inclui o header **`X-Processing-Ms`** com o tempo de processamento no servidor (ms), para benchmark.

### Benchmark de download puro (Python)

Mede tempo de transação (cliente), banda (tamanho da resposta) e processamento (servidor, via header):

```bash
pip install -r scripts/requirements.txt
python scripts/benchmark_endpoints.py [--url http://localhost:8081] [--runs 3] [--csv resultado.csv]
```

### Benchmark da geração no servidor

Separa DB load e serialização por formato (sem transferir payload no body):

```bash
python scripts/benchmark_geracao.py [--url http://localhost:8081] [--runs 5] [--mode full]
python scripts/benchmark_geracao.py [--url http://localhost:8081] [--runs 5] [--mode map]
```

### Benchmark de leitura para mapa

Mede o tempo desde o request até a lista de `(latitude, longitude)` pronta para uso no mapa (GET + deserialização + extração):

```bash
pip install -r scripts/requirements.txt
python scripts/benchmark_map_leitura.py [--url http://localhost:8081] [--runs 3] [--mode full]
python scripts/benchmark_map_leitura.py [--url http://localhost:8081] [--runs 3] [--mode map]
```

O código Python do proto está em `scripts/benchmark/sync_pb2.py`. Para regenerar a partir do `.proto`: `protoc -I src/main/proto --python_out=scripts src/main/proto/sync.proto` (requer `protobuf-compiler` instalado).

### Benchmark de deserialização isolada

Mede apenas o tempo de parse (sem rede): um GET por formato, depois N deserializações em memória. Útil para comparar custo de interpretação Protobuf vs JSON vs SQLite.

```bash
python scripts/benchmark_deserializacao.py [--url http://localhost:8081] [--runs 10] [--mode full]
python scripts/benchmark_deserializacao.py [--url http://localhost:8081] [--runs 10] [--mode map]
```

### Testes adicionais (resultados-benchmark.md)

Em `resultados-benchmark.md` estão descritos e com tabelas pré-prontas: **testes com muitos campos** (modelo com 16 campos por item), **escala variável** (50k a 1M itens), **deserialização isolada** e **adendo com compressão (gzip)**. O script `gerar_itens_aleatorios.py` já gera todos os campos; após resetar o banco e popular de novo, reinicie o app e rode os scripts de benchmark para preencher as tabelas.
