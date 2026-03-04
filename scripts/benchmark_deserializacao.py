#!/usr/bin/env python3
"""
Benchmark de deserialização isolada: mede apenas o tempo de parse (sem rede).
Faz um GET por formato, guarda o payload em memória, depois roda N vezes só a
deserialização + extração de (lat, long) e reporta tempo médio/min/max por parse.

Uso:
  python benchmark_deserializacao.py [--url BASE_URL] [--runs N] [--mode full|map]
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

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

try:
    from benchmark import sync_pb2
except ImportError:
    sync_pb2 = None


def fetch_bin(url: str) -> bytes:
    r = requests.get(url.rstrip("/") + "/api/snapshot/download/bin", timeout=300)
    r.raise_for_status()
    return r.content


def fetch_json(url: str) -> bytes:
    r = requests.get(url.rstrip("/") + "/api/snapshot/download/json", timeout=300)
    r.raise_for_status()
    return r.content


def fetch_sqlite(url: str) -> bytes:
    r = requests.get(url.rstrip("/") + "/api/snapshot/download/sqlite", timeout=300)
    r.raise_for_status()
    return r.content


def fetch_map_sqlite(url: str) -> bytes:
    r = requests.get(url.rstrip("/") + "/api/snapshot/download/map-sqlite", timeout=300)
    r.raise_for_status()
    return r.content


def parse_protobuf(data: bytes) -> list:
    snapshot = sync_pb2.SnapshotResponse()
    snapshot.ParseFromString(data)
    return [(float(item.latitude), float(item.longitude)) for item in snapshot.items]


def fetch_map_bin(url: str) -> bytes:
    r = requests.get(url.rstrip("/") + "/api/snapshot/download/map-bin", timeout=300)
    r.raise_for_status()
    return r.content


def fetch_map_json(url: str) -> bytes:
    r = requests.get(url.rstrip("/") + "/api/snapshot/download/map-json", timeout=300)
    r.raise_for_status()
    return r.content


def parse_map_protobuf(data: bytes) -> list:
    snapshot = sync_pb2.MapSnapshotResponse()
    snapshot.ParseFromString(data)
    return [(float(item.latitude), float(item.longitude)) for item in snapshot.items]


def parse_json(data: bytes) -> list:
    obj = json.loads(data)
    items = obj if isinstance(obj, list) else obj.get("items", obj)
    return [(float(it.get("latitude", 0)), float(it.get("longitude", 0))) for it in items]


def parse_sqlite(data: bytes) -> list:
    with tempfile.NamedTemporaryFile(suffix=".sqlite", delete=False) as f:
        f.write(data)
        path = f.name
    conn = sqlite3.connect(path)
    try:
        try:
            cur = conn.execute("SELECT latitude, longitude FROM item")
        except sqlite3.OperationalError:
            cur = conn.execute("SELECT latitude, longitude FROM item_map")
        rows = cur.fetchall()
    except sqlite3.OperationalError as e:
        if "no such column" in str(e):
            raise RuntimeError(
                "Snapshot SQLite sem colunas latitude/longitude. Reinicie o app para gerar novo snapshot."
            ) from e
        raise
    finally:
        conn.close()
        Path(path).unlink(missing_ok=True)
    return [(float(r[0] or 0), float(r[1] or 0)) for r in rows]


def main() -> None:
    parser = argparse.ArgumentParser(description="Benchmark de deserialização isolada (sem rede)")
    parser.add_argument("--url", default="http://localhost:8081", help="URL base da API")
    parser.add_argument("--runs", type=int, default=10, metavar="N", help="Execuções de parse por formato (default: 10)")
    parser.add_argument("--mode", choices=["full", "map"], default="full", help="Modo do payload: full ou map")
    args = parser.parse_args()

    if args.runs < 1:
        print("--runs deve ser >= 1", file=sys.stderr)
        sys.exit(1)

    if sync_pb2 is None:
        print("Aviso: benchmark.sync_pb2 não encontrado. Protobuf será ignorado.", file=sys.stderr)

    print(f"Base URL: {args.url}")
    print(f"Parse isolado (sem rede), {args.runs} runs por formato.")
    print(f"Modo: {args.mode}")
    print()

    if args.mode == "map":
        configs = [
            ("Protobuf map (.bin)", fetch_map_bin, parse_map_protobuf),
            ("SQLite map (.sqlite)", fetch_map_sqlite, parse_sqlite),
            ("JSON map (.json)", fetch_map_json, parse_json),
        ]
    else:
        configs = [
            ("Protobuf full (.bin)", fetch_bin, parse_protobuf),
            ("SQLite full (.sqlite)", fetch_sqlite, parse_sqlite),
            ("JSON full (.json)", fetch_json, parse_json),
        ]

    for label, fetch, parse in configs:
        if "Protobuf" in label and sync_pb2 is None:
            print(f"=== {label} === (ignorado: sync_pb2 não disponível)")
            print()
            continue
        print(f"Baixando {label}...")
        try:
            raw = fetch(args.url)
        except requests.RequestException as e:
            print(f"Erro ao baixar {label}: {e}", file=sys.stderr)
            sys.exit(1)
        size = len(raw)
        times_ms = []
        for _ in range(args.runs):
            start = time.perf_counter()
            points = parse(raw)
            elapsed_ms = (time.perf_counter() - start) * 1000
            times_ms.append(elapsed_ms)
        avg = round(statistics.mean(times_ms), 2)
        mn = round(min(times_ms), 2)
        mx = round(max(times_ms), 2)
        n_pts = len(points)
        print(f"=== {label} ===")
        print(f"  Payload: {size} bytes | Pontos: {n_pts}")
        print(f"  Deserialização isolada: médio={avg} ms  min={mn} ms  max={mx} ms")
        print()


if __name__ == "__main__":
    main()
