# Resultados de benchmark

Registro de medições: rotina de snapshot (inicialização), endpoints de download e getAll.

## Modelo com muitos campos

A partir desta versão, cada item possui **16 campos** (além do id), para simular payload grande e comparar melhor Protobuf vs JSON vs SQLite:

- `valueA`, `valueB`, `label`, `latitude`, `longitude`
- `altitude`, `createdAt`, `updatedAt`, `description` (até ~1000 chars), `code` (~20–50 chars)
- `category`, `status`, `count`, `score`, `metadata`

O script `gerar_itens_aleatorios.py` preenche todos esses campos com dados aleatórios. **Recomendação:** resetar o banco, popular de novo e reiniciar o app antes de rodar os testes abaixo.

## Auditoria da comparação

Durante a auditoria foi identificado um ponto importante: parte dos benchmarks antigos misturava **camadas diferentes**.

- Em alguns cenários, Protobuf/SQLite eram medidos com custo de geração completo (DB + serialização) no request.
- JSON em `GET /api/items` também era gerado no request, mas com dinâmica diferente.
- Isso pode fazer parecer que “JSON está mais rápido” em certos testes, mesmo com payload bem maior.

Para comparação justa, os testes foram separados em:

1. **Download puro** (`/api/snapshot/download/*`) com arquivos pré-gerados no MinIO.  
2. **Geração no servidor** (`/api/benchmark/generate/*`) medindo `dbLoadMs` e `serializeMs`.  
3. **Deserialização isolada** (script Python, sem rede).  
4. **Tempo até mapa** em dois modos: `full` e `map`.

## Rotina de snapshot (inicialização do app)

| Data       | Itens | Total (ms) | DB (ms) | Protobuf (ms) | SQLite (ms) | Upload .bin (ms) | Upload .sqlite (ms) | Tamanho .bin | Tamanho .sqlite |
|------------|-------|------------|---------|---------------|-------------|------------------|---------------------|--------------|-----------------|
| 2026-03-03 (antes) | 20000 | 252937 | 148 | 32 | 252609 | 23 | 57 | 800108 (781 KB) | 835584 (816 KB) |
| 2026-03-03 (após batch) | 20000 | 456 | 156 | 31 | 69 | 68 | 62 | 800108 (781 KB) | 835584 (816 KB) |
| 2026-03-03 | 520000 | 2846 | 1482 | 200 | 446 | 345 | 290 | 21207623 (20,7 MB) | 21942272 (21,4 MB) |
| 2026-03-03 | 500000 | 3171 | 1759 | 247 | 499 | 314 | 285 | 29387054 (28,0 MB) | 30240768 (28,8 MB) |

**Ambiente:** Java 17, Spring Boot 3.3.4, PostgreSQL 16, MinIO, Pop 22.04.

### Por que ficou mais rápido?

A segunda medição (após otimização) caiu de **~253 s** para **~0,45 s** no total. O ganho veio quase todo do SQLite:

- **Antes:** um `INSERT` por linha (20.000 `executeUpdate()`). Cada um era um round-trip para o SQLite e, com a conexão em modo padrão, cada statement era tratado como uma transação separada — ou seja, muitos commits e muito I/O em disco.
- **Depois:** (1) **Transação única** — um único `commit()` no final, reduzindo I/O; (2) **Batch insert** — `addBatch()` nas linhas e `executeBatch()` a cada 2000 linhas, reduzindo chamadas ao driver/SQLite; (3) **PRAGMAs** — `synchronous=OFF` durante a carga (menos flush por escrita) e `synchronous=FULL` só depois do commit (para o arquivo ficar consistente ao ser lido).

Com isso, o tempo do passo SQLite passou de **252609 ms** para **69 ms** (redução de ~99,97%). O tempo total da rotina de snapshot ficou dominado por DB + Protobuf + SQLite + uploads, todos na casa de dezenas de milissegundos.

---

## Template para próximas medições

Copie o bloco abaixo e preencha após cada execução.

```
### Snapshot – YYYY-MM-DD HH:MM

- **Itens:** 
- **Total:**  ms
- **DB:**  ms | **Protobuf:**  ms | **SQLite:**  ms
- **Upload:** .bin  ms | .sqlite  ms
- **Tamanhos:** .bin  bytes | .sqlite  bytes
- **Observações:** (ex.: após otimização batch SQLite)
```

---

## Endpoints (GET /api/snapshot/download/bin | /sqlite | /items)

| Data       | Endpoint | Runs | Tempo médio (ms) | min–max (ms) | Banda (bytes) | X-Processing-Ms (médio) |
|------------|----------|------|------------------|--------------|---------------|--------------------------|
| 2026-03-03 | Protobuf (.bin) | 3 | 86,59 | 65,40–118,25 | 800108 (781 KB) | 67,33 |
| 2026-03-03 | SQLite (.sqlite) | 3 | 65,39 | 59,19–71,14 | 835584 (816 KB) | 61,67 |
| 2026-03-03 | JSON (getAll) | 3 | 66,70 | 55,97–85,31 | 1546639 (1,47 MB) | 39,33 |

*Primeiro teste de endpoints; 20k itens no banco. Comando: `python3 ./scripts/benchmark_endpoints.py`*

**520k itens, 10 runs** (`--runs 10`):

| Data       | Endpoint | Runs | Tempo médio (ms) | min–max (ms) | Banda (bytes) | X-Processing-Ms (médio) |
|------------|----------|------|------------------|--------------|---------------|--------------------------|
| 2026-03-03 | Protobuf (.bin) | 10 | 1333,88 | 1186,12–1647,25 | 21207623 (20,23 MB) | 1296,30 |
| 2026-03-03 | SQLite (.sqlite) | 10 | 1439,76 | 1383,52–1478,02 | 21942272 (20,93 MB) | 1406,90 |
| 2026-03-03 | JSON (getAll) | 10 | 1442,76 | 1362,26–1492,59 | 40902127 (39,01 MB) | 1086,70 |

**500k itens, 10 runs** (`--runs 10`):

| Data       | Endpoint | Runs | Tempo médio (ms) | min–max (ms) | Banda (bytes) | X-Processing-Ms (médio) |
|------------|----------|------|------------------|--------------|---------------|--------------------------|
| 2026-03-03 | Protobuf (.bin) | 10 | 1508,07 | 1235,23–1755,76 | 29387054 (28,03 MB) | 1462,20 |
| 2026-03-03 | SQLite (.sqlite) | 10 | 1554,08 | 1491,75–1628,66 | 30240768 (28,84 MB) | 1512,00 |
| 2026-03-03 | JSON (getAll) | 10 | 1709,52 | 1600,89–1788,44 | 62708951 (59,80 MB) | 1134,70 |

---

## O que vamos medir: leitura para mapa

Cenário: front/mobile baixa o snapshot (Protobuf, SQLite ou JSON), deserializa e precisa da lista de pontos `(latitude, longitude)` pronta para inserir no mapa. O que medimos é o **tempo total** desde o request até essa lista pronta (GET + deserialização + extração dos pares lat/long), com `--runs N` para obter média, min e max.

- **Modo full:** GET `/api/snapshot/download/bin|sqlite|json` → parse completo → extração de `(lat,long)`.
- **Modo map:** GET `/api/snapshot/download/map-bin|map-json` (+ sqlite com `SELECT latitude, longitude`) → lista de pontos.
- **Observação:** `GET /api/items` fica como endpoint dinâmico de referência, não como benchmark de download puro.

Comando: `python3 ./scripts/benchmark_map_leitura.py [--url BASE_URL] [--runs N]`.

---

## Leitura para mapa (tempo até dados prontos)

| Data | Formato | Runs | Tempo médio (ms) | min–max (ms) | Tamanho (bytes) | Pontos |
|------|---------|------|------------------|--------------|-----------------|--------|
| 2026-03-03 | Protobuf (.bin) | 10 | 1557,07 | 1504,07–1598,70 | 29387054 (28,03 MB) | 500000 |
| 2026-03-03 | SQLite (.sqlite) | 10 | 1724,85 | 1674,93–1778,84 | 30240768 (28,84 MB) | 500000 |
| 2026-03-03 | JSON (getAll) | 10 | 2191,37 | 2075,56–2341,42 | 62708951 (59,80 MB) | 500000 |

Em leitura “até o mapa”, o Protobuf entrega a lista de 500k pontos mais rápido e com o menor payload, seguido de perto pelo SQLite; o JSON fica bem atrás, principalmente porque envia aproximadamente o dobro de bytes dos outros formatos. Nos endpoints de download, Protobuf e SQLite têm tempos de servidor parecidos, mas novamente com vantagem clara sobre o JSON em termos de banda, reforçando que formatos mais compactos ajudam tanto no backend quanto na experiência do cliente.

*Preencher após rodar: `python3 ./scripts/benchmark_map_leitura.py --runs N`.*

---

## O que vamos fazer: testes com muitos campos e escala

1. **Testes com muitos campos (payload grande)**  
   Com o modelo expandido (16 campos por item), rodar de novo: snapshot na subida do app, `benchmark_endpoints.py --runs N` e `benchmark_map_leitura.py --runs N`. Anotar tamanhos e tempos nas tabelas abaixo. Esperado: JSON cresce bem mais que Protobuf/SQLite (repetição de nomes de chaves).

2. **Escala variável (N itens)**  
   Para cada N ∈ {50.000, 100.000, 200.000, 500.000, 1.000.000}: (1) resetar o banco; (2) `python scripts/gerar_itens_aleatorios.py N`; (3) reiniciar o app; (4) rodar `benchmark_endpoints.py --runs 5` e anotar tamanho (bytes) e tempo médio (ms) por formato. Tabela abaixo pronta para preencher.

3. **Deserialização isolada**  
   Medir só o tempo de parse (sem rede): um GET por formato, depois N deserializações em memória. Mostra que Protobuf não só é menor como é mais barato de interpretar. Comando: `python3 ./scripts/benchmark_deserializacao.py [--url BASE_URL] [--runs 10]`.

4. **Adendo: compressão (gzip)**  
   Existe a possibilidade de enviar as respostas com `Content-Encoding: gzip`. Os resultados com compressão podem ser anotados na seção ao final; assim comparamos banda e tempo com/sem compressão.

---

## Testes com muitos campos (payload grande)

*Após resetar o banco, popular com N itens (ex.: 500.000) e reiniciar o app.*

### Snapshot (inicialização) – muitos campos

| Data | Itens | Total (ms) | DB (ms) | Protobuf (ms) | SQLite (ms) | Upload .bin (ms) | Upload .sqlite (ms) | Tamanho .bin | Tamanho .sqlite |
|------|-------|------------|---------|---------------|-------------|------------------|---------------------|--------------|-----------------|
| 2026-03-03 | 50000 | 1842 | 447 | 217 | 264 | 463 | 378 | 26631072 (26,0 MB) | 28434432 (28,4 MB) |
| 2026-03-03 | 100000 | 3351 | 793 | 448 | 458 | 801 | 774 | 53301044 (52,3 MB) | 56918016 (55,6 MB) |
| 2026-03-03 | 200000 | 6294 | 1466 | 777 | 830 | 1563 | 1586 | 106605073 (101,7 MB) | 113807360 (108,5 MB) |
| 2026-03-03 | 500000 | 12798 | 3376 | 1662 | 1680 | 3105 | 2905 | 266564039 (254,2 MB) | 284540928 (271,4 MB) |
| 2026-03-03 | 1000000 | 23980 | 6645 | 3109 | 3175 | 5398 | 5582 | 533089843 (508,4 MB) | 569057280 (542,7 MB) |

### Endpoints – muitos campos (50k itens, 5 runs)

| Data | Endpoint | Runs | Tempo médio (ms) | min–max (ms) | Banda (bytes) | X-Processing-Ms (médio) |
|------|----------|------|------------------|--------------|---------------|--------------------------|
| 2026-03-03 | Protobuf (.bin) | 5 | 522,49 | 485,75–593,65 | 26631072 (25,40 MB) | 472,00 |
| 2026-03-03 | SQLite (.sqlite) | 5 | 466,85 | 455,09–483,52 | 28434432 (27,12 MB) | 426,80 |
| 2026-03-03 | JSON (getAll) | 5 | 448,72 | 436,46–475,14 | 35735312 (34,08 MB) | 260,80 |

**100k itens, 5 runs:**

| Data | Endpoint | Runs | Tempo médio (ms) | min–max (ms) | Banda (bytes) | X-Processing-Ms (médio) |
|------|----------|------|------------------|--------------|---------------|--------------------------|
| 2026-03-03 | Protobuf (.bin) | 5 | 998,85 | 899,07–1051,97 | 53301044 (50,83 MB) | 910,40 |
| 2026-03-03 | SQLite (.sqlite) | 5 | 905,83 | 900,32–911,28 | 56918016 (54,28 MB) | 825,00 |
| 2026-03-03 | JSON (getAll) | 5 | 898,48 | 864,11–925,01 | 71505225 (68,19 MB) | 518,80 |

**200k itens, 5 runs:**

| Data | Endpoint | Runs | Tempo médio (ms) | min–max (ms) | Banda (bytes) | X-Processing-Ms (médio) |
|------|----------|------|------------------|--------------|---------------|--------------------------|
| 2026-03-03 | Protobuf (.bin) | 5 | 1978,10 | 1881,40–2016,50 | 106605073 (101,67 MB) | 1824,00 |
| 2026-03-03 | SQLite (.sqlite) | 5 | 1834,48 | 1764,82–1874,43 | 113807360 (108,54 MB) | 1679,80 |
| 2026-03-03 | JSON (getAll) | 5 | 1776,76 | 1711,29–1850,87 | 143104057 (136,47 MB) | 1019,60 |

**500k itens, 5 runs:**

| Data | Endpoint | Runs | Tempo médio (ms) | min–max (ms) | Banda (bytes) | X-Processing-Ms (médio) |
|------|----------|------|------------------|--------------|---------------|--------------------------|
| 2026-03-03 | Protobuf (.bin) | 5 | 4806,43 | 4427,35–4997,10 | 266564039 (254,22 MB) | 4447,20 |
| 2026-03-03 | SQLite (.sqlite) | 5 | 4475,64 | 4351,80–4608,45 | 284540928 (271,36 MB) | 4105,20 |
| 2026-03-03 | JSON (getAll) | 5 | 4475,83 | 4335,07–4626,69 | 357950235 (341,37 MB) | 2702,40 |

### Leitura para mapa – muitos campos

| Data | Formato | Runs | Tempo médio (ms) | min–max (ms) | Tamanho (bytes) | Pontos |
|------|---------|------|------------------|--------------|-----------------|--------|
| 2026-03-03 (100k) | Protobuf (.bin) | 5 | 1027,75 | 988,24–1067,32 | 53301044 (50,83 MB) | 100000 |
| 2026-03-03 (100k) | SQLite (.sqlite) | 5 | 922,30 | 896,68–957,67 | 56918016 (54,28 MB) | 100000 |
| 2026-03-03 (100k) | JSON (getAll) | 5 | 1134,26 | 1113,48–1154,60 | 71505225 (68,19 MB) | 100000 |
| 2026-03-03 (200k) | Protobuf (.bin) | 5 | 2062,76 | 1970,05–2145,16 | 106605073 (101,67 MB) | 200000 |
| 2026-03-03 (200k) | SQLite (.sqlite) | 5 | 1846,29 | 1816,72–1883,79 | 113807360 (108,54 MB) | 200000 |
| 2026-03-03 (200k) | JSON (getAll) | 5 | 2359,63 | 2283,75–2400,29 | 143104057 (136,47 MB) | 200000 |
| 2026-03-03 (500k) | Protobuf (.bin) | 5 | 5138,78 | 5051,01–5184,24 | 266564039 (254,22 MB) | 500000 |
| 2026-03-03 (500k) | SQLite (.sqlite) | 5 | 4794,48 | 4684,35–4957,54 | 284540928 (271,36 MB) | 500000 |
| 2026-03-03 (500k) | JSON (getAll) | 5 | 5888,97 | 5802,77–6016,28 | 357950235 (341,37 MB) | 500000 |
| 2026-03-03 (1M) | Protobuf (.bin) | 5 | 10235,40 | 9950,10–10378,35 | 533089843 (508,39 MB) | 1000000 |
| 2026-03-03 (1M) | SQLite (.sqlite) | 5 | 9356,25 | 9208,02–9573,72 | 569057280 (542,70 MB) | 1000000 |
| 2026-03-03 (1M) | JSON (getAll) | 5 | 11442,85 | 11298,90–11609,92 | 715942278 (682,78 MB) | 1000000 |

**Análise (50k itens, muitos campos):** Com 16 campos por item, o JSON já ocupa **34,08 MB** (35735312 bytes) contra **25,40 MB** do Protobuf e **27,12 MB** do SQLite — ou seja, o JSON fica ~34% maior que o Protobuf e ~26% maior que o SQLite. A repetição dos nomes das chaves em cada registro infla bem o payload. No tempo de download (cliente), os três ficaram na faixa de 450–520 ms para 50k itens; no servidor (X-Processing-Ms), o JSON teve menor tempo (260 ms) por fazer só serialização em memória, enquanto bin/sqlite incluem leitura do MinIO.

---

## Escala variável (N itens)

Para cada N: resetar banco → `gerar_itens_aleatorios.py N` → reiniciar app → `benchmark_endpoints.py --runs 5` → anotar.

| N (itens) | Formato | Tamanho (bytes) | Tempo médio (ms) |
|-----------|---------|-----------------|-------------------|
| 50.000 | Protobuf (.bin) | 26631072 (25,40 MB) | 522,49 |
| 50.000 | SQLite (.sqlite) | 28434432 (27,12 MB) | 466,85 |
| 50.000 | JSON (getAll) | 35735312 (34,08 MB) | 448,72 |
| 100.000 | Protobuf (.bin) | 53301044 (50,83 MB) | 998,85 |
| 100.000 | SQLite (.sqlite) | 56918016 (54,28 MB) | 905,83 |
| 100.000 | JSON (getAll) | 71505225 (68,19 MB) | 898,48 |
| 200.000 | Protobuf (.bin) | 106605073 (101,67 MB) | 1978,10 |
| 200.000 | SQLite (.sqlite) | 113807360 (108,54 MB) | 1834,48 |
| 200.000 | JSON (getAll) | 143104057 (136,47 MB) | 1776,76 |
| 500.000 | Protobuf (.bin) | 266564039 (254,22 MB) | 4806,43 |
| 500.000 | SQLite (.sqlite) | 284540928 (271,36 MB) | 4475,64 |
| 500.000 | JSON (getAll) | 357950235 (341,37 MB) | 4475,83 |
| 1.000.000 | Protobuf (.bin) | 533089843 (508,39 MB) | 9621,80 |
| 1.000.000 | SQLite (.sqlite) | 569057280 (542,70 MB) | 8702,95 |
| 1.000.000 | JSON (getAll) | 715942278 (682,78 MB) | 8849,10 |

**Análise (preencher):** *Aqui podemos mostrar como tamanho e tempo escalam com N e que a vantagem do Protobuf se mantém (ou aumenta) com o volume.*

---

## Deserialização isolada (só parse, sem rede)

Comando: `python3 ./scripts/benchmark_deserializacao.py [--url BASE_URL] [--runs 10]`.  
Baixa uma vez cada formato; em seguida mede apenas o tempo de deserializar + extrair lista de (lat, long), sem novo request.

| Data | Formato | Runs | Payload (bytes) | Tempo médio por parse (ms) | min–max (ms) |
|------|---------|------|-----------------|----------------------------|--------------|
| 2026-03-03 | Protobuf (.bin) | 5 | 26631072 | 77,16 | 70,10–87,83 |
| 2026-03-03 | SQLite (.sqlite) | 5 | 28434432 | 36,79 | 34,89–39,87 |
| 2026-03-03 | JSON (getAll) | 5 | 35735312 | 151,22 | 145,96–155,61 |

*100k itens, 5 runs:*

| Data | Formato | Runs | Payload (bytes) | Tempo médio por parse (ms) | min–max (ms) |
|------|---------|------|-----------------|----------------------------|--------------|
| 2026-03-03 | Protobuf (.bin) | 5 | 53301044 | 152,57 | 141,79–175,12 |
| 2026-03-03 | SQLite (.sqlite) | 5 | 56918016 | 72,32 | 69,72–79,34 |
| 2026-03-03 | JSON (getAll) | 5 | 71505225 | 320,17 | 312,08–327,27 |

*200k itens, 5 runs:*

| Data | Formato | Runs | Payload (bytes) | Tempo médio por parse (ms) | min–max (ms) |
|------|---------|------|-----------------|----------------------------|--------------|
| 2026-03-03 | Protobuf (.bin) | 5 | 106605073 | 302,59 | 286,97–343,04 |
| 2026-03-03 | SQLite (.sqlite) | 5 | 113807360 | 140,17 | 135,71–149,17 |
| 2026-03-03 | JSON (getAll) | 5 | 143104057 | 616,51 | 585,84–673,38 |

*500k itens, 5 runs:*

| Data | Formato | Runs | Payload (bytes) | Tempo médio por parse (ms) | min–max (ms) |
|------|---------|------|-----------------|----------------------------|--------------|
| 2026-03-03 | Protobuf (.bin) | 5 | 266564039 | 760,11 | 719,95–878,69 |
| 2026-03-03 | SQLite (.sqlite) | 5 | 284540928 | 394,28 | 364,94–428,77 |
| 2026-03-03 | JSON (getAll) | 5 | 357950235 | 1524,13 | 1475,35–1576,65 |

**Análise (50k itens, muitos campos):** O tempo **puro de parse** (sem rede) deixa claro a diferença: **JSON ~151 ms** por deserialização, **Protobuf ~77 ms** e **SQLite ~37 ms**. O SQLite ganha no parse porque, após escrever o blob em arquivo temporário, o `SELECT latitude, longitude` é muito leve; o Protobuf fica no meio (parse binário eficiente); o JSON paga o custo de interpretar 50k objetos com muitas chaves e strings. Ou seja, Protobuf é cerca de 2× mais rápido que JSON no parse e usa ~25% menos bytes; o SQLite, neste teste isolado de “extrair pontos”, é o mais rápido porque a operação é basicamente leitura sequencial de duas colunas.

**100k itens:** Parse isolado ~152 ms (Protobuf), ~72 ms (SQLite), ~320 ms (JSON). O JSON continua ~2× mais lento que o Protobuf no parse; o SQLite segue mais rápido na extração dos pontos.

**200k itens:** Parse isolado ~303 ms (Protobuf), ~140 ms (SQLite), ~617 ms (JSON). A proporção mantém-se: JSON ~2× Protobuf; SQLite continua o mais rápido na extração dos pontos.

**500k itens:** Parse isolado ~760 ms (Protobuf), ~394 ms (SQLite), ~1524 ms (JSON). JSON continua ~2× mais lento que Protobuf no parse; em leitura para mapa, 500k pontos ficam prontos em ~5,1 s (Protobuf), ~4,8 s (SQLite) e ~5,9 s (JSON).

**1M itens:** Parse isolado ~1485 ms (Protobuf), ~829 ms (SQLite), ~3009 ms (JSON). Em leitura para mapa, 1M de pontos ficam prontos em ~10,2 s (Protobuf), ~9,4 s (SQLite) e ~11,4 s (JSON). A diferença entre JSON e Protobuf se mantém (JSON ~2× mais lento no parse, payload bem maior), e o SQLite continua o mais rápido na extração dos pontos brutos.

---

## Critérios de decisão arquitetural (consolidados)

- **First sync (dispositivo virgem):** usar snapshot **SQLite** completo (`snapshot.sqlite`) para attach local rápido e sem custo de UPSERT em massa.
- **Delta sync (atualizações):** usar **Protobuf map/delta** (`snapshot-map.bin` ou payload incremental equivalente), reduzindo banda e tempo de parse.
- **JSON:** manter para compatibilidade/inspeção humana e debug, não como formato principal para grande volume.
- **Benchmark oficial para comparação de transporte:** usar somente endpoints de **download puro** (`/api/snapshot/download/*`), com payload pré-gerado no MinIO.
- **Benchmark de geração:** medir separadamente em `/api/benchmark/generate/*` (`dbLoadMs` + `serializeMs`) para não contaminar conclusões de transporte.

---

## O que mudou de antes para hoje (metodologia)

Nas medições iniciais, parte dos números comparava formatos com **custos misturados** no mesmo endpoint (ex.: leitura do banco + serialização + resposta HTTP). Isso gerava viés metodológico: em alguns cenários, o tempo observado refletia mais a implementação do endpoint do que o formato de serialização em si.

A partir de 2026-03-04, a metodologia foi ajustada para comparação **justa por camada**:

1. **Download puro**: arquivos já prontos no MinIO (`/api/snapshot/download/*`), isolando rede + transferência.
2. **Geração no servidor**: benchmark separado de `DB load + serialização` (`/api/benchmark/generate/*`), sem confundir com latência de download.
3. **Parse isolado**: deserialização sem rede.
4. **Tempo até mapa**: request + parse + lista de pontos pronta.
5. **Dois perfis de payload**:
   - **full** (16 campos por item),
   - **map** (payload enxuto para mapa: `id`, `latitude`, `longitude`, `updatedAt`).

Com essa separação, os resultados passaram a refletir melhor o comportamento real de cada formato em cada etapa. Assim, a análise deixa de ser “qual formato é sempre mais rápido” e passa a ser “qual formato é melhor para cada etapa da arquitetura”.

---

## Resultado consolidado (arquitetura separada por camada) - 2026-03-04

Dataset: **1.000.000 itens** (16 campos no snapshot full).  
Camadas medidas: geração de snapshot, download puro, parse isolado e tempo até pontos no mapa.

### 1) Rotina de snapshot na inicialização (full)

| Data | Itens | Total (ms) | DB (ms) | Protobuf (ms) | SQLite (ms) | Upload .bin (ms) | Upload .sqlite (ms) | Tamanho .bin | Tamanho .sqlite | Tamanho .json | Tamanho map.bin | Tamanho map.json |
|------|-------|------------|---------|---------------|-------------|------------------|---------------------|--------------|-----------------|---------------|------------------|------------------|
| 2026-03-04 | 1.000.000 | 33978 | 6955 | 3061 | 3026 | 4737 | 4889 | 533278439 (508,57 MB) | 569335808 (542,96 MB) | 716132530 (682,96 MB) | 30983529 (29,55 MB) | 86666952 (82,65 MB) |

### 2) Download puro (`benchmark_endpoints.py --runs 5`)

**Modo full**

| Formato | Tempo médio (ms) | min-max (ms) | Banda (bytes) | X-Processing-Ms (médio) |
|---------|------------------|--------------|---------------|--------------------------|
| Protobuf full (.bin) | 930,83 | 909,48-965,95 | 533278439 (508,57 MB) | 275,80 |
| SQLite full (.sqlite) | 1041,46 | 997,53-1075,46 | 569335808 (542,96 MB) | 329,00 |
| JSON full (.json) | 1285,35 | 1239,82-1340,69 | 716132530 (682,96 MB) | 402,60 |

**Modo map**

| Formato | Tempo médio (ms) | min-max (ms) | Banda (bytes) | X-Processing-Ms (médio) |
|---------|------------------|--------------|---------------|--------------------------|
| Protobuf map (.bin) | 76,85 | 66,45-116,57 | 30983529 (29,55 MB) | 27,00 |
| SQLite map (.sqlite) | 66,20 | 60,39-70,77 | 33374208 (31,83 MB) | 24,20 |
| JSON map (.json) | 155,87 | 152,27-159,34 | 86666952 (82,65 MB) | 46,40 |

### 3) Parse isolado (`benchmark_deserializacao.py --runs 5`)

**Modo full**

| Formato | Payload (bytes) | Tempo médio parse (ms) | min-max (ms) |
|---------|------------------|------------------------|--------------|
| Protobuf full (.bin) | 533278439 | 1429,55 | 1369,11-1646,29 |
| SQLite full (.sqlite) | 569335808 | 808,21 | 756,72-843,66 |
| JSON full (.json) | 716132530 | 2920,22 | 2832,20-3012,30 |

**Modo map**

| Formato | Payload (bytes) | Tempo médio parse (ms) | min-max (ms) |
|---------|------------------|------------------------|--------------|
| Protobuf map (.bin) | 30983529 | 363,52 | 357,99-371,23 |
| SQLite map (.sqlite) | 33374208 | 362,39 | 348,17-394,26 |
| JSON map (.json) | 86666952 | 547,13 | 498,33-593,79 |

### 4) Tempo até dados prontos para mapa (`benchmark_map_leitura.py --runs 5`)

**Modo full**

| Formato | Tempo médio (ms) | min-max (ms) | Tamanho (bytes) | Pontos |
|---------|------------------|--------------|-----------------|--------|
| Protobuf full (.bin) | 2295,24 | 2190,11-2454,40 | 533278439 (508,57 MB) | 1000000 |
| SQLite full (.sqlite) | 1676,97 | 1531,01-1775,22 | 569335808 (542,96 MB) | 1000000 |
| JSON full (.json) | 4147,87 | 4014,25-4343,37 | 716132530 (682,96 MB) | 1000000 |

**Modo map**

| Formato | Tempo médio (ms) | min-max (ms) | Tamanho (bytes) | Pontos |
|---------|------------------|--------------|-----------------|--------|
| Protobuf map (.bin) | 381,26 | 363,74-424,28 | 30983529 (29,55 MB) | 1000000 |
| SQLite map (.sqlite) | 396,87 | 383,28-422,63 | 33374208 (31,83 MB) | 1000000 |
| JSON map (.json) | 653,39 | 623,10-693,33 | 86666952 (82,65 MB) | 1000000 |

### Análise técnica (por que os resultados ficaram assim)

- **Download puro full:** Protobuf vence porque transfere menos bytes que SQLite e muito menos que JSON; com payload já pronto no MinIO, o custo de geração não contamina a comparação.
- **Download/parse map (comparação justa):** com `snapshot-map.sqlite`, Protobuf map (29,55 MB) e SQLite map (31,83 MB) ficam muito próximos em banda e latência de download; ambos ficam bem à frente do JSON map (82,65 MB).
- **Parse map:** Protobuf map e SQLite map empataram na prática (~363 ms), mostrando que o SQLite deixa de ser “penalizado” quando também recebe payload enxuto.
- **JSON full:** além de maior payload, paga parse de texto e alocação de objetos; por isso piora bastante no tempo até dados prontos.
- **Conclusão prática:** para **first sync** grande, SQLite segue excelente; para **delta/mapa**, Protobuf map e SQLite map formam as duas melhores opções técnicas (com vantagem de menor banda para Protobuf e leve vantagem de download para SQLite nesta rodada).

---

## Adendo: compressão (gzip)

Existe a possibilidade de servir as respostas com compressão (ex.: `Content-Encoding: gzip`). Isso reduz a banda na rede; o cliente descomprime antes de deserializar. Abaixo, espaço para anotar resultados **com compressão** (ex.: mesmo N e mesmo número de runs), para comparação com as medições sem compressão.

| Data | Formato | Com compressão? | Runs | Banda (bytes) | Tempo médio (ms) | Observação |
|------|---------|-----------------|------|---------------|------------------|------------|
| (preencher) | Protobuf (.bin) | sim | | | | |
| (preencher) | SQLite (.sqlite) | sim | | | | |
| (preencher) | JSON (getAll) | sim | | | | |

**Análise (preencher):** *JSON comprime bem (muitas repetições de chaves), mas o payload comprimido ainda costuma ser maior (ou comparável) ao Protobuf sem compressão. Descreva aqui os resultados obtidos.*

---

## Notas

- **SQLite na rotina de snapshot:** A primeira medição (252609 ms para 20k itens) usou INSERT linha a linha. Foi otimizado com: transação única (`setAutoCommit(false)`), **batch insert** (`addBatch()` + `executeBatch()` a cada 2000 linhas), `PRAGMA synchronous=OFF` durante a carga e `PRAGMA synchronous=FULL` antes do commit. Vale rodar de novo e anotar o novo tempo.
- **PostgreSQL → .db direto?** O PostgreSQL não gera arquivo SQLite (.db) nativo. Alternativas seriam: (1) dump em CSV/custom e depois importar no SQLite (dois passos, ferramenta externa); (2) ferramentas como pgloader (Postgres → SQLite). Gerar o SQLite na aplicação com batch é a opção mais simples e, otimizada, tende a ser rápida o suficiente; o gargala era o INSERT por linha.
