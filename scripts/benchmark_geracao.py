#!/usr/bin/env python3
"""
Benchmark da camada de geração no servidor (DB load + serialização), sem transferir payload gigante.

Endpoints usados:
  /api/benchmark/generate/proto-full
  /api/benchmark/generate/sqlite-full
  /api/benchmark/generate/json-full
  /api/benchmark/generate/proto-map
  /api/benchmark/generate/json-map
"""

import argparse
import statistics
import sys
from typing import List

try:
    import requests
except ImportError:
    print("Erro: instale requests (pip install requests)", file=sys.stderr)
    sys.exit(1)

ENDPOINTS_FULL = [
    ("Protobuf full", "/api/benchmark/generate/proto-full"),
    ("SQLite full", "/api/benchmark/generate/sqlite-full"),
    ("JSON full", "/api/benchmark/generate/json-full"),
]

ENDPOINTS_MAP = [
    ("Protobuf map", "/api/benchmark/generate/proto-map"),
    ("JSON map", "/api/benchmark/generate/json-map"),
]


def main() -> None:
    parser = argparse.ArgumentParser(description="Benchmark da geração (DB + serialização) no servidor")
    parser.add_argument("--url", default="http://localhost:8081", help="URL base da API")
    parser.add_argument("--runs", type=int, default=5, metavar="N", help="Execuções por endpoint")
    parser.add_argument("--mode", choices=["full", "map"], default="full", help="Modo: full ou map")
    args = parser.parse_args()

    if args.runs < 1:
        print("--runs deve ser >= 1", file=sys.stderr)
        sys.exit(1)

    endpoints = ENDPOINTS_MAP if args.mode == "map" else ENDPOINTS_FULL

    print(f"Base URL: {args.url}")
    print(f"Execuções por endpoint: {args.runs}")
    print(f"Modo: {args.mode}")
    print()

    for label, path in endpoints:
        db_times: List[float] = []
        serialize_times: List[float] = []
        totals: List[float] = []
        sizes: List[int] = []
        items_count = 0

        for i in range(args.runs):
            full_url = args.url.rstrip("/") + path
            try:
                r = requests.get(full_url, timeout=600)
                r.raise_for_status()
                data = r.json()
            except requests.RequestException as e:
                print(f"Erro em {label} (run {i + 1}): {e}", file=sys.stderr)
                sys.exit(1)

            db_times.append(float(data["dbLoadMs"]))
            serialize_times.append(float(data["serializeMs"]))
            totals.append(float(data["totalMs"]))
            sizes.append(int(data["payloadBytes"]))
            items_count = int(data["items"])

        print(f"=== {label} ({path}) ===")
        print(f"  Itens: {items_count}")
        print(f"  Payload médio: {int(statistics.mean(sizes))} bytes")
        print(f"  DB load: médio={statistics.mean(db_times):.2f} ms")
        print(f"  Serialização: médio={statistics.mean(serialize_times):.2f} ms")
        print(f"  Total endpoint: médio={statistics.mean(totals):.2f} ms")
        print()


if __name__ == "__main__":
    main()
