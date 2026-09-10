function checkReviewTask(data, taskName, kind, now = Date.now()) {
    if (!data || data.isStudying !== true) return "inactive";
    const expected = kind === "review" ? data.currentTaskId : data.currentRollbackTaskId;
    const taskId = typeof taskName === "string" ? taskName.split("/").pop() : "";
    if (!taskId || typeof expected !== "string" || expected.split("/").pop() !== taskId) {
        return "stale";
    }
    const date = kind === "review" ? data.nextReviewDate : data.rollbackTime;
    const due = date && typeof date.toMillis === "function" ? date.toMillis() : NaN;
    if (!Number.isFinite(due)) return "missing-time";
    if (now < due) return "early";
    if (kind === "review" && data.rollbackTime && now >= data.rollbackTime.toMillis()) {
        return "expired";
    }
    return "ready";
}

module.exports = { checkReviewTask };
