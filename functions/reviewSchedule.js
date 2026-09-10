function createReviewSchedule(stampCount, settings, now = Date.now()) {
    if (!Number.isInteger(stampCount) || stampCount < 0 || stampCount >= 6) {
        throw new RangeError("Invalid study stage");
    }
    const stepKey = `step${stampCount + 1}`;
    const step = settings && (settings[stepKey] || settings.step1);
    const interval = Number(step?.interval);
    const grace = Number(step?.grace);
    if (!Number.isFinite(interval) || interval <= 0 ||
        !Number.isFinite(grace) || grace <= 0) {
        throw new RangeError("Review interval and grace must be positive minutes");
    }

    // Cloud Tasks uses seconds; round up so neither deadline fires early.
    const scheduledTime = Math.ceil((now + interval * 60000) / 1000);
    const rollbackTime = Math.ceil(scheduledTime + grace * 60);
    return { stepKey, scheduledTime, rollbackTime };
}

module.exports = { createReviewSchedule };
