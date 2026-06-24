#!/usr/bin/env python3
"""
seed(tradable_stocks_seed.sql) 종목에 매칭되는 로고 PNG만 프론트 public/으로 복사.

마스터 에셋 라이브러리에는 전체 상장 종목 로고가 들어있으나, 거래 대상은 seed
종목뿐이므로 해당 stock_code에 일치하는 로고만 복사한다(불필요한 수천 장 제외).

  파일명 규칙: KR=단축코드 6자리.png / US=심볼.png (stock_code와 그대로 일치)
  폴더 규칙:   KOSPI→KOSPI-logo / KOSDAQ→KOSDAQ-logo / NASDAQ·NYSE·AMEX→us-logo

사용:
  python3 copy_logos.py --src /path/to/master/public   # 마스터 에셋 라이브러리
  (--dest 미지정 시 모노레포 frontend/public)

이후 parse_stock_master.py를 다시 돌리면 frontend/public 존재 여부로 logo_url을
채운다(복사된 종목만 logo_url, 나머지는 NULL → 프론트 placeholder fallback).
"""
import argparse
import os
import re
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
SEED = os.path.join(HERE, "tradable_stocks_seed.sql")
DEFAULT_DEST = os.path.normpath(os.path.join(HERE, "..", "..", "..", "frontend", "public"))

# exchange → public 하위 로고 폴더
LOGO_DIR = {
    "KOSPI": "KOSPI-logo",
    "KOSDAQ": "KOSDAQ-logo",
    "NASDAQ": "us-logo",
    "NYSE": "us-logo",
    "AMEX": "us-logo",
}


def seed_rows():
    text = open(SEED, encoding="utf-8").read()
    return re.findall(r"\('([^']+)', '(KOSPI|KOSDAQ|NASDAQ|NYSE|AMEX)'", text)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=os.environ.get("STOCK_LOGO_SRC"),
                    help="마스터 에셋 라이브러리 경로(KOSPI-logo·KOSDAQ-logo·us-logo 보유)")
    ap.add_argument("--dest", default=DEFAULT_DEST, help="복사 대상(기본: frontend/public)")
    args = ap.parse_args()
    if not args.src:
        ap.error("--src(또는 STOCK_LOGO_SRC) 로 마스터 에셋 경로를 지정하세요")

    rows = seed_rows()
    copied = {d: 0 for d in set(LOGO_DIR.values())}
    missing = {}
    for code, exch in rows:
        folder = LOGO_DIR[exch]
        fname = code + ".png"
        src = os.path.join(args.src, folder, fname)
        if not os.path.isfile(src):
            missing.setdefault(exch, []).append(code)
            continue
        dst_dir = os.path.join(args.dest, folder)
        os.makedirs(dst_dir, exist_ok=True)
        shutil.copy2(src, os.path.join(dst_dir, fname))
        copied[folder] += 1

    print(f"대상 seed 종목: {len(rows)}")
    for folder, n in sorted(copied.items()):
        print(f"  복사 {folder:12} {n:5}")
    total_missing = sum(len(v) for v in missing.values())
    print(f"에셋 미보유(→ logo_url NULL, placeholder): {total_missing}")
    for exch, codes in missing.items():
        print(f"  {exch:7} {len(codes):4}: {codes}")


if __name__ == "__main__":
    main()
