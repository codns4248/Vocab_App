const test = require("node:test");
const assert = require("node:assert/strict");
const { createAccountLinks } = require("../accountLinks");

function fixture({ users = {}, documents = {}, token = { app_id: 123, id: 42 } } = {}) {
    const docs = new Map(Object.entries(documents));
    const snapshot = (path) => ({ exists: docs.has(path), data: () => docs.get(path) });
    const db = {
        collection: (name) => ({ doc: (id) => ({ path: `${name}/${id}`, get: async () => snapshot(`${name}/${id}`) }) }),
        runTransaction: async (action) => {
            const writes = [];
            const result = await action({
                get: async (ref) => snapshot(ref.path),
                set: (ref, value, options) => writes.push(() => docs.set(ref.path,
                    options?.merge ? { ...docs.get(ref.path), ...value } : value)),
                delete: (ref) => writes.push(() => docs.delete(ref.path)),
            });
            writes.forEach((write) => write());
            return result;
        },
    };
    const auth = {
        getUser: async (uid) => {
            if (!users[uid]) throw Object.assign(new Error("not found"), { code: "auth/user-not-found" });
            return users[uid];
        },
        createCustomToken: async (uid) => `token-for-${uid}`,
    };
    return { docs, service: createAccountLinks({ firestore: () => db, auth: () => auth }, 123,
        async () => ({ ok: true, json: async () => token })) };
}

function request(uid = "google-user", provider = "google.com", age = 0) {
    return { auth: { uid, token: { auth_time: Date.now() / 1000 - age,
        firebase: { sign_in_provider: provider } } }, data: { accessToken: "verified-token" } };
}

test("Google account links Kakao and future Kakao login resolves to the same UID", async () => {
    const { service, docs } = fixture();
    await service.linkKakao(request());
    assert.equal(await service.resolveLogin("42"), "google-user");
    assert.equal(docs.get("accountLinks/google-user").kakaoId, "42");
    await service.linkKakao(request()); // Idempotent retry.
});

test("new and legacy Kakao logins keep their original UID", async () => {
    const { service } = fixture();
    assert.equal(await service.resolveLogin("42"), "kakao:42");
    assert.equal(await service.getKakaoId("kakao:77"), "77");
});

test("reject another mapping owner", async () => {
    const { service } = fixture({ documents: { "kakaoIdentities/42": { uid: "other" } } });
    await assert.rejects(service.linkKakao(request()), { code: "already-exists" });
});

test("reject existing legacy account even before its first mapping", async () => {
    const { service } = fixture({ users: { "kakao:42": { providerData: [] } } });
    await assert.rejects(service.linkKakao(request()), { code: "already-exists" });
});

test("reject replacing an already linked Kakao account", async () => {
    const { service } = fixture({ documents: { "accountLinks/google-user": { kakaoId: "77" } } });
    await assert.rejects(service.linkKakao(request()), { code: "already-exists" });
});

test("reject missing, stale and non-Google authentication for linking Kakao", async () => {
    const { service } = fixture();
    await assert.rejects(service.linkKakao({}), { code: "unauthenticated" });
    await assert.rejects(service.linkKakao(request("user", "google.com", 301)), { code: "failed-precondition" });
    await assert.rejects(service.linkKakao(request("user", "custom")), { code: "failed-precondition" });
});

test("reject another Kakao app and missing access token", async () => {
    const { service } = fixture({ token: { app_id: 999, id: 42 } });
    await assert.rejects(service.verifyKakao("token"), { code: "permission-denied" });
    await assert.rejects(service.verifyKakao(""), { code: "invalid-argument" });
});

test("Kakao reauthentication only issues a token for the existing owner", async () => {
    const { service } = fixture();
    const result = await service.reauthenticateKakao(request("kakao:42", "custom"));
    assert.equal(result.customToken, "token-for-kakao:42");
    await assert.rejects(service.reauthenticateKakao(request("kakao:77", "custom")), { code: "permission-denied" });
});

test("server status handles both providers without depending on UID prefix", async () => {
    const { service } = fixture({ users: { "google-user": { providerData: [{ providerId: "google.com" }] } },
        documents: { "accountLinks/google-user": { kakaoId: "42" } } });
    assert.deepEqual(await service.status(request()), { google: true, kakao: true });
});

test("deletion blocks linking and login, then removes both mapping documents", async () => {
    const { service, docs } = fixture();
    await service.linkKakao(request());
    await service.beginDeletion("google-user", "42");
    await assert.rejects(service.resolveLogin("42"), { code: "failed-precondition" });
    await assert.rejects(service.linkKakao(request()), { code: "failed-precondition" });
    assert.equal(await service.getKakaoId("google-user", true), "42");
    await service.finishDeletion("google-user", "42");
    assert.equal(docs.size, 0);
});

test("deletion refuses a changed identity and preserves another owner's mapping", async () => {
    const { service, docs } = fixture({ documents: {
        "accountLinks/google-user": { kakaoId: "77" }, "kakaoIdentities/77": { uid: "other" },
    } });
    await assert.rejects(service.beginDeletion("google-user", "42"), { code: "aborted" });
    await service.finishDeletion("google-user", "77");
    assert.equal(docs.get("kakaoIdentities/77").uid, "other");
});
