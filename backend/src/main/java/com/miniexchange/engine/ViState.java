package com.miniexchange.engine;

/**
 * 변동성완화장치(VI) 상태. WebSocket(/topic/vi)·이벤트로그로 발행된다.
 *
 * @param halted         현재 매칭 정지 여부
 * @param referencePrice 기준가(이 가격에서 임계 이상 벗어나는 체결이 정지를 유발)
 * @param until          정지 해제 예정 시각(epoch ms). halted=false면 0
 */
public record ViState(boolean halted, long referencePrice, long until) {}
