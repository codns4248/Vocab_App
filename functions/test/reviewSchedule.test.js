const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { createRequire } = require("node:module");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");
const { createReviewSchedule } = require("../reviewSchedule");
const { checkReviewTask } = require("../reviewTaskGuard");

const now = Date.UTC(2026, 8, 10, 6, 40, 0);
const settings = { step1: { interval: 1, grace: 1 }, step2: { interval: 10, grace: 5 } };
const timestamp = (millis) => ({ toMillis: () => millis });

function loadScheduler({ stampCount = 0, timeSettings = settings, stopDuringCreate = false } = {}) {
    let version = 1;
    const book = { isStudying: true, stampCount, nextReviewDate: null, rollbackTime: null };
    const created = [];
    const deleted = [];
    const snapshot = () => {
        const readVersion = version;
        return {
            exists: true,
            data: () => ({ ...book }),
            updateTime: { version: readVersion, isEqual: (other) => readVersion === other.version },
        };
    };
    const bookRef = { collection: () => ({ doc: () => bookRef }), get: async () => snapshot() };
    const db = {
        collection: (name) => ({
            doc: () => name === "reviewAndRollbackTimeSetting" ?
                { get: async () => ({ data: () => timeSettings }) } : bookRef,
        }),
        runTransaction: async (action) => action({
            get: async () => snapshot(),
            update: (ref, fields) => { Object.assign(book, fields); version++; },
        }),
    };
    class HttpsError extends Error {
        constructor(code, message) { super(message); this.code = code; }
    }
    const mocks = {
        "firebase-functions/v2/https": {
            onCall: (options, handler) => handler,
            onRequest: (options, handler) => handler,
            HttpsError,
        },
        "firebase-functions/params": { defineSecret: () => ({ value: () => "test-secret" }) },
        "firebase-admin": {
            apps: [{}],
            firestore: Object.assign(() => db, { Timestamp: { fromMillis: timestamp } }),
        },
        "@google-cloud/tasks": { CloudTasksClient: class {
            queuePath(project, region, queue) { return `projects/${project}/locations/${region}/queues/${queue}`; }
            taskPath(project, region, queue, id) { return `${this.queuePath(project, region, queue)}/tasks/${id}`; }
            async createTask({ task }) {
                created.push(task);
                if (stopDuringCreate) { book.isStudying = false; version++; }
                return [{ name: task.name }];
            }
            async deleteTask({ name }) { deleted.push(name); }
        } },
        "@anthropic-ai/sdk": {},
        "exceljs": {},
        "./usageLogger": {},
    };
    const filename = path.join(__dirname, "../index.js");
    const localRequire = createRequire(filename);
    const handlers = {};
    vm.runInNewContext(readFileSync(filename, "utf8"), {
        exports: handlers,
        require: (name) => mocks[name] || localRequire(name),
        Buffer, Date,
        console: { log() {}, error() {}, warn() {} },
    }, { filename });
    return { schedule: handlers.scheduleReviewNotification, book, created, deleted };
}

for (const offset of [-180000, 43200000]) {
    test(`device clock offset ${offset} cannot move server deadlines`, async (t) => {
        t.mock.method(Date, "now", () => now);
        const { schedule, book, created } = loadScheduler();
        const result = await schedule({ auth: { uid: "user" }, data: {
            docId: "book", scheduledTime: (now + offset + 60000) / 1000,
            rollbackTime: (now + offset + 120000) / 1000,
        } });
        assert.equal(result.scheduledTime, now / 1000 + 60);
        assert.equal(result.rollbackTime, now / 1000 + 120);
        assert.equal(created[0].scheduleTime.seconds * 1000, book.nextReviewDate.toMillis());
        assert.equal(created[1].scheduleTime.seconds * 1000, book.rollbackTime.toMillis());
        assert.equal(book.rollbackState, false);
        assert.equal(book.buttonOn, false);
        assert.equal(checkReviewTask(book, created[1].name, "rollback", now), "early");
        assert.equal(checkReviewTask(book, created[1].name, "rollback", now + 120000), "ready");
        assert.equal(checkReviewTask(book, "old-task", "rollback", now + 120000), "stale");
    });
}

test("next stage uses Firestore stampCount without device timestamps", async (t) => {
    t.mock.method(Date, "now", () => now);
    const { schedule } = loadScheduler({ stampCount: 1 });
    const result = await schedule({ auth: { uid: "user" }, data: { docId: "book" } });
    assert.equal(result.scheduledTime, now / 1000 + 600);
    assert.equal(result.rollbackTime, now / 1000 + 900);
});

test("stopping study while tasks are created prevents publishing and cleans up tasks", async (t) => {
    t.mock.method(Date, "now", () => now);
    const { schedule, created, deleted, book } = loadScheduler({ stopDuringCreate: true });
    await assert.rejects(schedule({ auth: { uid: "user" }, data: { docId: "book" } }), { code: "aborted" });
    assert.deepEqual(deleted, created.map(task => task.name));
    assert.equal(book.isStudying, false);
    assert.equal(book.currentTaskId, undefined);
});

test("invalid grace fails before creating tasks", async () => {
    const { schedule, created } = loadScheduler({ timeSettings: { step1: { interval: 1, grace: 0 } } });
    await assert.rejects(schedule({ auth: { uid: "user" }, data: { docId: "book" } }), { code: "failed-precondition" });
    assert.equal(created.length, 0);
});

test("completed study cannot schedule a seventh stage", () => {
    assert.throws(() => createReviewSchedule(6, settings, now), RangeError);
});

test("fractional seconds round up and pending reservations cannot execute", () => {
    const schedule = createReviewSchedule(0, settings, now + 500);
    assert.equal(schedule.scheduledTime, now / 1000 + 61);
    const pending = { isStudying: true, currentRollbackTaskId: "old", rollbackTime: null };
    assert.equal(checkReviewTask(pending, "old", "rollback", now), "missing-time");
});
