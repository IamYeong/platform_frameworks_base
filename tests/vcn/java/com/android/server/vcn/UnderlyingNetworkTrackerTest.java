/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.vcn;

import static com.android.server.vcn.VcnTestUtils.setupSystemService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.os.ParcelUuid;
import android.os.test.TestLooper;
import android.telephony.SubscriptionInfo;
import android.util.ArraySet;

import com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;
import com.android.server.vcn.UnderlyingNetworkTracker.NetworkBringupCallback;
import com.android.server.vcn.UnderlyingNetworkTracker.RouteSelectionCallback;
import com.android.server.vcn.UnderlyingNetworkTracker.UnderlyingNetworkRecord;
import com.android.server.vcn.UnderlyingNetworkTracker.UnderlyingNetworkTrackerCallback;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class UnderlyingNetworkTrackerTest {
    private static final ParcelUuid SUB_GROUP = new ParcelUuid(new UUID(0, 0));
    private static final int INITIAL_SUB_ID_1 = 1;
    private static final int INITIAL_SUB_ID_2 = 2;
    private static final int UPDATED_SUB_ID = 3;

    private static final Set<Integer> INITIAL_SUB_IDS =
            new ArraySet<>(Arrays.asList(INITIAL_SUB_ID_1, INITIAL_SUB_ID_2));
    private static final Set<Integer> UPDATED_SUB_IDS =
            new ArraySet<>(Arrays.asList(UPDATED_SUB_ID));

    private static final NetworkCapabilities INITIAL_NETWORK_CAPABILITIES =
            new NetworkCapabilities.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                    .build();
    private static final NetworkCapabilities SUSPENDED_NETWORK_CAPABILITIES =
            new NetworkCapabilities.Builder(INITIAL_NETWORK_CAPABILITIES)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
                    .build();
    private static final NetworkCapabilities UPDATED_NETWORK_CAPABILITIES =
            new NetworkCapabilities.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .build();

    private static final LinkProperties INITIAL_LINK_PROPERTIES =
            getLinkPropertiesWithName("initial_iface");
    private static final LinkProperties UPDATED_LINK_PROPERTIES =
            getLinkPropertiesWithName("updated_iface");

    @Mock private Context mContext;
    @Mock private VcnNetworkProvider mVcnNetworkProvider;
    @Mock private ConnectivityManager mConnectivityManager;
    @Mock private TelephonySubscriptionSnapshot mSubscriptionSnapshot;
    @Mock private UnderlyingNetworkTrackerCallback mNetworkTrackerCb;
    @Mock private Network mNetwork;

    @Captor private ArgumentCaptor<RouteSelectionCallback> mRouteSelectionCallbackCaptor;

    private TestLooper mTestLooper;
    private VcnContext mVcnContext;
    private UnderlyingNetworkTracker mUnderlyingNetworkTracker;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();
        mVcnContext = spy(new VcnContext(mContext, mTestLooper.getLooper(), mVcnNetworkProvider));
        doNothing().when(mVcnContext).ensureRunningOnLooperThread();

        setupSystemService(
                mContext,
                mConnectivityManager,
                Context.CONNECTIVITY_SERVICE,
                ConnectivityManager.class);

        when(mSubscriptionSnapshot.getAllSubIdsInGroup(eq(SUB_GROUP))).thenReturn(INITIAL_SUB_IDS);

        mUnderlyingNetworkTracker =
                new UnderlyingNetworkTracker(
                        mVcnContext,
                        SUB_GROUP,
                        mSubscriptionSnapshot,
                        Collections.singleton(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                        mNetworkTrackerCb);
    }

    private static LinkProperties getLinkPropertiesWithName(String iface) {
        LinkProperties linkProperties = new LinkProperties();
        linkProperties.setInterfaceName(iface);
        return linkProperties;
    }

    private SubscriptionInfo getSubscriptionInfoForSubId(int subId) {
        SubscriptionInfo subInfo = mock(SubscriptionInfo.class);
        when(subInfo.getSubscriptionId()).thenReturn(subId);
        return subInfo;
    }

    @Test
    public void testNetworkCallbacksRegisteredOnStartup() {
        // verify NetworkCallbacks registered when instantiated
        verify(mConnectivityManager)
                .requestBackgroundNetwork(
                        eq(getWifiRequest()),
                        any(),
                        any(NetworkBringupCallback.class));
        verifyBackgroundCellRequests(mSubscriptionSnapshot, SUB_GROUP, INITIAL_SUB_IDS);

        verify(mConnectivityManager)
                .requestBackgroundNetwork(
                        eq(getRouteSelectionRequest()),
                        any(),
                        any(RouteSelectionCallback.class));
    }

    private void verifyBackgroundCellRequests(
            TelephonySubscriptionSnapshot snapshot,
            ParcelUuid subGroup,
            Set<Integer> expectedSubIds) {
        verify(snapshot).getAllSubIdsInGroup(eq(subGroup));

        for (final int subId : expectedSubIds) {
            verify(mConnectivityManager)
                    .requestBackgroundNetwork(
                            eq(getCellRequestForSubId(subId)),
                            any(),
                            any(NetworkBringupCallback.class));
        }
    }

    @Test
    public void testUpdateSubscriptionSnapshot() {
        // Verify initial cell background requests filed
        verifyBackgroundCellRequests(mSubscriptionSnapshot, SUB_GROUP, INITIAL_SUB_IDS);

        TelephonySubscriptionSnapshot subscriptionUpdate =
                mock(TelephonySubscriptionSnapshot.class);
        when(subscriptionUpdate.getAllSubIdsInGroup(eq(SUB_GROUP))).thenReturn(UPDATED_SUB_IDS);

        mUnderlyingNetworkTracker.updateSubscriptionSnapshot(subscriptionUpdate);

        // verify that initially-filed bringup requests are unregistered
        verify(mConnectivityManager, times(INITIAL_SUB_IDS.size()))
                .unregisterNetworkCallback(any(NetworkBringupCallback.class));
        verifyBackgroundCellRequests(subscriptionUpdate, SUB_GROUP, UPDATED_SUB_IDS);
    }

    private NetworkRequest getWifiRequest() {
        return getExpectedRequestBase()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
    }

    private NetworkRequest getCellRequestForSubId(int subId) {
        return getExpectedRequestBase()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .setNetworkSpecifier(new TelephonyNetworkSpecifier(subId))
                .build();
    }

    private NetworkRequest getRouteSelectionRequest() {
        return getExpectedRequestBase().build();
    }

    private NetworkRequest.Builder getExpectedRequestBase() {
        return new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                .addUnwantedCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
    }

    @Test
    public void testTeardown() {
        mUnderlyingNetworkTracker.teardown();

        // Expect 3 NetworkBringupCallbacks to be unregistered: 1 for WiFi and 2 for Cellular (1x
        // for each subId)
        verify(mConnectivityManager, times(3))
                .unregisterNetworkCallback(any(NetworkBringupCallback.class));
        verify(mConnectivityManager).unregisterNetworkCallback(any(RouteSelectionCallback.class));
    }

    @Test
    public void testUnderlyingNetworkRecordEquals() {
        UnderlyingNetworkRecord recordA =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        INITIAL_NETWORK_CAPABILITIES,
                        INITIAL_LINK_PROPERTIES,
                        false /* isBlocked */);
        UnderlyingNetworkRecord recordB =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        INITIAL_NETWORK_CAPABILITIES,
                        INITIAL_LINK_PROPERTIES,
                        false /* isBlocked */);
        UnderlyingNetworkRecord recordC =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        UPDATED_NETWORK_CAPABILITIES,
                        UPDATED_LINK_PROPERTIES,
                        false /* isBlocked */);

        assertEquals(recordA, recordB);
        assertNotEquals(recordA, recordC);
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForNetworkChange() {
        verifyRegistrationOnAvailableAndGetCallback();
    }

    private RouteSelectionCallback verifyRegistrationOnAvailableAndGetCallback() {
        return verifyRegistrationOnAvailableAndGetCallback(INITIAL_NETWORK_CAPABILITIES);
    }

    private RouteSelectionCallback verifyRegistrationOnAvailableAndGetCallback(
            NetworkCapabilities networkCapabilities) {
        verify(mConnectivityManager)
                .requestBackgroundNetwork(
                        eq(getRouteSelectionRequest()),
                        any(),
                        mRouteSelectionCallbackCaptor.capture());

        RouteSelectionCallback cb = mRouteSelectionCallbackCaptor.getValue();
        cb.onAvailable(mNetwork);
        cb.onCapabilitiesChanged(mNetwork, networkCapabilities);
        cb.onLinkPropertiesChanged(mNetwork, INITIAL_LINK_PROPERTIES);
        cb.onBlockedStatusChanged(mNetwork, false /* isFalse */);

        UnderlyingNetworkRecord expectedRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        networkCapabilities,
                        INITIAL_LINK_PROPERTIES,
                        false /* isBlocked */);
        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(eq(expectedRecord));
        return cb;
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForNetworkCapabilitiesChange() {
        RouteSelectionCallback cb = verifyRegistrationOnAvailableAndGetCallback();

        cb.onCapabilitiesChanged(mNetwork, UPDATED_NETWORK_CAPABILITIES);

        UnderlyingNetworkRecord expectedRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        UPDATED_NETWORK_CAPABILITIES,
                        INITIAL_LINK_PROPERTIES,
                        false /* isBlocked */);
        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(eq(expectedRecord));
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForLinkPropertiesChange() {
        RouteSelectionCallback cb = verifyRegistrationOnAvailableAndGetCallback();

        cb.onLinkPropertiesChanged(mNetwork, UPDATED_LINK_PROPERTIES);

        UnderlyingNetworkRecord expectedRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        INITIAL_NETWORK_CAPABILITIES,
                        UPDATED_LINK_PROPERTIES,
                        false /* isBlocked */);
        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(eq(expectedRecord));
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForNetworkSuspended() {
        RouteSelectionCallback cb = verifyRegistrationOnAvailableAndGetCallback();

        cb.onNetworkSuspended(mNetwork);

        UnderlyingNetworkRecord expectedRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        SUSPENDED_NETWORK_CAPABILITIES,
                        INITIAL_LINK_PROPERTIES,
                        false /* isBlocked */);
        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(eq(expectedRecord));
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForNetworkResumed() {
        RouteSelectionCallback cb =
                verifyRegistrationOnAvailableAndGetCallback(SUSPENDED_NETWORK_CAPABILITIES);

        cb.onNetworkResumed(mNetwork);

        UnderlyingNetworkRecord expectedRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        INITIAL_NETWORK_CAPABILITIES,
                        INITIAL_LINK_PROPERTIES,
                        false /* isBlocked */);
        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(eq(expectedRecord));
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForBlocked() {
        RouteSelectionCallback cb = verifyRegistrationOnAvailableAndGetCallback();

        cb.onBlockedStatusChanged(mNetwork, true /* isBlocked */);

        UnderlyingNetworkRecord expectedRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        INITIAL_NETWORK_CAPABILITIES,
                        INITIAL_LINK_PROPERTIES,
                        true /* isBlocked */);
        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(eq(expectedRecord));
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForNetworkLoss() {
        RouteSelectionCallback cb = verifyRegistrationOnAvailableAndGetCallback();

        cb.onLost(mNetwork);

        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(null);
    }

    @Test
    public void testRecordTrackerCallbackIgnoresDuplicateRecord() {
        RouteSelectionCallback cb = verifyRegistrationOnAvailableAndGetCallback();

        cb.onCapabilitiesChanged(mNetwork, INITIAL_NETWORK_CAPABILITIES);

        // Verify no more calls to the UnderlyingNetworkTrackerCallback when the
        // UnderlyingNetworkRecord does not actually change
        verifyNoMoreInteractions(mNetworkTrackerCb);
    }
}
