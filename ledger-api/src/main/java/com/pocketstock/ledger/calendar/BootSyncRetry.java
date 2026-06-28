package com.pocketstock.ledger.calendar;

import lombok.extern.slf4j.Slf4j;

/**
 * 부팅 동기화 공통 실행기 — 데몬 스레드에서 작업을 돌리고, 실패(예외) 시 백오프 재시도한다.
 *
 * <p>ledger가 core-api보다 먼저 떠 첫 upsert(Feign)가 실패하는 <b>기동 레이스</b>를 흡수한다.
 * 재시도 윈도우(= {@code maxAttempts × delayMs}) 안에 core-api가 뜨면 그 회차에서 성공한다.
 * 단, core-api가 끝내 안 뜨면 윈도우 소진 후 포기한다(저장 대상이 없으므로 당연).
 *
 * <p>{@code task}가 예외 없이 끝나면(정상 성공 또는 비활성 색·대상 없음으로 내부 skip) 즉시 종료해
 * 중복 실행/upsert가 없다. 따라서 재시도가 거는 조건은 <b>예외 전파</b>뿐이라, 호출 대상은 upsert
 * 실패를 삼키지 말고 던져야 한다.
 */
@Slf4j
final class BootSyncRetry {

    private BootSyncRetry() {}

    /**
     * @param threadName  데몬 스레드 이름
     * @param tag         로그 태그(예: {@code "[배당배치]"})
     * @param maxAttempts 최대 시도 횟수
     * @param delayMs     실패 후 재시도 간격(ms)
     * @param task        실행할 동기화 작업(실패 시 예외를 던져야 재시도됨)
     */
    static void runAsync(String threadName, String tag, int maxAttempts, long delayMs, Runnable task) {
        Thread t = new Thread(() -> {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    log.info("{} 부팅 동기화 시작 (시도 {}/{})", tag, attempt, maxAttempts);
                    task.run();
                    return;   // 성공(또는 내부 skip) → 종료
                } catch (Exception e) {
                    if (attempt >= maxAttempts) {
                        log.error("{} 부팅 동기화 최종 실패 — 재시도 {}회 소진: {}",
                                tag, maxAttempts, e.getMessage(), e);
                        return;
                    }
                    log.warn("{} 부팅 동기화 실패 (시도 {}/{}) — {}초 후 재시도: {}",
                            tag, attempt, maxAttempts, delayMs / 1000, e.getMessage());
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, threadName);
        t.setDaemon(true);
        t.start();
    }
}
