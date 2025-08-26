This is a sample app to reproduce https://github.com/firebase/firebase-android-sdk/issues/7295
"Firestore cache query slow when cache filled with deleted documents".

## Instructions

1. Start the Firestore emulator (see instructions below).
2. Open this directory in Android Studio.
3. Change the build variant to "release" (optional, but gives more meaningful performance numbers).
4. Change `RUN_SETUP` to `true` in `MainActivity.kt`.
5. Run the app; wait for the UI to indicate that 10,000 documents have been created.
6. Change `RUN_SETUP` back to `false` in `MainActivity.kt`.
7. Run the app; wait for it to run 200 iterations of both "active" and "inactive" queries.

## Starting the Firestore emulator

1. Install Node.js version 20 or newer.
2. Run `npm install -g firebase-tools`.
3. Run `firebase emulators:start --only firestore --project prjh5zbv64sv6`
