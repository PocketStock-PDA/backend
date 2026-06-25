# 공통 프록시 헤더 + 경로 라우팅 (80/443 server 양쪽에서 include)
# - 라우팅을 한 곳에만 두기 위한 partial. 파일명이 .inc 라 nginx 의 conf.d/*.conf
#   자동 로드 대상이 아님(단독 로드되면 server 밖이라 에러). 반드시 server {} 안에서 include.
# - X-Forwarded-Proto $scheme: 80 에선 http, 443 에선 https 가 자동으로 들어가
#   Spring(forward-headers-strategy=framework)이 실제 스킴을 인식.

proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;

# ---- core-api (DB A) ----
location /api/auth/            { proxy_pass http://core-api; }
location /api/assets/          { proxy_pass http://core-api; }
location /api/budget/          { proxy_pass http://core-api; }
location /api/notifications    { proxy_pass http://core-api; }
location /api/recommendations/ { proxy_pass http://core-api; }
location /api/trading/calendar { proxy_pass http://core-api; }  # /api/trading/ 보다 더 구체적 → nginx 최장일치로 먼저 매칭
location /api/users/           { proxy_pass http://core-api; }

# ---- ledger-api (DB B) ----
location /api/cma/      { proxy_pass http://ledger-api; }
location /api/exchange/ { proxy_pass http://ledger-api; }
location /api/recon/    { proxy_pass http://ledger-api; }
location /api/trading/  { proxy_pass http://ledger-api; }

# ---- WebSocket (STOMP /ws) ----
location /ws {
    proxy_pass http://ledger-api;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 120s;
}

# 그 외(actuator·internal·dev·swagger 등)는 외부로 노출하지 않음
location / { return 404; }
