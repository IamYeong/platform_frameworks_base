# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

# native test
# ========================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    backup_helper_test.cpp

LOCAL_CFLAGS := -Wall -Werror
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := backup_helper_test
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/../../NOTICE
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)
LOCAL_SHARED_LIBRARIES := libandroidfw libutils

include $(BUILD_EXECUTABLE)

# java test
# ========================================
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_PACKAGE_NAME := BackupTest
LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)
