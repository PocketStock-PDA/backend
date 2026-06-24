#!/usr/bin/env python3
"""
한투(한국투자증권) 종목 마스터 파일 → PocketStock tradable_stocks seed 정제기.

입력 (raw/):
  - kospi_code.mst, kosdaq_code.mst   : 국내(고정폭). cp949
  - NASMST.COD, NYSMST.COD, AMSMST.COD : 미국(탭구분). cp949

정제 규칙
  - 주권(ST)·ETF(EF)·외국주권(FS)만 채택. ELW/ETN/신주인수권 등 제외.
  - 미국은 stis=2(Stock)·3(ETP/ETF)만. Index/Warrant 제외.
  - NXT/주간/통합은 별도 행이 아님(시세 venue) → 종목당 1행.
  - 단축코드(KR)/심볼(US) = stock_code = LS API tr_key.

출력
  - tradable_stocks.csv          : 검수용
  - tradable_stocks_seed.sql     : mysql-b(pocketstock_ledger) 적재용 INSERT
"""
import csv
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")

# 로고 에셋: 프론트 public/ 하위(=실제 서빙 위치)에 복사된 PNG가 있을 때만 logo_url을
# 채운다(copy_logos.py가 seed 매칭 종목만 복사). 없으면 NULL → 프론트 placeholder.
LOGO_PUBLIC = os.path.normpath(os.path.join(HERE, "..", "..", "..", "frontend", "public"))
# exchange → 로고 폴더(미국은 거래소 구분 없이 단일 폴더). 파일명 = stock_code.png
LOGO_DIR = {"KOSPI": "KOSPI-logo", "KOSDAQ": "KOSDAQ-logo",
            "NASDAQ": "us-logo", "NYSE": "us-logo", "AMEX": "us-logo"}


def logo_url(row):
    folder = LOGO_DIR.get(row["exchange"])
    if not folder:
        return ""
    rel = f"/{folder}/{row['stock_code']}.png"
    return rel if os.path.isfile(os.path.join(LOGO_PUBLIC, folder, row["stock_code"] + ".png")) else ""

# --- KR 큐레이션 기준 ---
KOSPI_TOP_N = 200                      # KOSPI: 전일 시가총액 상위 N개 STOCK
KOSDAQ150_CSV = "kosdaq150_codes.csv"  # KOSDAQ: KODEX 코스닥150 구성종목(=150개)
KR_ETF_TOP_N = 50                      # KR ETF: 순자산(시총) 상위 N개 (KOSPI+KOSDAQ 합산)
US_INDEX_CSV = "us_index_codes.csv"    # US 주식: 나스닥100 + S&P500 구성종목 (한투엔 시총 없음 → 외부 지수로 큐레이션)
US_ETF_CSV = "us_etf_codes.csv"        # US ETF: 대형 ETF 큐레이션 리스트 (마스터 대조 검증)

# KR 고정폭 트레일링 길이(증권그룹구분코드가 trail[:2]에 오도록 실측한 값)
KR_TRAIL = {"kospi_code.mst": 227, "kosdaq_code.mst": 221}
KR_MARKET = {"kospi_code.mst": "KOSPI", "kosdaq_code.mst": "KOSDAQ"}

# 증권그룹구분코드 → sec_type (채택 대상만)
KR_SECTYPE = {"ST": "STOCK", "EF": "ETF", "FS": "STOCK"}

# 미국 excd → market
US_MARKET = {"NAS": "NASDAQ", "NYS": "NYSE", "AMS": "AMEX"}
US_FILES = ["NASMST.COD", "NYSMST.COD", "AMSMST.COD"]
# 미국 stis(Security type) → sec_type
US_SECTYPE = {"2": "STOCK", "3": "ETF"}

COLS = ["stock_code", "exchange", "standard_code", "stock_name", "english_name",
        "currency", "sec_type", "is_fractional", "is_active", "logo_url"]


def parse_kr(fname):
    rows = []
    trail = KR_TRAIL[fname]
    market = KR_MARKET[fname]
    with open(os.path.join(RAW, fname), encoding="cp949") as f:
        for line in f:
            row = line.rstrip("\n")
            if len(row) <= trail:
                continue
            front = row[:-trail]
            short = front[:9].strip()
            std = front[9:21].strip()
            name = front[21:].strip()
            grp = row[-trail:][:2]
            sec = KR_SECTYPE.get(grp)
            if not sec or not short:
                continue
            try:
                mktcap = int(row[-15:-6].strip() or 0)  # prdy_avls_scal(전일 시총, 억)
            except ValueError:
                mktcap = 0
            rows.append({
                "stock_code": short,
                "exchange": market,
                "standard_code": std,
                "stock_name": name,
                "english_name": "",
                "currency": "KRW",
                "sec_type": sec,
                "is_fractional": 1,
                "is_active": 1,
                "_mktcap": mktcap,        # 내부 필터용(출력 제외)
            })
    return rows


def parse_us(fname):
    rows = []
    with open(os.path.join(RAW, fname), encoding="cp949") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) < 10:
                continue
            excd, symb, knam, enam, stis, curr = p[2], p[4], p[6], p[7], p[8], p[9]
            sec = US_SECTYPE.get(stis)
            mkt = US_MARKET.get(excd)
            if not sec or not mkt or not symb:
                continue
            rows.append({
                "stock_code": symb.strip(),
                "exchange": mkt,
                "standard_code": "",
                "stock_name": (knam or enam).strip(),
                "english_name": enam.strip(),
                "currency": (curr or "USD").strip(),
                "sec_type": sec,
                "is_fractional": 1,
                "is_active": 1,
            })
    return rows


def sql_val(v):
    if v == "" or v is None:
        return "NULL"
    if isinstance(v, int):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def load_codeset(fname, col="stock_code"):
    with open(os.path.join(HERE, fname), encoding="utf-8") as f:
        return {row[col] for row in csv.DictReader(f)}


def main():
    all_rows = []

    kospi_all = parse_kr("kospi_code.mst")
    kosdaq_all = parse_kr("kosdaq_code.mst")

    # KOSPI: STOCK만 시총 상위 N개
    kospi = sorted([r for r in kospi_all if r["sec_type"] == "STOCK"],
                   key=lambda r: r["_mktcap"], reverse=True)[:KOSPI_TOP_N]
    print(f"{'KOSPI(시총 top'+str(KOSPI_TOP_N)+')':22} -> {len(kospi):5} rows")
    all_rows += kospi

    # KOSDAQ: 코스닥150 구성종목만
    k150 = load_codeset(KOSDAQ150_CSV)
    kosdaq = [r for r in kosdaq_all if r["stock_code"] in k150]
    missing = k150 - {r["stock_code"] for r in kosdaq}
    print(f"{'KOSDAQ(코스닥150)':22} -> {len(kosdaq):5} rows" +
          (f"  ⚠ 마스터 미발견 {len(missing)}: {sorted(missing)}" if missing else ""))
    all_rows += kosdaq

    # KR ETF: 순자산(시총) 상위 N개 (KOSPI+KOSDAQ 합산)
    kr_etf = sorted([r for r in kospi_all + kosdaq_all if r["sec_type"] == "ETF"],
                    key=lambda r: r["_mktcap"], reverse=True)[:KR_ETF_TOP_N]
    print(f"{'KR ETF(순자산 top'+str(KR_ETF_TOP_N)+')':22} -> {len(kr_etf):5} rows")
    all_rows += kr_etf

    # US: 나스닥100 + S&P500 구성종목(주식) + 대형 ETF 큐레이션
    us_allow = load_codeset(US_INDEX_CSV) | load_codeset(US_ETF_CSV)
    us_rows = []
    for fn in US_FILES:
        us_rows += parse_us(fn)
    us = [r for r in us_rows if r["stock_code"] in us_allow]
    us_missing = us_allow - {r["stock_code"] for r in us}
    us_stock = sum(1 for r in us if r["sec_type"] == "STOCK")
    us_etf = sum(1 for r in us if r["sec_type"] == "ETF")
    print(f"{'US(주식'+str(us_stock)+'+ETF'+str(us_etf)+')':22} -> {len(us):5} rows" +
          (f"  ⚠ 마스터 미발견 {len(us_missing)}: {sorted(us_missing)}" if us_missing else ""))
    all_rows += us

    # stock_code 전역 유니크 보장(중복 시 첫 등장 우선)
    seen, dedup = set(), []
    for r in all_rows:
        if r["stock_code"] in seen:
            continue
        seen.add(r["stock_code"])
        dedup.append(r)
    dropped = len(all_rows) - len(dedup)
    print(f"{'TOTAL':18} -> {len(dedup):5} rows (중복 {dropped} 제거)")

    # 로고: 프론트 public/에 복사된 에셋이 있는 종목만 logo_url, 없으면 NULL
    for r in dedup:
        r["logo_url"] = logo_url(r)
    with_logo = sum(1 for r in dedup if r["logo_url"])
    print(f"{'  logo_url':18} -> {with_logo:5} / {len(dedup)} (에셋 없음 {len(dedup) - with_logo} = NULL)")

    # CSV
    csv_path = os.path.join(HERE, "tradable_stocks.csv")
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=COLS, extrasaction="ignore")
        w.writeheader()
        w.writerows(dedup)

    # SQL (멀티로우 INSERT, 1000행 단위)
    sql_path = os.path.join(HERE, "tradable_stocks_seed.sql")
    with open(sql_path, "w", encoding="utf-8") as f:
        f.write("-- 자동생성: parse_stock_master.py (한투 마스터 정제)\n")
        f.write("USE pocketstock_ledger;\n")
        f.write("SET NAMES utf8mb4;  -- 적재 client charset 고정(미지정 시 한글 이중인코딩)\n\n")
        cols = "stock_code, exchange, standard_code, stock_name, english_name, currency, sec_type, is_fractional, is_active, logo_url"
        CHUNK = 1000
        for i in range(0, len(dedup), CHUNK):
            f.write(f"INSERT INTO tradable_stocks ({cols}) VALUES\n")
            vals = []
            for r in dedup[i:i + CHUNK]:
                vals.append("(" + ", ".join(sql_val(r[c]) for c in COLS) + ")")
            f.write(",\n".join(vals))
            f.write("\nON DUPLICATE KEY UPDATE stock_name=VALUES(stock_name), "
                    "exchange=VALUES(exchange), logo_url=VALUES(logo_url), "
                    "updated_at=CURRENT_TIMESTAMP;\n\n")

    print(f"\n작성: {csv_path}")
    print(f"작성: {sql_path}")


if __name__ == "__main__":
    main()
