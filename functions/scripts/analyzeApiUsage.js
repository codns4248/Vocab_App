#!/usr/bin/env node
/**
 * apiUsageLogs 집계 스크립트.
 *
 * 실행 (functions 디렉터리에서):
 *   gcloud auth application-default login        # 최초 1회
 *   node scripts/analyzeApiUsage.js
 *   node scripts/analyzeApiUsage.js --month=2026-08
 *   node scripts/analyzeApiUsage.js --project=vocaapp-bf580
 *
 * 서비스 계정 키를 쓰려면 GOOGLE_APPLICATION_CREDENTIALS 환경변수를 지정하세요.
 */
const admin = require("firebase-admin");
const { USAGE_COLLECTION } = require("../usageLogger");

const DEFAULT_PROJECT_ID = "vocaapp-bf580";

/**
 * `--key=value` 형태의 CLI 인자를 객체로 파싱합니다.
 * @return {Object<string, string>} 파싱된 인자
 */
function parseArgs() {
    const args = {};
    for (const raw of process.argv.slice(2)) {
        const match = /^--([^=]+)=(.*)$/.exec(raw);
        if (match) {
            args[match[1]] = match[2];
        }
    }
    return args;
}

/**
 * 정렬된 배열에서 백분위수를 구합니다(nearest-rank).
 * @param {number[]} sorted 오름차순 정렬된 배열
 * @param {number} p 0~100
 * @return {number} 백분위수 값
 */
function percentile(sorted, p) {
    if (sorted.length === 0) {
        return 0;
    }
    const rank = Math.ceil((p / 100) * sorted.length);
    const index = Math.min(Math.max(rank - 1, 0), sorted.length - 1);
    return sorted[index];
}

/**
 * 정렬된 배열의 중앙값.
 * @param {number[]} sorted 오름차순 정렬된 배열
 * @return {number} 중앙값
 */
function median(sorted) {
    if (sorted.length === 0) {
        return 0;
    }
    const mid = Math.floor(sorted.length / 2);
    return sorted.length % 2 === 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
}

/**
 * 산술 평균.
 * @param {number[]} values 값 배열
 * @return {number} 평균
 */
function mean(values) {
    if (values.length === 0) {
        return 0;
    }
    return values.reduce((sum, value) => sum + value, 0) / values.length;
}

/**
 * Firestore Timestamp를 YYYY-MM 문자열로 바꿉니다.
 * @param {object|null} timestamp Firestore Timestamp
 * @return {string} YYYY-MM (없으면 "unknown")
 */
function monthKey(timestamp) {
    if (!timestamp || typeof timestamp.toDate !== "function") {
        return "unknown";
    }
    const date = timestamp.toDate();
    return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
}

/**
 * 숫자를 자릿수 맞춰 포맷합니다.
 * @param {number} value 값
 * @param {number} digits 소수점 자릿수
 * @return {string} 포맷된 문자열
 */
function fmt(value, digits) {
    return value.toFixed(digits);
}

/**
 * 컬렉션 전체를 페이지 단위로 읽어옵니다.
 * @param {object} db Firestore 인스턴스
 * @return {Promise<object[]>} 문서 데이터 배열
 */
async function fetchAllLogs(db) {
    const pageSize = 1000;
    const rows = [];
    let query = db.collection(USAGE_COLLECTION).orderBy("createdAt").limit(pageSize);

    for (;;) {
        const snapshot = await query.get();
        if (snapshot.empty) {
            break;
        }
        snapshot.docs.forEach((doc) => rows.push(doc.data()));
        if (snapshot.size < pageSize) {
            break;
        }
        const last = snapshot.docs[snapshot.docs.length - 1];
        query = db
            .collection(USAGE_COLLECTION)
            .orderBy("createdAt")
            .startAfter(last)
            .limit(pageSize);
    }

    return rows;
}

/**
 * 집계 결과를 출력합니다.
 * @return {Promise<void>} 완료 프라미스
 */
async function main() {
    const args = parseArgs();
    const projectId = args.project || process.env.GCLOUD_PROJECT || DEFAULT_PROJECT_ID;

    admin.initializeApp({ projectId });
    const db = admin.firestore();

    let logs = await fetchAllLogs(db);

    if (args.month) {
        logs = logs.filter((log) => monthKey(log.createdAt) === args.month);
    }

    const total = logs.length;
    if (total === 0) {
        console.log(`${USAGE_COLLECTION}에 집계할 문서가 없습니다.`);
        return;
    }

    const successLogs = logs.filter((log) => log.success);
    const successRate = (successLogs.length / total) * 100;

    // 호출당 outputTokens / costUsd 는 성공한 호출 기준으로 봅니다.
    const outputTokens = successLogs.map((log) => log.outputTokens || 0).sort((a, b) => a - b);
    const costs = successLogs.map((log) => log.costUsd || 0).sort((a, b) => a - b);

    const totalOutputTokens = successLogs.reduce((sum, log) => sum + (log.outputTokens || 0), 0);
    const totalWords = successLogs.reduce((sum, log) => sum + (log.extractedWordCount || 0), 0);

    const maxTokensCount = logs.filter((log) => log.stopReason === "max_tokens").length;

    const header = args.month ? `apiUsageLogs 집계 (${args.month})` : "apiUsageLogs 집계 (전체)";
    console.log(`\n=== ${header} ===`);
    console.log(`프로젝트: ${projectId}\n`);

    console.log(`총 호출 수        : ${total}`);
    console.log(`성공 호출 수      : ${successLogs.length}`);
    console.log(`성공률            : ${fmt(successRate, 2)}%\n`);

    console.log("호출당 outputTokens (성공 호출 기준)");
    console.log(`  평균            : ${fmt(mean(outputTokens), 1)}`);
    console.log(`  중앙값          : ${fmt(median(outputTokens), 1)}`);
    console.log(`  p90             : ${percentile(outputTokens, 90)}`);
    console.log(`  p95             : ${percentile(outputTokens, 95)}`);
    console.log(`  최댓값          : ${outputTokens.length ? outputTokens[outputTokens.length - 1] : 0}\n`);

    console.log("호출당 costUsd (성공 호출 기준)");
    console.log(`  평균            : $${fmt(mean(costs), 6)}`);
    console.log(`  중앙값          : $${fmt(median(costs), 6)}`);
    console.log(`  p90             : $${fmt(percentile(costs, 90), 6)}\n`);

    const tokensPerWord = totalWords > 0 ? totalOutputTokens / totalWords : 0;
    console.log(`단어 1개당 outputTokens 평균 : ${fmt(tokensPerWord, 2)}` +
        `  (총 ${totalOutputTokens} 토큰 / ${totalWords} 단어)`);
    console.log(`stopReason=max_tokens 비율   : ${fmt((maxTokensCount / total) * 100, 2)}%` +
        `  (${maxTokensCount}/${total})\n`);

    // uid별 월간 총 costUsd 상위 10명
    const byMonth = new Map();
    for (const log of logs) {
        const month = monthKey(log.createdAt);
        const uid = log.uid || "(anonymous)";
        if (!byMonth.has(month)) {
            byMonth.set(month, new Map());
        }
        const uidMap = byMonth.get(month);
        const prev = uidMap.get(uid) || { costUsd: 0, calls: 0 };
        uidMap.set(uid, {
            costUsd: prev.costUsd + (log.costUsd || 0),
            calls: prev.calls + 1,
        });
    }

    console.log("uid별 월간 총 costUsd 상위 10명");
    for (const month of [...byMonth.keys()].sort()) {
        const ranked = [...byMonth.get(month).entries()]
            .sort((a, b) => b[1].costUsd - a[1].costUsd)
            .slice(0, 10);
        console.log(`\n  [${month}]`);
        ranked.forEach(([uid, stats], index) => {
            console.log(
                `   ${String(index + 1).padStart(2)}. ${uid.padEnd(30)} ` +
                `$${fmt(stats.costUsd, 6)}  (${stats.calls}회)`
            );
        });
    }
    console.log("");
}

main()
    .then(() => process.exit(0))
    .catch((error) => {
        console.error("집계 실패:", error);
        process.exit(1);
    });
