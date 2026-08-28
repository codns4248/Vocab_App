/**
 * 모델별 단가표 (USD / 1M 토큰).
 *
 * 단가가 개정되거나 모델이 바뀌면 이 파일만 수정하면 됩니다.
 * 출처: Anthropic 공식 가격표 (first-party API 기준)
 */
const MODEL_PRICING_USD_PER_MTOK = {
    "claude-haiku-4-5": {
        input: 1.0,
        output: 5.0,
        cacheRead: 0.1,
        // 캐시 쓰기(cache_creation_input_tokens) 단가.
        // 현재 costUsd 공식에는 포함하지 않습니다(아래 calculateCostUsd 주석 참고).
        cacheWrite: 1.25,
    },
    "claude-sonnet-5": {
        input: 3.0,
        output: 15.0,
        cacheRead: 0.3,
        cacheWrite: 3.75,
    },
    "claude-opus-5": {
        input: 5.0,
        output: 25.0,
        cacheRead: 0.5,
        cacheWrite: 6.25,
    },
};

/**
 * 단가표 조회용 모델 키를 만듭니다.
 *
 * API 응답의 model은 별칭이 아니라 날짜 스냅샷으로 옵니다.
 * (예: "claude-haiku-4-5" 로 요청해도 응답은 "claude-haiku-4-5-20251001")
 * 날짜 접미사만 떼면 단가표의 별칭 키와 맞습니다.
 *
 * @param {string} model 응답에 실려온 모델 문자열
 * @return {string} 단가표 조회용 키
 */
function normalizeModelId(model) {
    if (typeof model !== "string") {
        return "";
    }
    return model.replace(/-\d{8}$/, "");
}

/**
 * 호출 1건의 비용(USD)을 계산합니다.
 *
 * costUsd = (inputTokens * 입력단가 + outputTokens * 출력단가
 *            + cacheReadTokens * 캐시읽기단가) / 1_000_000
 *
 * cacheWriteTokens는 기록만 하고 비용에는 넣지 않습니다.
 * (현재 호출 경로에서 프롬프트 캐싱을 쓰지 않아 항상 0이며,
 *  캐싱을 도입하면 여기에 cacheWrite 항을 추가해야 합니다.)
 *
 * @param {string} model 실제 호출한 모델 문자열
 * @param {{inputTokens: number, outputTokens: number, cacheReadTokens: number}} tokens 토큰 사용량
 * @return {number} 소수점 6자리로 반올림한 USD 비용
 */
function calculateCostUsd(model, tokens) {
    const pricing = MODEL_PRICING_USD_PER_MTOK[normalizeModelId(model)];
    if (!pricing) {
        // 단가표에 없는 모델이면 0으로 두고 경고만 남깁니다.
        // 집계에서 비용이 과소 계산되므로 로그를 보고 단가표를 채워야 합니다.
        console.warn(`단가표에 없는 모델입니다(costUsd=0): ${model}`);
        return 0;
    }

    const inputTokens = tokens.inputTokens || 0;
    const outputTokens = tokens.outputTokens || 0;
    const cacheReadTokens = tokens.cacheReadTokens || 0;

    const cost =
        (inputTokens * pricing.input +
            outputTokens * pricing.output +
            cacheReadTokens * pricing.cacheRead) /
        1000000;

    return Number(cost.toFixed(6));
}

module.exports = { MODEL_PRICING_USD_PER_MTOK, normalizeModelId, calculateCostUsd };
