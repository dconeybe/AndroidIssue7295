This is a sample app to reproduce https://github.com/firebase/firebase-android-sdk/issues/7295
"Firestore cache query slow when cache filled with deleted documents".

## Instructions

1. Start the Firestore emulator (see instructions below).
2. Open this directory in Android Studio.
3. Change the build variant to "release" (optional, but gives more meaningful performance numbers).
4. Run the app.
5. The first time the app runs it will create and delete 10,000 documents.
6. Then the app will run queries 200 times on a non-existent collection and then on the collection
   with the deleted documents.

## Starting the Firestore emulator

1. Install Node.js version 20 or newer.
2. Run `npm install -g firebase-tools`.
3. Run `firebase emulators:start --only firestore --project prjh5zbv64sv6`
