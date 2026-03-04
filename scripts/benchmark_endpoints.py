#!/usr/bin/env python3
"""
Benchmark de download puro (snapshots pré-gerados no MinIO):
tempo de transação, processamento (servidor) e banda (tamanho da resposta).

Uso:
  python benchmark_endpoints.py [--url BASE_URL] [--runs N] [--mode full|map] [--csv ARQUIVO]

O servidor envia o tempo de processamento no header X-Processing-Ms (em ms).
"""

import argparse
import statistics
import sys
import time
from pathlib import Path
from typing import List, Optional, Tuple

try:
    import requests
except ImportError:
    print("Erro: instale requests (pip install requests)", file=sys.stderr)
    sys.exit(1)

HEADER_PROCESSING_MS = "X-Processing-Ms"

ENDPOINTS_FULL = [
    ("Protobuf full (.bin)", "/api/snapshot/download/bin"),
    ("SQLite full (.sqlite)", "/api/snapshot/download/sqlite"),
    ("JSON full (.json)", "/api/snapshot/download/json"),
]

ENDPOINTS_MAP = [
    ("Protobuf map (.bin)", "/api/snapshot/download/map-bin"),
    ("SQLite map (.sqlite)", "/api/snapshot/download/map-sqlite"),
    ("JSON map (.json)", "/api/snapshot/download/map-json"),
]


def run_one(url: str, path: str) -> Tuple[float, int, Optional[int]]:
    """Faz uma requisição GET. Retorna (tempo_s, tamanho_bytes, processing_ms ou None)."""
    full_url = url.rstrip("/") + path
    start = time.perf_counter()
    r = requests.get(full_url, timeout=300)
    elapsed = time.perf_counter() - start
    r.raise_for_status()
    size = len(r.content)
    processing_ms = r.headers.get(HEADER_PROCESSING_MS)
    processing = int(processing_ms) if processing_ms is not None else None
    return elapsed, size, processing


def format_size(n: int) -> str:
    if n < 1024:
        return f"{n} B"
    if n < 1024 * 1024:
        return f"{n / 1024:.2f} KB"
    return f"{n / (1024 * 1024):.2f} MB"


def main() -> None:
    parser = argparse.ArgumentParser(description="Benchmark de download puro (Protobuf, SQLite e JSON)")
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
        help="Número de execuções por endpoint (default: 3)",
    )
    parser.add_argument(
        "--csv",
        metavar="ARQUIVO",
        help="Exportar resultado em CSV",
    )
    parser.add_argument(
        "--mode",
        choices=["full", "map"],
        default="full",
        help="Modo dos endpoints (default: full)",
    )
    args = parser.parse_args()

    if args.runs < 1:
        print("--runs deve ser >= 1", file=sys.stderr)
        sys.exit(1)

    print(f"Base URL: {args.url}")
    print(f"Execuções por endpoint: {args.runs}")
    print(f"Modo: {args.mode}")
    print()

    all_rows = []
    endpoints = ENDPOINTS_MAP if args.mode == "map" else ENDPOINTS_FULL

    for label, path in endpoints:
        times_s: List[float] = []
        sizes: List[int] = []
        processings_ms: List[int] = []

        for i in range(args.runs):
            try:
                elapsed, size, processing_ms = run_one(args.url, path)
                times_s.append(elapsed)
                sizes.append(size)
                if processing_ms is not None:
                    processings_ms.append(processing_ms)
            except requests.RequestException as e:
                print(f"Erro em {label} (run {i + 1}): {e}", file=sys.stderr)
                sys.exit(1)

        time_ms_list = [t * 1000 for t in times_s]
        avg_time_ms = statistics.mean(time_ms_list)
        min_time_ms = min(time_ms_list)
        max_time_ms = max(time_ms_list)
        avg_size = int(statistics.mean(sizes))
        avg_processing_ms = statistics.mean(processings_ms) if processings_ms else None

        row = {
            "endpoint": label,
            "path": path,
            "runs": args.runs,
            "tempo_medio_ms": round(avg_time_ms, 2),
            "tempo_min_ms": round(min_time_ms, 2),
            "tempo_max_ms": round(max_time_ms, 2),
            "banda_bytes": avg_size,
            "banda_fmt": format_size(avg_size),
            "processamento_servidor_ms": round(avg_processing_ms, 2) if avg_processing_ms is not None else None,
        }
        all_rows.append(row)

        print(f"=== {label} ({path}) ===")
        print(f"  Tempo (cliente):  médio={avg_time_ms:.2f} ms  min={min_time_ms:.2f} ms  max={max_time_ms:.2f} ms")
        print(f"  Banda (resposta): {format_size(avg_size)} ({avg_size} bytes)")
        if avg_processing_ms is not None:
            print(f"  Processamento (servidor, X-Processing-Ms): médio={avg_processing_ms:.2f} ms")
        else:
            print("  Processamento (servidor): (header não enviado)")
        print()

    if args.csv:
        csv_path = Path(args.csv)
        with open(csv_path, "w") as f:
            f.write("endpoint;path;runs;tempo_medio_ms;tempo_min_ms;tempo_max_ms;banda_bytes;banda_fmt;processamento_servidor_ms\n")
            for row in all_rows:
                proc = "" if row["processamento_servidor_ms"] is None else str(row["processamento_servidor_ms"])
                f.write(f"{row['endpoint']};{row['path']};{row['runs']};{row['tempo_medio_ms']};{row['tempo_min_ms']};{row['tempo_max_ms']};{row['banda_bytes']};{row['banda_fmt']};{proc}\n")
        print(f"Resultado exportado para: {csv_path}")


if __name__ == "__main__":
    main()
