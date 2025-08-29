# Firestore Slow Cache Query Reproduction

This is a sample app to reproduce issue
[#7295](https://github.com/firebase/firebase-android-sdk/issues/7295) in the
`firebase-android-sdk`: "Firestore cache query slow when cache filled with deleted documents".

This project can be used to demonstrate the performance issue and to test the corresponding fix.

## Prerequisites

Before you begin, ensure you have the following tools installed:

*   **Android Studio:** For building and running the Android application.
*   **Node.js:** Version 20 or newer (for running the Firebase CLI).
*   **Git:** For cloning the SDK repository to test the fix.

## Part 1: Reproducing the Performance Issue

These steps will guide you through running the app against a public version of the Firestore SDK
to see the slow query performance.

### 1. Start the Firestore Emulator

You must have the Firebase CLI installed. The emulator will be started with a placeholder project
ID, so no cloud project is required.

```bash
# Install Firebase Tools globally
npm install -g firebase-tools

# Start the Firestore emulator
firebase emulators:start --only firestore --project prjh5zbv64sv6
```

### 2. Run the App

1.  Open this project directory in Android Studio.
2.  Change the build variant to **release**
    (optional, but this gives more meaningful performance numbers).
3.  Run the app on a device or emulator.

The first time the app runs, it will create and then delete 10,000 documents. This populates the
local Firestore cache with "tombstone" entries that cause the performance issue. Subsequent runs
will skip this "setup" step and execute two sets of queries 200 times each: one against a
non-existent collection and one against the collection with the deleted documents.
The performance difference will be printed to the UI of the application.

## Part 2: Testing the Fix

These steps will guide you through building the Firestore SDK with the fix, publishing it to your
local Maven repository, and running the app against that local version.

The fix is contained in pull request
[#7301](https://github.com/firebase/firebase-android-sdk/pull/7301).

### 1. Prepare the App State

To properly test the fix, you must first run the app *without* the fix to populate the cache with
the problematic data.

1.  If run have run the app before, uninstall it to ensure the next run starts with a clean state.
    You can do so by running `adb shell pm uninstall com.example.firebaselocalsample`
2.  Ensure `app/build.gradle.kts` uses a public version of the Firestore SDK without the fix
    (e.g., `com.google.firebase:firebase-firestore:26.0.0`).
3.  Follow the steps in "Part 1" to run the app once.

### 2. Build and Publish the Fixed SDK Locally

1.  Clone the `firebase-android-sdk` repository:
    ```bash
    git clone https://github.com/firebase/firebase-android-sdk.git
    cd firebase-android-sdk
    ```

2.  Fetch the pull request containing the fix and check it out.
    ```bash
    git fetch origin pull/7301/head:PR7301_NoDocumentPerformanceFix
    git checkout PR7301_NoDocumentPerformanceFix
    ```

3.  Edit `firebase-firestore/gradle.properties` and change the version to `99.99.99`.
    Using a high, non-existent version number such as `99.99.99` ensures that Gradle will resolve
    this dependency to your local build instead of a public version from a public maven repository.

4.  Publish the modified Firestore SDK to your local Maven repository.
    ```bash
    ./gradlew :firebase-firestore:publishToMavenLocal
    ```

### 3. Run the App with the Fixed SDK

1.  Back in this sample app project, edit `settings.gradle.kts` and add `mavenLocal()` to the
    `dependencyResolutionManagement.repositories` block. This tells Gradle to look for dependencies
    in your local Maven repository.
    ```kotlin
    dependencyResolutionManagement {
        repositories {
            google()
            mavenCentral()
            mavenLocal() // Add this line
        }
    }
    ```

2.  In `app/build.gradle.kts`, change the version of the `com.google.firebase:firebase-firestore`
    dependency to `99.99.99`.
    ```kotlin
    dependencies {
        implementation("com.google.firebase:firebase-firestore:99.99.99")
        // ... other dependencies
    }
    ```

3.  Compile and run the app.
    It will now use the locally-built SDK.
    Observe the query times reported in the app's UI and compare them to the results from Part 1.
    The queries on the collection with deleted documents should now be significantly faster.
