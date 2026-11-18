/*
 *  Copyright (C) 2017 OrionStar Technology Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.ainirobot.robotos.application;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.ainirobot.coreservice.client.ApiListener;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.module.ModuleCallbackApi;
import com.ainirobot.coreservice.client.speech.SkillApi;

import java.util.Locale;

public class RobotOSApplication extends Application {

    private static final String TAG = RobotOSApplication.class.getName();

    private Context mContext;
    private SkillApi mSkillApi;
    private TextToSpeech mTTS;

    private SpeechCallback mSkillCallback;
    private HandlerThread mApiCallbackThread;
    private ModuleCallbackApi mModuleCallback;
    private static RobotOSApplication mApplication;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        mApplication = this;
        init();
        initTTS();

        // Start WebSocket Bridge Server immediately on app launch
        try {
            com.ainirobot.robotos.bridge.RobotBridgeManager.getInstance().startBridge(mContext);
        } catch (Exception e) {
            Log.e(TAG, "Early bridge start failed: " + e.getMessage());
        }
        initRobotApi();
    }

    private void init() {
        mSkillCallback = new SpeechCallback();
        mModuleCallback = new ModuleCallback();
        mApiCallbackThread = new HandlerThread("RobotOSDemo");
        mApiCallbackThread.start();
    }

    private void initTTS() {
        try {
            mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS && mTTS != null) {
                        int res = mTTS.setLanguage(new Locale("vi", "VN"));
                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                            mTTS.setLanguage(Locale.US);
                        }
                        Log.i(TAG, "Android native TextToSpeech initialized successfully");
                    }
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "Failed to init TextToSpeech: " + t.getMessage());
        }
    }

    public TextToSpeech getTTS() {
        return mTTS;
    }

    public static RobotOSApplication getInstance() {
        return mApplication;
    }

    private void initRobotApi() {
        RobotApi.getInstance().connectServer(mContext, new ApiListener() {
            @Override
            public void handleApiDisabled() {
                Log.i(TAG, "handleApiDisabled");
            }

            @Override
            public void handleApiConnected() {
                Log.i(TAG, "handleApiConnected");
                addApiCallBack();
                initSkillApi();
                // Start Le Hoang Robotics Bridge server for PC control
                com.ainirobot.robotos.bridge.RobotBridgeManager.getInstance().startBridge(mContext);

                // Tự động nhận diện bản đồ hiện tại từ CoreService
                try {
                    RobotApi.getInstance().getMapName(0, new com.ainirobot.coreservice.client.listener.CommandListener() {
                        @Override
                        public void onResult(int result, String message, String extraData) {
                            if (!android.text.TextUtils.isEmpty(message)) {
                                Log.i(TAG, "Active CoreService map detected on start: " + message);
                                if (com.ainirobot.robotos.maputils.GlobalData.getInstance() != null) {
                                    com.ainirobot.robotos.maputils.GlobalData.getInstance().setCurrentMapName(message);
                                }
                                com.ainirobot.robotos.bridge.RobotMapManager.notifyMapPlacesChanged(message);
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }

            @Override
            public void handleApiDisconnected() {
                Log.i(TAG, "handleApiDisconnected");
                com.ainirobot.robotos.bridge.RobotBridgeManager.getInstance().stopBridge();
            }
        });
    }

    private void addApiCallBack() {
        Log.d(TAG, "CoreService connected ");
        RobotApi.getInstance().setCallback(mModuleCallback);
        RobotApi.getInstance().setResponseThread(mApiCallbackThread);
    }

    private void initSkillApi() {
        try {
            mSkillApi = new SkillApi();
            ApiListener apiListener = new ApiListener() {
                @Override
                public void handleApiDisabled() {
                    Log.w(TAG, "SkillApi disabled");
                }

                @Override
                public void handleApiConnected() {
                    Log.i(TAG, "SkillApi connected!");
                    if (mSkillApi != null) {
                        mSkillApi.registerCallBack(mSkillCallback);
                    }
                }

                @Override
                public void handleApiDisconnected() {
                    Log.w(TAG, "SkillApi disconnected");
                }
            };
            mSkillApi.addApiEventListener(apiListener);

            Context speechCtx = new ContextWrapper(mContext) {
                @Override
                public String getPackageName() {
                    return "com.ainirobot.robotos";
                }
            };
            mSkillApi.connectApi(speechCtx, apiListener);
        } catch (Throwable t) {
            Log.e(TAG, "initSkillApi error: " + t.getMessage());
        }
    }

    public SkillApi getSkillApi() {
        if (mSkillApi != null && mSkillApi.isApiConnectedService()) {
            return mSkillApi;
        }
        return null;
    }
}
