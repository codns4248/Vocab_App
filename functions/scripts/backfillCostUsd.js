#!/usr/bin/env node
/**
 * apiUsageLogs의 costUsd를 현재 단가표(pricing.js)로 다시 계산합니다.
 *
 * 단가가 개정되거나 단가표에 모델을 새로 추가했을 때 과거 문서를 맞추는 용도입니다.
 *
 * 실행 (functions 디렉터리에서):
 *   node scripts/backfillCostUsd.js            # 무엇이 바뀔지만 출력(기본값)
 *   node scripts/backfillCostUsd.js --apply    # 실제로 반영
 */
const admin = require("firebase-admin");
const { calculateCostUsd } = require("../pricing");
const { USAGE_COLLECTION } = require("../usageLogger");

const DEFAULT_PROJECT_ID = "vocaapp-bf580";
const BATCH_LIMIT = 400;

/**
 * costUsd를 다시 계산해 필요한 문서만 갱신합니다.
 * @return {Promise<void>} 완료 프라미스
 */
async function main() {
    const apply = process.argv.includes("--apply");
    const projectId = process.env.GCLOUD_PROJECT || DEFAULT_PROJECT_ID;

    admin.initializeApp({ projectId });
    const db = admin.firestore();

    const snapshot = await db.collection(USAGE_COLLECTION).get();
    if (snapshot.empty) {
        console.log(`${USAGE_COLLECTION}에 문서가 없습니다.`);
        return;
    }

    const changes = [];
    snapshot.docs.forEach((doc) => {
        const data = doc.data();
        const recalculated = calculateCostUsd(data.model, {
            inputTokens: data.inputTokens,
            outputTokens: data.outputTokens,
            cacheReadTokens: data.cacheReadTokens,
        });
        if (recalculated !== (data.costUsd || 0)) {
            changes.push({ ref: doc.ref, id: doc.id, before: data.costUsd || 0, after: recalculated, model: data.model });
        }
    });

    console.log(`\n전체 ${snapshot.size}건 중 재계산이 필요한 문서: ${changes.length}건\n`);
    changes.forEach((change) => {
        console.log(`  ${change.id}  ${change.model}  $${change.before.toFixed(6)} -> $${change.after.toFixed(6)}`);
    });

    if (changes.length === 0) {
        return;
    }

    if (!apply) {
        console.log("\n미리보기입니다. 실제로 반영하려면 --apply 를 붙여 다시 실행하세요.\n");
        return;
    }

    for (let i = 0; i < changes.length; i += BATCH_LIMIT) {
        const batch = db.batch();
        changes.slice(i, i + BATCH_LIMIT).forEach((change) => {
            batch.update(change.ref, { costUsd: change.after });
        });
        await batch.commit();
    }

    console.log(`\n${changes.length}건 반영 완료.\n`);
}

main()
    .then(() => process.exit(0))
    .catch((error) => {
        console.error("재계산 실패:", error);
        process.exit(1);
    });
