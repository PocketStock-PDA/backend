import csv
import os
import pymysql

conn = pymysql.connect(
    host=os.environ.get('DB_HOST', 'localhost'),
    port=int(os.environ.get('DB_PORT', '3306')),
    user=os.environ.get('DB_USERNAME', 'root'),
    password=os.environ.get('DB_PASSWORD', ''),
    database='pocketstock_main',
    charset='utf8mb4'
)

with conn:
    cur = conn.cursor()

    cur.execute("SET FOREIGN_KEY_CHECKS = 0")

    # 테스트용 유저 1명 (user_id=1, JWT 토큰 발급용)
    cur.execute("""
        INSERT IGNORE INTO users (id, username, name, status, created_at, updated_at)
        VALUES (1, 'testuser', '테스트유저', 'ACTIVE', NOW(), NOW())
    """)

    # CSV의 user_id 범위(1~100) 모두 삽입
    for uid in range(2, 101):
        cur.execute("""
            INSERT IGNORE INTO users (id, username, name, status, created_at, updated_at)
            VALUES (%s, %s, %s, 'ACTIVE', NOW(), NOW())
        """, (uid, f'user{uid}', f'유저{uid}'))

    # linked_institutions (더미)
    cur.execute("""
        INSERT IGNORE INTO linked_institutions (id, user_id, company, institution_type, link_status, linked_at)
        VALUES (1, 1, 'KB', 'CARD', 'LINKED', NOW())
    """)

    # linked_accounts (더미)
    cur.execute("""
        INSERT IGNORE INTO linked_accounts (id, user_id, institution_id, account_type, balance, currency, last_synced_at)
        VALUES (1, 1, 1, 'CARD', 0, 'KRW', NOW())
    """)

    conn.commit()

    # CSV import
    csv_path = r'C:\Users\kimsh\Downloads\card_transactions_mock_100users_realistic_2026_04_05_06\card_transactions_100users_2026_04_05_06.csv'

    with open(csv_path, encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        rows = []
        for row in reader:
            rows.append((
                int(row['id']),
                int(row['user_id']),
                int(row['linked_account_id']),
                row['merchant_name'],
                row['mcc'],
                row['category'],
                float(row['amount']),
                row['paid_at'],
                1 if row['is_cancelled'] == 'True' else 0
            ))

    cur.executemany("""
        INSERT IGNORE INTO card_transactions
        (id, user_id, linked_account_id, merchant_name, mcc, category, amount, paid_at, is_cancelled)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
    """, rows)

    conn.commit()
    cur.execute("SET FOREIGN_KEY_CHECKS = 1")
    conn.commit()

    cur.execute("SELECT COUNT(*) FROM card_transactions")
    count = cur.fetchone()[0]
    print(f"card_transactions import 완료: {count}건")
