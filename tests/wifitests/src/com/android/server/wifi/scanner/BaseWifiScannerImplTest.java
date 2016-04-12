/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.wifi.scanner;

import static com.android.server.wifi.ScanTestUtil.NativeScanSettingsBuilder;
import static com.android.server.wifi.ScanTestUtil.assertScanDataEquals;
import static com.android.server.wifi.ScanTestUtil.createFreqSet;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiSsid;
import android.os.SystemClock;

import com.android.server.wifi.MockAlarmManager;
import com.android.server.wifi.MockLooper;
import com.android.server.wifi.MockResources;
import com.android.server.wifi.MockWifiMonitor;
import com.android.server.wifi.ScanDetail;
import com.android.server.wifi.ScanResults;
import com.android.server.wifi.WifiMonitor;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.scanner.ChannelHelper.ChannelCollection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Base unit tests that should pass for all implementations of
 * {@link com.android.server.wifi.scanner.WifiScannerImpl}.
 */
public abstract class BaseWifiScannerImplTest {
    @Mock Context mContext;
    MockAlarmManager mAlarmManager;
    MockWifiMonitor mWifiMonitor;
    MockLooper mLooper;
    @Mock WifiNative mWifiNative;
    MockResources mResources;

    /**
     * mScanner implementation should be filled in by derived test class
     */
    WifiScannerImpl mScanner;

    @Before
    public void setUpBase() throws Exception {
        MockitoAnnotations.initMocks(this);

        mLooper = new MockLooper();
        mAlarmManager = new MockAlarmManager();
        mWifiMonitor = new MockWifiMonitor();
        mResources = new MockResources();

        when(mWifiNative.getInterfaceName()).thenReturn("a_test_interface_name");

        when(mContext.getSystemService(Context.ALARM_SERVICE))
                .thenReturn(mAlarmManager.getAlarmManager());

        when(mContext.getResources()).thenReturn(mResources);
    }

    protected Set<Integer> expectedBandScanFreqs(int band) {
        ChannelCollection collection = mScanner.getChannelHelper().createChannelCollection();
        collection.addBand(band);
        return collection.getSupplicantScanFreqs();
    }

    protected Set<Integer> expectedBandAndChannelScanFreqs(int band, int... channels) {
        ChannelCollection collection = mScanner.getChannelHelper().createChannelCollection();
        collection.addBand(band);
        for (int channel : channels) {
            collection.addChannel(channel);
        }
        return collection.getSupplicantScanFreqs();
    }

    @Test
    public void singleScanSuccess() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000) // ms
                .withMaxApPerScan(10)
                .addBucketWithBand(10000 /* ms */, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        doSuccessfulSingleScanTest(settings, expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ),
                new HashSet<Integer>(),
                ScanResults.create(0, 2400, 2450, 2450, 2400, 2450, 2450, 2400, 2450, 2450), false);
    }

    @Test
    public void singleScanSuccessWithChannels() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithChannels(20000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN, 5650)
                .build();

        doSuccessfulSingleScanTest(settings, createFreqSet(5650),
                new HashSet<Integer>(),
                ScanResults.create(0, 5650, 5650, 5650, 5650, 5650, 5650, 5650, 5650), false);
    }

    @Test
    public void singleScanSuccessWithFullResults() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                        | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        doSuccessfulSingleScanTest(settings, expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ),
                new HashSet<Integer>(),
                ScanResults.create(0, 2400, 2450, 2450, 2400, 2450, 2450, 2400, 2450, 2450), true);
    }

    /**
     * Tests whether the provided hidden networkId's in scan settings is correctly passed along
     * when invoking native scan.
     */
    @Test
    public void singleScanSuccessWithHiddenNetworkIds() {
        int[] hiddenNetworkIds = {0, 5};
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .withHiddenNetworkIds(hiddenNetworkIds)
                .addBucketWithChannels(20000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN, 5650)
                .build();

        Set<Integer> hiddenNetworkIdSet = new HashSet<Integer>();
        for (int i = 0; i < hiddenNetworkIds.length; i++) {
            hiddenNetworkIdSet.add(hiddenNetworkIds[i]);
        }
        doSuccessfulSingleScanTest(settings, createFreqSet(5650),
                hiddenNetworkIdSet,
                ScanResults.create(0, 5650, 5650, 5650, 5650, 5650, 5650, 5650, 5650), false);
    }

    @Test
    public void overlappingSingleScanFails() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000) // ms
                .withMaxApPerScan(10)
                .addBucketWithBand(10000 /* ms */, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        WifiNative.ScanSettings settings2 = new NativeScanSettingsBuilder()
                .withBasePeriod(10000) // ms
                .withMaxApPerScan(10)
                .addBucketWithBand(10000 /* ms */, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_5_GHZ)
                .build();
        WifiNative.ScanEventHandler eventHandler2 = mock(WifiNative.ScanEventHandler.class);

        // scan start succeeds
        when(mWifiNative.scan(any(Set.class), any(Set.class))).thenReturn(true);

        assertTrue(mScanner.startSingleScan(settings, eventHandler));
        assertFalse("second scan while first scan running should fail immediately",
                mScanner.startSingleScan(settings2, eventHandler2));
    }

    @Test
    public void singleScanFailOnExecute() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        ScanResults results = ScanResults.create(0, 2400, 2450, 2450);

        InOrder order = inOrder(eventHandler, mWifiNative);

        // scan fails
        when(mWifiNative.scan(any(Set.class), any(Set.class))).thenReturn(false);

        // start scan
        assertTrue(mScanner.startSingleScan(settings, eventHandler));

        mLooper.dispatchAll();
        order.verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_FAILED);

        verifyNoMoreInteractions(eventHandler);
    }

    /**
     * Test that a scan failure is reported if a scan times out
     */
    @Test
    public void singleScanFailOnTimeout() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        ScanResults results = ScanResults.create(0, 2400, 2450, 2450);

        InOrder order = inOrder(eventHandler, mWifiNative);

        // scan succeeds
        when(mWifiNative.scan(any(Set.class), any(Set.class))).thenReturn(true);

        // start scan
        assertTrue(mScanner.startSingleScan(settings, eventHandler));
        mLooper.dispatchAll();

        // Fire timeout
        mAlarmManager.dispatch(SupplicantWifiScannerImpl.TIMEOUT_ALARM_TAG);
        mLooper.dispatchAll();

        order.verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_FAILED);

        verifyNoMoreInteractions(eventHandler);
    }

    /**
     * Test that a scan failure is reported if supplicant sends a scan failed event
     */
    @Test
    public void singleScanFailOnFailedEvent() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        ScanResults results = ScanResults.create(0, 2400, 2450, 2450);

        InOrder order = inOrder(eventHandler, mWifiNative);

        // scan succeeds
        when(mWifiNative.scan(any(Set.class), any(Set.class))).thenReturn(true);

        // start scan
        assertTrue(mScanner.startSingleScan(settings, eventHandler));
        mLooper.dispatchAll();

        // Fire failed event
        mWifiMonitor.sendMessage(mWifiNative.getInterfaceName(), WifiMonitor.SCAN_FAILED_EVENT);
        mLooper.dispatchAll();

        order.verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_FAILED);

        verifyNoMoreInteractions(eventHandler);
    }

    @Test
    public void singleScanNullEventHandler() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();
        assertFalse(mScanner.startSingleScan(settings, null));
    }

    @Test
    public void singleScanNullSettings() {
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        assertFalse(mScanner.startSingleScan(null, eventHandler));

        verifyNoMoreInteractions(eventHandler);
    }

    @Test
    public void multipleSingleScanSuccess() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();
        WifiNative.ScanSettings settings2 = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_5_GHZ)
                .build();

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        InOrder order = inOrder(eventHandler, mWifiNative);

        // scans succeed
        when(mWifiNative.scan(any(Set.class), any(Set.class))).thenReturn(true);

        // start first scan
        assertTrue(mScanner.startSingleScan(settings, eventHandler));

        expectSuccessfulSingleScan(order, eventHandler,
                expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ),
                new HashSet<Integer>(),
                ScanResults.create(0, 2400, 2450, 2450), false);

        // start second scan
        assertTrue(mScanner.startSingleScan(settings2, eventHandler));

        expectSuccessfulSingleScan(order, eventHandler,
                expectedBandScanFreqs(WifiScanner.WIFI_BAND_5_GHZ),
                new HashSet<Integer>(),
                ScanResults.create(0, 5150, 5175), false);

        verifyNoMoreInteractions(eventHandler);
    }

    /**
     * Validate that scan results that are returned from supplicant, which are timestamped prior to
     * the start of the scan, are ignored.
     */
    @Test
    public void singleScanWhereSupplicantReturnsSomeOldResults() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(2)
                .addBucketWithBand(10000,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                        | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        long approxScanStartUs = SystemClock.elapsedRealtime() * 1000;
        ArrayList<ScanDetail> rawResults = new ArrayList<>(Arrays.asList(
                        new ScanDetail(WifiSsid.createFromAsciiEncoded("TEST AP 1"),
                                "00:00:00:00:00:00", "", -70, 2450,
                                approxScanStartUs + 2000 * 1000, 0),
                        new ScanDetail(WifiSsid.createFromAsciiEncoded("TEST AP 2"),
                                "AA:BB:CC:DD:EE:FF", "", -66, 2400,
                                approxScanStartUs + 2500 * 1000, 0),
                        new ScanDetail(WifiSsid.createFromAsciiEncoded("TEST AP 3"),
                                "00:00:00:00:00:00", "", -80, 2450,
                                approxScanStartUs - 2000 * 1000, 0), // old result will be filtered
                        new ScanDetail(WifiSsid.createFromAsciiEncoded("TEST AP 4"),
                                "AA:BB:CC:11:22:33", "", -65, 2450,
                                approxScanStartUs + 4000 * 1000, 0)));

        ArrayList<ScanResult> fullResults = new ArrayList<>();
        for (ScanDetail detail : rawResults) {
            if (detail.getScanResult().timestamp > approxScanStartUs) {
                fullResults.add(detail.getScanResult());
            }
        }
        ArrayList<ScanResult> scanDataResults = new ArrayList<>(fullResults);
        Collections.sort(scanDataResults, ScanResults.SCAN_RESULT_RSSI_COMPARATOR);
        ScanData scanData = new ScanData(0, 0,
                scanDataResults.toArray(new ScanResult[scanDataResults.size()]));
        Set<Integer> expectedScan = expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ);

        // Actual test

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        InOrder order = inOrder(eventHandler, mWifiNative);

        // scan succeeds
        when(mWifiNative.scan(any(Set.class), any(Set.class))).thenReturn(true);

        // start scan
        assertTrue(mScanner.startSingleScan(settings, eventHandler));

        order.verify(mWifiNative).scan(eq(expectedScan), any(Set.class));

        when(mWifiNative.getScanResults()).thenReturn(rawResults);

        // Notify scan has finished
        mWifiMonitor.sendMessage(mWifiNative.getInterfaceName(), WifiMonitor.SCAN_RESULTS_EVENT);

        mLooper.dispatchAll();

        for (ScanResult result : fullResults) {
            order.verify(eventHandler).onFullScanResult(eq(result), eq(0));
        }

        order.verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        assertScanDataEquals(scanData, mScanner.getLatestSingleScanResults());

        verifyNoMoreInteractions(eventHandler);
    }

    @Test
    public void backgroundScanNullEventHandler() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();
        assertFalse(mScanner.startBatchedScan(settings, null));
    }

    @Test
    public void backgroundScanNullSettings() {
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        assertFalse(mScanner.startBatchedScan(null, eventHandler));

        verifyNoMoreInteractions(eventHandler);
    }

    protected void doSuccessfulSingleScanTest(WifiNative.ScanSettings settings,
            Set<Integer> expectedScan, Set<Integer> expectedHiddenNetIds, ScanResults results,
            boolean expectFullResults) {
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        InOrder order = inOrder(eventHandler, mWifiNative);

        // scan succeeds
        when(mWifiNative.scan(any(Set.class), any(Set.class))).thenReturn(true);

        // start scan
        assertTrue(mScanner.startSingleScan(settings, eventHandler));

        expectSuccessfulSingleScan(order, eventHandler, expectedScan, expectedHiddenNetIds,
                results, expectFullResults);

        verifyNoMoreInteractions(eventHandler);
    }

    protected void expectSuccessfulSingleScan(InOrder order,
            WifiNative.ScanEventHandler eventHandler, Set<Integer> expectedScan,
            Set<Integer> expectedHiddenNetIds, ScanResults results, boolean expectFullResults) {
        order.verify(mWifiNative).scan(eq(expectedScan), eq(expectedHiddenNetIds));

        when(mWifiNative.getScanResults()).thenReturn(results.getScanDetailArrayList());

        // Notify scan has finished
        mWifiMonitor.sendMessage(mWifiNative.getInterfaceName(), WifiMonitor.SCAN_RESULTS_EVENT);

        mLooper.dispatchAll();

        if (expectFullResults) {
            for (ScanResult result : results.getRawScanResults()) {
                order.verify(eventHandler).onFullScanResult(eq(result), eq(0));
            }
        }

        order.verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        assertScanDataEquals(results.getScanData(), mScanner.getLatestSingleScanResults());
    }
}