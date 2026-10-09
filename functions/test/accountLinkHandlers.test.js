const assert = require("node:assert/strict");
const test = require("node:test");
const { readFileSync } = require("node:fs");
const { createRequire } = require("node:module");
const path = require("node:path");
const vm = require("node:vm");

function handlers({ uid = "google-user", missing = false, kakaoId = "42", verifiedId = "42" } = {}) {
    const events = [];
    class HttpsError extends Error {
        constructor(code, message) { super(message); this.code = code; }
    }
    const links = {
        verifyKakao: async () => verifiedId,
        resolveLogin: async () => uid,
        getKakaoId: async () => kakaoId,
        requireRecentGoogle: () => events.push("recent-google"),
        beginDeletion: async (owner, id) => events.push(["lock", owner, id]),
        finishDeletion: async (owner, id) => events.push(["cleanup", owner, id]),
    };
    const auth = {
        getUser: async () => {
            if (missing) throw Object.assign(new Error(), { code: "auth/user-not-found" });
            return { uid };
        },
        createUser: async (data) => events.push(["create", data.uid]),
        updateUser: async () => { throw new Error("Existing profile must not be overwritten"); },
        createCustomToken: async (owner) => { events.push(["token", owner]); return `token-${owner}`; },
        deleteUser: async (owner) => events.push(["delete-auth", owner]),
    };
    const mocks = {
        "firebase-functions/v2/https": { onCall: (options, fn) => fn, onRequest: (options, fn) => fn, HttpsError },
        "firebase-functions/params": { defineSecret: () => ({ value: () => "test" }) },
        "./accountLinks": { createAccountLinks: () => links },
        "firebase-admin": { apps: [{}], auth: () => auth, firestore: () => ({
            collection: () => ({ doc: (id) => id }),
            recursiveDelete: async (id) => events.push(["delete-data", id]),
        }) },
        "@google-cloud/tasks": { CloudTasksClient: class {} },
        "@anthropic-ai/sdk": {}, "exceljs": {}, "./usageLogger": {},
    };
    const filename = path.join(__dirname, "../index.js");
    const localRequire = createRequire(filename);
    const exported = {};
    vm.runInNewContext(readFileSync(filename, "utf8"), {
        exports: exported, require: (name) => mocks[name] || localRequire(name),
        console: { log() {}, warn() {}, error() {} }, Buffer, Date,
        fetch: async (url, options) => {
            if (url.endsWith("/unlink")) events.push(["unlink", options.headers.Authorization]);
            return { ok: true, json: async () => ({ kakao_account: { email: "kakao@example.com" } }) };
        },
    }, { filename });
    return { exported, events };
}

test("linked Kakao sign-in issues original Google UID and never creates a second user", async () => {
    const { exported, events } = handlers();
    const result = await exported.kakaoCustomToken({ data: { accessToken: "token" } });
    assert.equal(result.customToken, "token-google-user");
    assert.equal(result.isNewUser, false);
    assert.deepEqual(events, [["token", "google-user"]]);
});

test("first Kakao login still creates the legacy UID", async () => {
    const { exported, events } = handlers({ uid: "kakao:42", missing: true });
    const result = await exported.kakaoCustomToken({ data: { accessToken: "token" } });
    assert.equal(result.isNewUser, true);
    assert.deepEqual(events, [["create", "kakao:42"], ["token", "kakao:42"]]);
});

test("stale link never recreates a deleted Google account", async () => {
    const { exported, events } = handlers({ missing: true });
    await assert.rejects(exported.kakaoCustomToken({ data: { accessToken: "token" } }), { code: "failed-precondition" });
    assert.equal(events.length, 0);
});

test("Google-origin linked account deletion verifies Kakao and cleans the mapping", async () => {
    const { exported, events } = handlers();
    await exported.deleteAccount({ auth: { uid: "google-user" }, data: { kakaoAccessToken: "proof" } });
    assert.deepEqual(events, [
        ["lock", "google-user", "42"], ["delete-data", "google-user"],
        ["delete-auth", "google-user"], ["delete-data", "google-user"],
        ["unlink", "Bearer proof"], ["cleanup", "google-user", "42"],
    ]);
});

test("wrong Kakao account cannot delete any data", async () => {
    const { exported, events } = handlers({ verifiedId: "77" });
    await assert.rejects(exported.deleteAccount({ auth: { uid: "google-user" }, data: { kakaoAccessToken: "wrong" } }),
        { code: "permission-denied" });
    assert.equal(events.length, 0);
});

test("Google-only deletion requires recent Google authentication", async () => {
    const { exported, events } = handlers({ kakaoId: null });
    await exported.deleteAccount({ auth: { uid: "google-user" }, data: {} });
    assert.equal(events[0], "recent-google");
});
