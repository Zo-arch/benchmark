#!/usr/bin/env python3
"""
Benchmark de leitura para mapa: tempo desde o request até a lista de (lat, long) pronta
para inserir no mapa, para Protobuf, JSON e SQLite.

Uso:
  python benchmark_map_leitura.py [--url BASE_URL] [--runs N]

Mede: GET → deserializar → extrair lista de (latitude, longitude) → tempo total (ms) e tamanho (bytes).
"""

import argparse
import json
import sqlite3
import statistics
import sys
import tempfile
import time
from pathlib import Path

try:
    import requests
except ImportError:
    print("Erro: instale requests (pip install requests)", file=sys.stderr)
    sys.exit(1)

# Caminho para importar benchmark.sync_pb2
SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

try:
    from benchmark import sync_pb2
except ImportError:
    sync_pb2 = None

ENDPOINTS = [
    ("Protobuf (.bin)", "/api/snapshot/download/bin", "protobuf"),
    ("SQLite (.sqlite)", "/api/snapshot/download/sqlite", "sqlite"),
    ("JSON (getAll)", "/api/items", "json"),
]


def extract_points_protobuf(data: bytes) -> list:
    """Deserializa .bin e retorna lista de (latitude, longitude)."""
    snapshot = sync_pb2.SnapshotResponse()
    snapshot.ParseFromString(data)
    return [(float(item.latitude), float(item.longitude)) for item in snapshot.items]


def extract_points_sqlite(data: bytes) -> list:
    """Abre o SQLite em memória a partir dos bytes e retorna lista de (latitude, longitude)."""
    with tempfile.NamedTemporaryFile(suffix=".sqlite", delete=False) as f:
        f.write(data)
        path = f.name
    conn = sqlite3.connect(path)
    try:
        cursor = conn.execute("SELECT latitude, longitude FROM item")
        rows = cursor.fetchall()
    except sqlite3.OperationalError as e:
        if "no such column: latitude" in str(e) or "no such column: longitude" in str(e):
            raise RuntimeError(
                "O snapshot SQLite não possui colunas latitude/longitude. "
                "Reinicie o app para gerar um novo snapshot com lat/long."
            ) from e
        raise
    finally:
        conn.close()
        Path(path).unlink(missing_ok=True)
    return [(float(r[0] or 0), float(r[1] or 0)) for r in rows]


def extract_points_json(data: bytes) -> list:
    """Deserializa JSON e retorna lista de (latitude, longitude)."""
    obj = json.loads(data)
    items = obj if isinstance(obj, list) else obj.get("items", obj)
    return [(float(it.get("latitude", 0)), float(it.get("longitude", 0))) for it in items]


def run_one(url: str, path: str, fmt: str) -> tuple[float, int, list]:
    """Faz GET, deserializa, extrai pontos. Retorna (tempo_ms, tamanho_bytes, pontos)."""
    full_url = url.rstrip("/") + path
    start = time.perf_counter()
    r = requests.get(full_url, timeout=300)
    r.raise_for_status()
    raw = r.content
    size = len(raw)

    if fmt == "protobuf":
        points = extract_points_protobuf(raw)
    elif fmt == "sqlite":
        points = extract_points_sqlite(raw)
    else:
        points = extract_points_json(raw)

    elapsed_ms = (time.perf_counter() - start) * 1000
    return elapsed_ms, size, points


def format_size(n: int) -> str:
    if n < 1024:
        return f"{n} B"
    if n < 1024 * 1024:
        return f"{n / 1024:.2f} KB"
    return f"{n / (1024 * 1024):.2f} MB"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Benchmark de leitura para mapa (tempo até lista de lat/long pronta)"
    )
    parser.add_argument(
        "--url",
        default="http://localhost:8081",
        help="URL base da API (default: http://localhost:8081)",
    )
    parser.add_argument(
        "--runs",
        type=int,
        default=3,
        metavar="N",
        help="Número de execuções por formato (default: 3)",
    )
    args = parser.parse_args()

    if args.runs < 1:
        print("--runs deve ser >= 1", file=sys.stderr)
        sys.exit(1)

    if sync_pb2 is None:
        print(
            "Aviso: módulo benchmark.sync_pb2 não encontrado. Protobuf será ignorado.",
            file=sys.stderr,
        )
        print(
            "  Para gerar: protoc -I src/main/proto --python_out=scripts src/main/proto/sync.proto",
            file=sys.stderr,
        )

    print(f"Base URL: {args.url}")
    print(f"Execuções por formato: {args.runs}")
    print()

    for label, path, fmt in ENDPOINTS:
        if fmt == "protobuf" and sync_pb2 is None:
            print(f"=== {label} ({path}) === (ignorado: sync_pb2 não disponível)")
            print()
            continue

        times_ms: list[float] = []
        sizes: list[int] = []
        num_points: list[int] = []

        for i in range(args.runs):
            elapsed_ms, size, points = run_one(args.url, path, fmt)
            times_ms.append(elapsed_ms)
            sizes.append(size)
            num_points.append(len(points))

        avg_ms = round(statistics.mean(times_ms), 2)
        min_ms = round(min(times_ms), 2)
        max_ms = round(max(times_ms), 2)
        avg_size = int(statistics.mean(sizes))
        n_pts = num_points[0] if num_points else 0

        print(f"=== {label} ({path}) ===")
        print(f"  Tempo até dados prontos:  médio={avg_ms} ms  min={min_ms} ms  max={max_ms} ms")
        print(f"  Tamanho do payload: {format_size(avg_size)} ({avg_size} bytes)")
        print(f"  Pontos (lat, long): {n_pts}")
        print()


if __name__ == "__main__":
    main()
