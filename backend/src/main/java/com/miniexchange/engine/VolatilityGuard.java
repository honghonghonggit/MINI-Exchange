package com.miniexchange.engine;

/**
 * 변동성완화장치(VI). 기준가에서 임계(threshold) 이상 벗어나는 체결이 들어오면
 * 일정 시간(cooldown) 매칭을 정지시킨다.
 *
 * 설계 결정:
 *   - 단일 매칭 스레드에서만 호출 → 내부 동기화 불필요(상태가 모두 평범한 필드).
 *   - 기준가는 매 체결마다가 아니라 refInterval 간격으로만 갱신하는 "느린 앵커".
 *     매 체결마다 갱신하면 기준가가 가격을 바짝 따라가 급변동을 절대 못 잡는다.
 *     앵커가 약간 stale하기 때문에 짧은 시간의 급격한 이동을 감지할 수 있다.
 *   - now(epoch ms)를 인자로 받아 시계 의존성을 제거 → 결정적 단위 테스트 가능.
 */
public class VolatilityGuard {

    private final boolean enabled;
    private final double threshold;   // 0.025 = ±2.5%
    private final long cooldownMs;    // 정지 지속 시간
    private final long refIntervalMs; // 기준가 갱신 최소 간격

    private long referencePrice = 0L; // 0 = 미설정
    private long lastRefUpdateMs = 0L;
    private boolean halted = false;
    private long haltUntil = 0L;

    public VolatilityGuard(double threshold, long cooldownMs, long refIntervalMs) {
        this(true, threshold, cooldownMs, refIntervalMs);
    }

    private VolatilityGuard(boolean enabled, double threshold, long cooldownMs, long refIntervalMs) {
        this.enabled = enabled;
        this.threshold = threshold;
        this.cooldownMs = cooldownMs;
        this.refIntervalMs = refIntervalMs;
    }

    /** VI를 끈 가드(기존 2-인자 MatchingEngine 생성자·테스트용). 절대 트리거되지 않는다. */
    public static VolatilityGuard disabled() {
        return new VolatilityGuard(false, 0, 0, 0);
    }

    /** 이 가격에 체결되면 정지를 유발하는가? */
    public boolean wouldTrigger(long price) {
        if (!enabled || referencePrice <= 0) return false;
        return Math.abs(price - referencePrice) / (double) referencePrice > threshold;
    }

    public void trigger(long now) {
        halted = true;
        haltUntil = now + cooldownMs;
    }

    public void release(long price, long now) {
        halted = false;
        forceReference(price, now); // 해제 시 기준가를 현재가로 재설정
    }

    /** refInterval 경과 시(또는 미설정 시)에만 기준가를 갱신하는 느린 앵커. */
    public void maybeUpdateReference(long price, long now) {
        if (!enabled || price <= 0) return;
        if (referencePrice <= 0 || now - lastRefUpdateMs >= refIntervalMs) {
            forceReference(price, now);
        }
    }

    private void forceReference(long price, long now) {
        if (price > 0) {
            referencePrice = price;
            lastRefUpdateMs = now;
        }
    }

    public boolean halted()        { return halted; }
    public long haltUntil()        { return haltUntil; }
    public long referencePrice()   { return referencePrice; }
    public boolean enabled()       { return enabled; }
}
