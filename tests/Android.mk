LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SDK_VERSION := current

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests

# LOCAL_JAVA_LIBRARIES := android.test.runner

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := ExactCalculatorTests

LOCAL_INSTRUMENTATION_FOR := ExactCalculator

LOCAL_AAPT_FLAGS := --rename-manifest-package com.android.exactcalculator.tests

include $(BUILD_PACKAGE)
