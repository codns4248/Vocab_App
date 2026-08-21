const admin = require("firebase-admin");
const { calculateCostUsd } = require("./pricing");

const USAGE_COLLECTION = "apiUsageLogs";

/**
 * base64 문자열이 디코딩되면 몇 바이트가 되는지 계산합니다.
 * 실제로 디코딩하면 이미지 수 MB를 그대로 메모리에 올리게 되므로 길이로만 계산합니다.
 *
 * @param {string} data base64 문자열
 * @return {number} 디코딩 후 바이트 수
 */
function base64ByteLength(data) {
    if (typeof data !== "string" || data.length === 0) {
        return 0;
    }
    let padding = 0;
    if (data.endsWith("==")) {
        padding = 2;
    } else if (data.endsWith("=")) {
        padding = 1;
    }
    return Math.floor(data.length / 4) * 3 - padding;
}

/**
 * 에러 객체를 로그에 남길 짧은 문자열로 바꿉니다.
 * Anthropic SDK 에러는 클래스 이름 + HTTP 상태(예: RateLimitError_429)로 남습니다.
 *
 * @param {unknown} error 잡힌 에러
 * @return {string|null} 에러 종류 문자열
 */
function classifyError(error) {
    if (!error) {
        return null;
    }
    if (typeof error === "string") {
        return error;
    }
    const name = error.constructor?.name || error.name || "UnknownError";
    return error.status ? `${name}_${error.status}` : name;
}

/**
 * Claude API 호출 1건의 토큰 사용량을 apiUsageLogs에 기록합니다.
 *
 * 요금제 설계를 위한 계측용이므로, 이 함수는 절대 호출자를 깨뜨리지 않습니다.
 * - await 하지 않습니다(응답 지연 금지). 쓰기 실패는 console.error만 남깁니다.
 * - 이미지 원본 / 추출된 단어 텍스트 / 프롬프트 내용은 저장하지 않습니다.
 *
 * @param {object} params 기록할 값
 * @param {string|null} params.uid Firebase Auth UID
 * @param {string} params.model 요청한 모델 문자열
 * @param {object|null} params.response Anthropic 응답(실패 시 null)
 * @param {number} params.latencyMs API 호출 시작~응답까지 경과 시간
 * @param {number} params.imageCount 이번 호출에 넣은 이미지 개수
 * @param {number} params.imageBytes base64 디코딩 후 바이트 합계
 * @param {number} params.extractedWordCount 추출된 단어 개수
 * @param {boolean} params.success 성공 여부
 * @param {string|null} params.errorType 실패 시 에러 종류
 * @return {void}
 */
function logApiUsage(params) {
    try {
        const response = params.response || null;
        const usage = (response && response.usage) || {};

        const inputTokens = usage.input_tokens || 0;
        const outputTokens = usage.output_tokens || 0;
        const cacheReadTokens = usage.cache_read_input_tokens || 0;
        const cacheWriteTokens = usage.cache_creation_input_tokens || 0;

        // 실제로 응답을 준 모델이 우선(라우팅/폴백 대비), 없으면 요청한 모델.
        const model = (response && response.model) || params.model;

        const doc = {
            uid: params.uid || null,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            model,
            inputTokens,
            outputTokens,
            cacheReadTokens,
            cacheWriteTokens,
            costUsd: calculateCostUsd(model, { inputTokens, outputTokens, cacheReadTokens }),
            stopReason: (response && response.stop_reason) || null,
            imageCount: params.imageCount || 0,
            imageBytes: params.imageBytes || 0,
            extractedWordCount: params.success ? params.extractedWordCount || 0 : 0,
            latencyMs: params.latencyMs || 0,
            success: Boolean(params.success),
            errorType: params.errorType || null,
        };

        // 의도적으로 await 하지 않습니다. 사용자 응답을 지연시키지 않기 위함입니다.
        admin
            .firestore()
            .collection(USAGE_COLLECTION)
            .add(doc)
            .catch((error) => console.error("apiUsageLogs 기록 실패:", error));
    } catch (error) {
        console.error("apiUsageLogs 기록 실패:", error);
    }
}

module.exports = { USAGE_COLLECTION, base64ByteLength, classifyError, logApiUsage };
