package com.miniexchange.api.dto;

/**
 * 이벤트 리플레이 결과.
 * 기록된 주문 입력(제출/취소)만 새 오더북에 가격-시간 우선으로 재생해 체결을 재구성하고,
 * 원본 EXECUTION 이벤트와 대조한다.
 *
 * @param inputEvents            재생한 입력 이벤트 수(ORDER_SUBMITTED + ORDER_CANCELLED)
 * @param regeneratedExecutions  재구성된 체결 건수
 * @param regeneratedQuantity    재구성된 체결 총량
 * @param recordedExecutions     원본 기록 체결 건수(EXECUTION)
 * @param recordedQuantity       원본 기록 체결 총량
 * @param matched                재구성 결과가 원본과 일치(건수+총량)하는가
 * @param finalBidLevels         재구성 후 남은 매수 호가 레벨 수
 * @param finalAskLevels         재구성 후 남은 매도 호가 레벨 수
 */
public record ReplayResult(
        int inputEvents,
        int regeneratedExecutions,
        long regeneratedQuantity,
        int recordedExecutions,
        long recordedQuantity,
        boolean matched,
        int finalBidLevels,
        int finalAskLevels
) {}
