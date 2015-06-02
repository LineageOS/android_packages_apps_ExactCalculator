Run on Android with

1) Build the tests.
2) Install the calculator with
adb install <tree root>/out/target/product/generic/data/app/ExactCalculator/ExactCalculator.apk
3) adb install <tree root>/out/target/product/generic/data/app/ExactCalculatorTests/ExactCalculatorTests.apk
4) adb shell am instrument -w com.android.exactcalculator.tests/android.test.InstrumentationTestRunner

There are two kinds of tests:

1. A superficial test of calculator functionality through the UI.
This is a resurrected version of a test that appeared in KitKat.
This is currently only a placeholder for regression tests we shouldn't
forget; it doesn't yet actually do much of anything.

2. A test of the BoundedRationals library that mostly checks for agreement
with the constructive reals (CR) package.  (The BoundedRationals package
is used by the calculator mostly to identify exact results, i.e.
terminating decimal expansions.  But it's also used to optimize CR
computations, and bugs in BoundedRational could result in incorrect
outputs.)
