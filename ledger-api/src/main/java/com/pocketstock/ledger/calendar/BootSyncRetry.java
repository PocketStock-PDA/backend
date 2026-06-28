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
     * 데몬 스레드에서 {@link #runWithRetry}를 비동기 실행한다(부팅 리스너 블로킹 방지).
     *
     * @param threadName  데몬 스레드 이름
     * @param tag         로그 태그(예: {@code "[배당배치]"})
     * @param maxAttempts 최대 시도 횟수
     * @param delayMs     실패 후 재시도 간격(ms)
     * @param task        실행할 동기화 작업(실패 시 예외를 던져야 재시도됨)
     */
    static void runAsync(String threadName, String tag, int maxAttempts, long delayMs, Runnable task) {
        Thread t = new Thread(() -> runWithRetry(tag, maxAttempts, delayMs, task), threadName);
        t.setDaemon(true);
        t.start();
    }

    /**
     * {@code task}를 호출 스레드에서 동기로 실행하고, 실패(예외) 시 {@code delayMs} 간격으로 최대
     * {@code maxAttempts}회 재시도한다. 성공(또는 내부 skip으로 예외 없이 종료)하면 즉시 반환한다.
     * 재시도가 걸리는 조건은 예외 전파뿐이므로, 호출 대상은 실패를 삼키지 말고 던져야 한다.
     *
     * <p>이미 풀(bounded executor) 스레드 위에서 도는 호출자(체결 기반 수집 등)가 추가 스레드 없이
     * 재시도하도록 비동기 래핑과 분리해 둔다.
     */
    static void runWithRetry(String tag, int maxAttempts, long delayMs, Runnable task) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                task.run();
                return;   // 성공(또는 내부 skip) → 종료
            } catch (Exception e) {
                if (attempt >= maxAttempts) {
                    log.error("{} 동기화 최종 실패 — 재시도 {}회 소진: {}", tag, maxAttempts, e.getMessage(), e);
                    return;
                }
                log.warn("{} 동기화 실패 (시도 {}/{}) — {}초 후 재시도: {}",
                        tag, attempt, maxAttempts, delayMs / 1000, e.getMessage());
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
