#!/usr/bin/env python3
"""
Script para gerar e inserir itens aleatórios no banco de dados.
Gera registros compatíveis com ItemEntity: value_a, value_b, label, latitude, longitude,
altitude, created_at, updated_at, description, code, category, status, count, score, metadata.
Coordenadas aleatórias dentro de Minas Gerais (bbox) para simular árvores no mapa.
"""

import os
import random
import sys
import time
from pathlib import Path

def carregar_env():
    """Carrega variáveis de ambiente do arquivo .env se existir."""
    script_dir = Path(__file__).parent
    env_file = script_dir.parent / '.env'

    if env_file.exists():
        with open(env_file, 'r') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    if line.startswith('SPRING_DATASOURCE'):
                        key, value = line.split('=', 1)
                        key = key.strip()
                        value = value.strip().strip('"').strip("'")
                        os.environ[key] = value

carregar_env()

DB_CONFIG = {
    'host': os.getenv('SPRING_DATASOURCE_HOST', 'localhost'),
    'port': int(os.getenv('SPRING_DATASOURCE_PORT', '5432')),
    'database': os.getenv('SPRING_DATASOURCE_DATABASE', 'benchmark'),
    'user': os.getenv('SPRING_DATASOURCE_USERNAME', 'benchmark'),
    'password': os.getenv('SPRING_DATASOURCE_PASSWORD', 'benchmark')
}

# Bounding box Minas Gerais (lat/long) para simular árvores no mapa
MG_LAT_MIN = -22.9
MG_LAT_MAX = -14.2
MG_LONG_MIN = -51.0
MG_LONG_MAX = -39.8

# Prefixos e sufixos para labels variadas
LABEL_PREFIXOS = ['item', 'registro', 'entrada', 'dado', 'linha', 'row', 'record']
LABEL_SUFIXOS = ['alfa', 'beta', 'gamma', 'x', 'y', 'z', 'a', 'b', 'c', '01', '02', 'teste', 'sample']

# Valores para campos categóricos (inflação de payload no JSON)
CATEGORIAS = ['árvore', 'planta', 'semente', 'muda', 'fruto', 'folha', 'raiz', 'outro']
STATUS_LIST = ['ativo', 'pendente', 'inativo', 'rascunho', 'publicado']

# Palavras para montar description e code
DESC_PALAVRAS = ['Lorem', 'ipsum', 'dolor', 'sit', 'amet', 'consectetur', 'adipiscing', 'elit', 'sed', 'do', 'eiusmod', 'tempor', 'incididunt', 'ut', 'labore', 'et', 'dolore', 'magna', 'aliqua', 'Ut', 'enim', 'ad', 'minim', 'veniam', 'quis', 'nostrud', 'exercitation', 'ullamco', 'laboris', 'nisi', 'aliquip', 'ex', 'ea', 'commodo', 'consequat']


def conectar_banco():
    """Conecta ao banco de dados PostgreSQL."""
    try:
        import psycopg2
        conn = psycopg2.connect(**DB_CONFIG)
        return conn
    except ImportError:
        print("Erro: instale o pacote psycopg2 (pip install psycopg2-binary)")
        sys.exit(1)
    except Exception as e:
        if 'does not exist' in str(e):
            print(f"Erro: Banco de dados '{DB_CONFIG['database']}' não existe")
            print(f"Host: {DB_CONFIG['host']}:{DB_CONFIG['port']}")
        else:
            print(f"Erro ao conectar ao banco de dados: {e}")
        sys.exit(1)


def criar_tabela_se_nao_existir(conn):
    """Cria a tabela item se não existir (espelho do ItemEntity com todos os campos)."""
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS item (
            id BIGSERIAL PRIMARY KEY,
            value_a DOUBLE PRECISION NOT NULL,
            value_b DOUBLE PRECISION NOT NULL,
            label VARCHAR(500) NOT NULL,
            latitude DOUBLE PRECISION,
            longitude DOUBLE PRECISION,
            altitude DOUBLE PRECISION,
            created_at BIGINT,
            updated_at BIGINT,
            description VARCHAR(1000),
            code VARCHAR(100),
            category VARCHAR(100),
            status VARCHAR(50),
            count INTEGER,
            score DOUBLE PRECISION,
            metadata VARCHAR(2000)
        )
    """)
    conn.commit()
    # Garantir colunas em bancos já existentes (idempotente)
    for col, typ in [
        ("latitude", "DOUBLE PRECISION"),
        ("longitude", "DOUBLE PRECISION"),
        ("altitude", "DOUBLE PRECISION"),
        ("created_at", "BIGINT"),
        ("updated_at", "BIGINT"),
        ("description", "VARCHAR(1000)"),
        ("code", "VARCHAR(100)"),
        ("category", "VARCHAR(100)"),
        ("status", "VARCHAR(50)"),
        ("count", "INTEGER"),
        ("score", "DOUBLE PRECISION"),
        ("metadata", "VARCHAR(2000)"),
    ]:
        try:
            cursor.execute(f"ALTER TABLE item ADD COLUMN IF NOT EXISTS {col} {typ}")
            conn.commit()
        except Exception:
            conn.rollback()
    cursor.close()


def gerar_item_aleatorio():
    """Gera dados aleatórios para um Item (todos os campos)."""
    value_a = round(random.uniform(0.0, 1_000_000.0), 4)
    value_b = round(random.uniform(-1000.0, 1000.0), 4)
    prefixo = random.choice(LABEL_PREFIXOS)
    sufixo = random.choice(LABEL_SUFIXOS)
    label = f"{prefixo}-{sufixo}-{random.randint(1000, 99999)}"
    latitude = round(random.uniform(MG_LAT_MIN, MG_LAT_MAX), 7)
    longitude = round(random.uniform(MG_LONG_MIN, MG_LONG_MAX), 7)
    altitude = round(random.uniform(200.0, 1800.0), 2)
    now_ms = int(time.time() * 1000)
    created_at = now_ms - random.randint(0, 365 * 24 * 3600 * 1000)
    updated_at = created_at + random.randint(0, 86400 * 1000)
    # description: 200–500 chars
    num_palavras = random.randint(35, 80)
    description = " ".join(random.choices(DESC_PALAVRAS, k=num_palavras))
    if len(description) > 1000:
        description = description[:997] + "..."
    code = "COD-" + "".join(random.choices("ABCDEFGHJKLMNPQRSTUVWXYZ0123456789", k=random.randint(18, 48)))
    category = random.choice(CATEGORIAS)
    status = random.choice(STATUS_LIST)
    count = random.randint(0, 10000)
    score = round(random.uniform(0.0, 10.0), 2)
    metadata = '{"v":1,"t":"' + random.choice(["a", "b", "c"]) + '"}' if random.random() > 0.3 else None
    if metadata and len(metadata) > 2000:
        metadata = metadata[:1997] + '"}'
    return (
        value_a, value_b, label, latitude, longitude, altitude,
        created_at, updated_at, description, code, category, status, count, score, metadata
    )


def inserir_itens(conn, quantidade):
    """Insere N itens no banco e retorna (sucesso, erro)."""
    cursor = conn.cursor()
    cols = (
        "value_a", "value_b", "label", "latitude", "longitude", "altitude",
        "created_at", "updated_at", "description", "code", "category", "status",
        "count", "score", "metadata"
    )
    placeholders = ", ".join(["%s"] * len(cols))
    sql = f"INSERT INTO item ({', '.join(cols)}) VALUES ({placeholders})"
    sucesso = 0
    erro = 0
    for i in range(quantidade):
        row = gerar_item_aleatorio()
        try:
            cursor.execute(sql, row)
            sucesso += 1
            if (i + 1) % 100 == 0:
                print(f"Progresso: {i + 1}/{quantidade} itens inseridos...")
        except Exception as e:
            erro += 1
            print(f"Erro ao inserir item {i + 1}: {e}")
    conn.commit()
    cursor.close()
    return sucesso, erro


def main():
    if len(sys.argv) < 2:
        print("Uso: python gerar_itens_aleatorios.py <quantidade>")
        print("Exemplo: python gerar_itens_aleatorios.py 1000")
        sys.exit(1)

    quantidade = int(sys.argv[1])
    if quantidade <= 0:
        print("A quantidade deve ser maior que zero!")
        sys.exit(1)

    print("Conectando ao banco de dados...")
    conn = conectar_banco()

    print("Criando tabela item se não existir...")
    criar_tabela_se_nao_existir(conn)

    print(f"\nGerando {quantidade} itens aleatórios...")
    print("=" * 50)
    sucesso, erro = inserir_itens(conn, quantidade)
    conn.close()

    print("=" * 50)
    print("Concluído!")
    print(f"Itens inseridos com sucesso: {sucesso}")
    print(f"Erros: {erro}")
    print(f"Total processado: {sucesso + erro}")


if __name__ == "__main__":
    main()
