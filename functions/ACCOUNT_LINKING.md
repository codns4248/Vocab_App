# Account Linking

The profile screen opens "로그인 계정 연결". Linking preserves the Firebase UID,
so vocabulary, study history and points remain attached to the same account.
Existing accounts are not merged or deleted automatically.

## Deployment

Deploy the server functions before installing the updated app:

```sh
firebase deploy --only functions:getAccountLinks,functions:linkKakaoAccount,functions:reauthenticateKakaoAccount,functions:kakaoCustomToken,functions:deleteAccount
```

No deployment is performed by the implementation task. Rebuild the Android app.
The existing administrator account does not need to be deleted or migrated.

## Storage And Authentication

- Google uses Firebase `linkWithCredential` after verifying the existing Kakao account.
- Kakao uses server-owned `kakaoIdentities/{kakaoId}` and `accountLinks/{uid}`.
- Both collections must remain inaccessible to clients. The repository's existing
  catch-all Firestore deny rule already covers them. Check deployed rules match.
- The server verifies the Kakao token's app ID and user ID. Linking Kakao also
  requires Google reauthentication within five minutes.
- A Kakao identity can belong to only one UID. Login and linking reserve the same
  identity transactionally. Legacy `kakao:{id}` accounts are retained.
- Google credential collisions and Kakao identities owned by another account are rejected.
- Connected Kakao accounts use Kakao reauthentication on withdrawal, regardless
  of the original signup provider. Unlink uses that user's access token, not an admin key.
- Google-only withdrawal requires recent Google reauthentication.
- This version adds linking only, not individual-provider unlinking or account merging.

## Verification

```sh
node --test functions/test/*.test.js
```

Before release, test both signup directions with disposable accounts:

1. Record UID, vocabulary and points; connect the second provider.
2. Sign out and sign in using each provider. Confirm the same UID and data,
   and no duplicate signup points or onboarding.
3. Try an already-registered provider, the wrong account during reauthentication,
   cancellation, rotation and network failure. Confirm the original account remains.
4. Withdraw a linked account. Confirm Firebase Auth, user data and both mapping
   documents are removed; sign in again only as a new account.

Automated tests mock Firebase/Kakao services. They do not replace live OAuth,
Firestore concurrency or device UI testing. No real account is deleted by tests.

## References

- [Firebase Android account linking](https://firebase.google.com/docs/auth/android/account-linking)
- [Kakao token verification and unlink APIs](https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api)
