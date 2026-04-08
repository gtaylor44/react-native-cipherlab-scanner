package au.com.micropacific.react.cipherlab;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.widget.Toast;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import java.util.Map;
import java.util.HashMap;
import java.io.StringWriter;
import java.io.PrintWriter;

import javax.annotation.Nullable;

import com.cipherlab.barcode.*;
import com.cipherlab.barcode.decoder.*;
import com.cipherlab.barcode.decoderparams.*;
import com.cipherlab.barcodebase.*;

import au.com.micropacific.react.cipherlab.IModuleCounter;

public class CipherLabScannerModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext _reactContext;

    public static final String DEBUG_TAG = "CipherLabScanner";
    public static final String EVENT_TAG = "CIPHERLAB";

    private static final String DURATION_SHORT_KEY = "SHORT";
    private static final String DURATION_LONG_KEY = "LONG";

    private ReaderManager mReaderManager;
    private DataReceiver mDataReceiver;
    private Activity activity;
    private static BcReaderType mReaderType;

    public static IModuleCounter MainActivity;

    public CipherLabScannerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this._reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "CipherLabScannerModule";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put(DURATION_SHORT_KEY, Toast.LENGTH_SHORT);
        constants.put(DURATION_LONG_KEY, Toast.LENGTH_LONG);
        return constants;
    }

    @ReactMethod
    public void show(String message, int duration) {
        Toast.makeText(getReactApplicationContext(), message, duration).show();
    }

    /**
     * Bridgeless-safe event emitter
     */
    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        if (reactContext == null || !reactContext.hasActiveReactInstance()) {
            Log.v(DEBUG_TAG, "Skipping event, React instance not active");
            return;
        }

        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(EVENT_TAG + "." + eventName, params);
    }

    private IntentFilter filter = null;

    @ReactMethod
    public void initialise() {
        initialise(true);
    }

    public void initialise(boolean checkRegisteredModules) {

        Log.v(DEBUG_TAG, "CipherLabScanner.initialise()");

        if (checkRegisteredModules && MainActivity != null && MainActivity.getModuleSize() > 0) {
            Log.v(DEBUG_TAG, "Module already registered, deferring");
            initCallback();
            return;
        }

        sendEvent(_reactContext, "setupStarted", null);

        try {
            this.activity = getCurrentActivity();

            if (this.activity == null) {
                Log.v(DEBUG_TAG, "getCurrentActivity() is null");
                return;
            }

            filter = new IntentFilter();
            filter.addAction(GeneralString.Intent_SOFTTRIGGER_DATA);
            filter.addAction(GeneralString.Intent_PASS_TO_APP);
            filter.addAction(GeneralString.Intent_READERSERVICE_CONNECTED);
            filter.addAction(GeneralString.Intent_DECODE_ERROR);

            mDataReceiver = new DataReceiver(this, null, _reactContext, checkRegisteredModules);
            this.activity.registerReceiver(mDataReceiver, filter);

            mReaderManager = ReaderManager.InitInstance(this.activity);

            if (mReaderManager != null) {
                mDataReceiver.setReaderManager(mReaderManager);
                mReaderType = mReaderManager.GetReaderType();
                Log.v(DEBUG_TAG, "Got reader manager: " + mReaderType);
            } else {
                Log.v(DEBUG_TAG, "Null ReaderManager, no scanner?");
            }

        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            printWriter.flush();
            Log.v(DEBUG_TAG, "Error starting reader manager: " + writer.toString());
        }

        if (MainActivity != null && MainActivity.getModuleSize() == 0) {
            MainActivity.registerModule(this);
        }

        Log.v(DEBUG_TAG, "CipherLabScanner.initialise() Done");
    }

    public void initCallback() {
        Log.v(DEBUG_TAG, "initCallback");
        sendEvent(_reactContext, "initEvent", null);
    }

    public void receiveData(String barcode, int barcodeTypeInt, byte[] binary) {

        BcDecodeType barcodeType = BcDecodeType.valueOf(barcodeTypeInt);
        barcode = barcode.replace("\n", "");

        WritableMap params = Arguments.createMap();
        params.putString("barcode", barcode);
        params.putString("type",
            barcodeType.name().toUpperCase()
                .replace("CODE_128", "CODE128")
                .replace("CODE_39", "CODE39")
        );

        if (enableBinary) {
            WritableArray integers = Arguments.createArray();
            for (byte b : binary) {
                integers.pushInt(b & 0xFF);
            }
            params.putArray("binary", integers);
        }

        Log.v(DEBUG_TAG, "barcodeReadEvent(" + barcode + ")");
        sendEvent(_reactContext, "barcodeReadEvent", params);
    }

    public void receiveIntent(Intent intent) {
        String action = intent.getAction();

        WritableMap params = Arguments.createMap();
        params.putString("action", action);

        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            for (String key : bundle.keySet()) {
                Object value = bundle.get(key);
                params.putString(key, value != null ? value.toString() : "null");
            }
        }

        sendEvent(_reactContext, "receiveIntent", params);
    }

    @ReactMethod
    public void requestScan() {
        if (mReaderManager != null) {
            mReaderManager.SoftScanTrigger();
        }
    }

    private boolean enableBinary = false;

    @ReactMethod
    public void enableBinaryData() {
        enableBinary = true;
    }

    @ReactMethod
    public void disableBinaryData() {
        enableBinary = false;
    }

    @ReactMethod
    public void releaseResources(boolean releaseReaderManager) {

        try {
            if (mDataReceiver != null && activity != null) {
                activity.unregisterReceiver(mDataReceiver);
            }

            if (releaseReaderManager && mReaderManager != null) {
                mReaderManager.Release();
            }

        } catch (Exception e) {
            Log.v(DEBUG_TAG, "Error in releaseResources: " + e.toString());
        }

        mReaderManager = null;
        mDataReceiver = null;
        activity = null;

        Log.v(DEBUG_TAG, "releaseResources() done.");
    }

    private static final String READER_TYPE = "ReaderType";

    public void onSaveInstanceState(Bundle outState) {
        if (mReaderType != null) {
            outState.putInt(READER_TYPE, mReaderType.getValue());
        }
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            int current = savedInstanceState.getInt(READER_TYPE);
            Log.v(DEBUG_TAG, "onRestoreInstanceState = " + current);
        }
    }
}