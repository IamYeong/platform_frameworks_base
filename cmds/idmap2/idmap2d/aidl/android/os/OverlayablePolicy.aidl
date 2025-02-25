/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

/**
 * @see ResourcesTypes.h ResTable_overlayable_policy_header::PolicyFlags
 * @hide
 */
interface OverlayablePolicy {
  const int PUBLIC = 0x00000001;
  const int SYSTEM_PARTITION = 0x00000002;
  const int VENDOR_PARTITION = 0x00000004;
  const int PRODUCT_PARTITION = 0x00000008;
  const int SIGNATURE = 0x00000010;
  const int ODM_PARTITION = 0x00000020;
  const int OEM_PARTITION = 0x00000040;
  const int ACTOR_SIGNATURE = 0x00000080;
  const int CONFIG_SIGNATURE = 0x0000100;
}
