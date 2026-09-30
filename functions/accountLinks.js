const { HttpsError } = require("firebase-functions/v2/https");

function createAccountLinks(admin, appId, fetchApi = fetch) {
    const db = () => admin.firestore();
    const account = (uid) => db().collection("accountLinks").doc(uid);
    const identity = (id) => db().collection("kakaoIdentities").doc(id);

    async function verifyKakao(accessToken) {
        if (typeof accessToken !== "string" || !accessToken.trim()) {
            throw new HttpsError("invalid-argument", "카카오 인증이 필요합니다.");
        }
        let response;
        try {
            response = await fetchApi("https://kapi.kakao.com/v1/user/access_token_info", {
                headers: { Authorization: `Bearer ${accessToken}` },
                signal: AbortSignal.timeout(10000),
            });
        } catch {
            throw new HttpsError("unavailable", "카카오 인증 서버에 연결하지 못했습니다.");
        }
        if (!response.ok) throw new HttpsError("unauthenticated", "카카오 인증이 만료되었습니다.");
        const info = await response.json();
        if (!appId || Number(info.app_id) !== Number(appId)) {
            throw new HttpsError("permission-denied", "허용되지 않은 카카오 앱입니다.");
        }
        const id = String(info.id ?? "");
        if (!/^\d+$/.test(id)) throw new HttpsError("unauthenticated", "카카오 계정을 확인하지 못했습니다.");
        return id;
    }

    function requireAuth(request) {
        if (!request.auth) throw new HttpsError("unauthenticated", "로그인이 필요합니다.");
        return request.auth.uid;
    }

    function requireRecentGoogle(request) {
        requireAuth(request);
        const token = request.auth.token;
        const age = Date.now() / 1000 - Number(token.auth_time);
        if (token.firebase?.sign_in_provider !== "google.com" || !Number.isFinite(age) || age < -60 || age > 300) {
            throw new HttpsError("failed-precondition", "구글 계정을 다시 인증해주세요.");
        }
    }

    function ensureActive(data) {
        if (data?.deleting) throw new HttpsError("failed-precondition", "회원탈퇴 처리 중인 계정입니다.");
    }

    async function getKakaoId(uid, allowDeleting = false) {
        const snapshot = await account(uid).get();
        if (!allowDeleting) ensureActive(snapshot.data());
        return snapshot.data()?.kakaoId || (uid.startsWith("kakao:") ? uid.slice(6) : null);
    }

    // Login and linking reserve the same identity document to prevent two owners.
    async function resolveLogin(id) {
        return db().runTransaction(async (tx) => {
            const mapping = await tx.get(identity(id));
            const uid = mapping.data()?.uid || `kakao:${id}`;
            const state = await tx.get(account(uid));
            ensureActive(state.data());
            if (state.data()?.kakaoId && state.data().kakaoId !== id) {
                throw new HttpsError("failed-precondition", "카카오 연결 정보를 확인해주세요.");
            }
            tx.set(identity(id), { uid });
            tx.set(account(uid), { kakaoId: id }, { merge: true });
            return uid;
        });
    }

    async function linkKakao(request) {
        const uid = requireAuth(request);
        requireRecentGoogle(request);
        const id = await verifyKakao(request.data?.accessToken);
        // Older accounts may not yet have an identity mapping.
        if (`kakao:${id}` !== uid) {
            try {
                await admin.auth().getUser(`kakao:${id}`);
                throw new HttpsError("already-exists", "이미 다른 앱 계정에 가입된 카카오 계정입니다.");
            } catch (error) {
                if (error.code !== "auth/user-not-found") throw error;
            }
        }
        await db().runTransaction(async (tx) => {
            const mapping = await tx.get(identity(id));
            const state = await tx.get(account(uid));
            ensureActive(state.data());
            if (mapping.exists && mapping.data().uid !== uid) {
                throw new HttpsError("already-exists", "이미 다른 앱 계정에 연결된 카카오 계정입니다.");
            }
            const currentId = state.data()?.kakaoId || (uid.startsWith("kakao:") ? uid.slice(6) : null);
            if (currentId && currentId !== id) {
                throw new HttpsError("already-exists", "이미 카카오 계정이 연결되어 있습니다.");
            }
            tx.set(identity(id), { uid });
            tx.set(account(uid), { kakaoId: id }, { merge: true });
        });
        return { success: true };
    }

    async function status(request) {
        const uid = requireAuth(request);
        const user = await admin.auth().getUser(uid);
        return {
            google: user.providerData.some((provider) => provider.providerId === "google.com"),
            kakao: Boolean(await getKakaoId(uid, true)),
        };
    }

    async function reauthenticateKakao(request) {
        const uid = requireAuth(request);
        const id = await verifyKakao(request.data?.accessToken);
        if (await getKakaoId(uid) !== id || await resolveLogin(id) !== uid) {
            throw new HttpsError("permission-denied", "현재 계정에 연결된 카카오로 인증해주세요.");
        }
        return { customToken: await admin.auth().createCustomToken(uid, { provider: "kakao" }) };
    }

    async function beginDeletion(uid, expectedId) {
        await db().runTransaction(async (tx) => {
            const state = await tx.get(account(uid));
            const id = state.data()?.kakaoId || (uid.startsWith("kakao:") ? uid.slice(6) : null);
            if (id !== expectedId) throw new HttpsError("aborted", "연결 정보가 변경되었습니다. 다시 시도해주세요.");
            tx.set(account(uid), { deleting: true }, { merge: true });
        });
    }

    async function finishDeletion(uid, id) {
        await db().runTransaction(async (tx) => {
            const mapping = id ? await tx.get(identity(id)) : null;
            if (mapping?.data()?.uid === uid) tx.delete(identity(id));
            tx.delete(account(uid));
        });
    }

    return { verifyKakao, resolveLogin, linkKakao, status, reauthenticateKakao,
        getKakaoId, requireRecentGoogle, beginDeletion, finishDeletion };
}

module.exports = { createAccountLinks };
