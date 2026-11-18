package com.ainirobot.robotos.fragment;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;

import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.ainirobot.coreservice.client.listener.CommandListener;
import com.ainirobot.coreservice.client.robotsetting.RobotSettingApi;
import com.ainirobot.robotos.MainActivity;
import com.ainirobot.robotos.R;
import com.ainirobot.robotos.bridge.RobotBridgeManager;
import com.ainirobot.robotos.bridge.RobotBridgeServer;
import com.ainirobot.robotos.bridge.RobotMapManager;
import com.ainirobot.robotos.bridge.RobotPosePersistenceManager;
import com.ainirobot.robotos.bridge.TTSPlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DeliveryFragment extends Fragment {

    private static final String TAG = "DeliveryFragment";

    // 3 Tray Slots (Tầng 1, Tầng 2, Tầng 3)
    public static class TraySlot {
        public int number;
        public String destination = "";

        public TraySlot(int number) {
            this.number = number;
        }

        public boolean hasDestination() {
            return !TextUtils.isEmpty(destination);
        }
    }

    private final TraySlot[] mTraySlots = new TraySlot[] {
            new TraySlot(1),
            new TraySlot(2),
            new TraySlot(3)
    };

    private int mActiveTray = 1; // 1, 2, or 3
    private String mCurrentMap = "tăng 2-1110154527";
    private final List<String> mStationsList = new ArrayList<>();

    // Listener for map changes triggered by Desktop PC
    private final RobotMapManager.OnMapPlacesChangedListener mMapPlacesListener = new RobotMapManager.OnMapPlacesChangedListener() {
        @Override
        public void onPlacesChanged(final String mapName) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "Map places changed (" + mapName + "), reloading stations in DeliveryFragment...");
                        loadStationsFromMap();
                    }
                });
            }
        }
    };

    private BroadcastReceiver mBatteryReceiver;

    // 1. Delivery Screen Views
    private View mPanelDeliveryConfig;
    private TextView mBtnMenuDots;
    private TextView mBtnLocationFloor;
    private TextView mTvLocalizationStatus;
    private TextView mBtnChargeDock;
    private TextView mBtnLanguage;
    private TextView mBtnDishCollection;
    private TextView mTvBadgeTray1;
    private TextView mTvBadgeTray2;
    private TextView mTvBadgeTray3;
    private TextView mTvHeaderDestinations;

    // 8. Language Selection Screen Views
    private LinearLayout mPanelLanguageSelect;
    private TextView mBtnLangBack;
    private TextView mTvLangTitle;
    private TextView mTvLangClock;
    private TextView mTvLangBattery;
    private RelativeLayout mLangItemVi;
    private RelativeLayout mLangItemEn;
    private RelativeLayout mLangItemZh;
    private RelativeLayout mLangItemZht;
    private RelativeLayout mLangItemJa;
    private RelativeLayout mLangItemKo;
    private RelativeLayout mLangItemTh;
    private RelativeLayout mLangItemId;
    private RelativeLayout mLangItemDe;
    private TextView mIcCheckVi;
    private TextView mIcCheckEn;
    private TextView mIcCheckZh;

    // 9. Table Clearing / Busboy Mode Views
    private RelativeLayout mPanelCollectionMode;
    private TextView mBtnCollectionMenuDots;
    private TextView mTvCollectionClock;
    private TextView mTvCollectionBattery;
    private Button mBtnCollectionLocal;
    private Button mBtnCollectionWashroom;

    private static final String PREF_KEY_LANGUAGE_IS_EN = "pref_key_language_is_en";
    private boolean mIsEnglish = false;
    private TextView mBtnModeTray;
    private TextView mTvClockTime;
    private TextView mTvBatteryPill;
    private TextView mBtnRefreshStations;

    private RelativeLayout mShelfTray1;
    private TextView mTvTray1Status;
    private TextView mBtnClearTray1;

    private RelativeLayout mShelfTray2;
    private TextView mTvTray2Status;
    private TextView mBtnClearTray2;

    private RelativeLayout mShelfTray3;
    private TextView mTvTray3Status;
    private TextView mBtnClearTray3;

    private TextView mBtnTrayFloatingBell;
    private LinearLayout mContainerStationButtons;
    private Button mBtnSlowDelivery;
    private Button mBtnFastDelivery;

    // 2. Left Slide-In Drawer Views
    private RelativeLayout mPanelLeftDrawer;
    private View mViewDrawerScrim;
    private TextView mBtnDrawerPower;
    private TextView mBtnDrawerClose;
    private RelativeLayout mBtnMenuReturnHome;
    private RelativeLayout mBtnMenuRelocalize;
    private RelativeLayout mBtnMenuSwitchMode;
    private RelativeLayout mBtnMenuSettings;
    private RelativeLayout mBtnMenuMusic;
    private RelativeLayout mBtnMenuHistory;
    private TextView mTvMusicTitle;
    private TextView mBtnMusicSwitch;
    private CountDownTimer mDrawerTimer;

    // 3. Mode Switch Screen Views
    private LinearLayout mPanelModeSwitch;
    private TextView mBtnModeBack;
    private LinearLayout mModeBtnTransport;
    private LinearLayout mModeBtnPromo;
    private LinearLayout mModeBtnCruise;
    private LinearLayout mModeBtnCollection;

    // 4. Settings Screen Views
    private LinearLayout mPanelSettings;
    private TextView mBtnSettingsBack;
    private RelativeLayout mRowSpeedFast;
    private TextView mTvValSpeedFast;
    private RelativeLayout mRowSpeedWater;
    private TextView mTvValSpeedWater;
    private RelativeLayout mRowSpeedReturn;
    private TextView mTvValSpeedReturn;
    private RelativeLayout mRowDestCategories;
    private RelativeLayout mRowStartPoint;
    private RelativeLayout mRowStopScreenTime;
    private TextView mTvValStopConfirm;
    private RelativeLayout mRowTableWaitTime;
    private TextView mTvValTableWaitTime;
    private RelativeLayout mRowBroadcastInterval;
    private TextView mTvValBroadcastInterval;
    private Button mBtnOpenDevTools;

    // 6. Minimalist Cruise (Du thuyền) Mode Views & State
    private TextView mTagUsingTransport;
    private TextView mTagUsingCruise;
    private LinearLayout mPanelCruiseConfig;
    private TextView mBtnCruiseBack;
    private TextView mTvCruiseModeBadge;
    private TextView mBtnCruiseSelectAll;
    private TextView mBtnCruiseClearAll;
    private LinearLayout mContainerCruiseStations;
    private TextView mTvCruiseRouteSummary;
    private TextView mBtnCruiseDwell10s;
    private TextView mBtnCruiseDwell20s;
    private TextView mBtnCruiseDwell30s;
    private TextView mBtnCruiseLoop1;
    private TextView mBtnCruiseLoop2;
    private TextView mBtnCruiseLoopInfinite;
    private Button mBtnStartCruise;

    // 7. Active Cruising Overlay Views
    private LinearLayout mOverlayCruising;
    private TextView mTvCruiseActiveTitle;
    private TextView mTvCruiseActiveLoopInfo;
    private TextView mTvCruiseActiveCurrentDest;
    private LinearLayout mPanelCruiseArrival;
    private TextView mTvCruiseArrivalStation;
    private TextView mTvCruiseCountdown;
    private Button mBtnCruiseNext;
    private Button mBtnCruisePauseResume;
    private Button mBtnCruiseFinish;

    // Cruise Engine State
    private static final int MODE_DELIVERY = 1;
    private static final int MODE_CRUISE = 2;
    private static final int MODE_PROMO = 3;
    private static final int MODE_COLLECTION = 4;
    private int mCurrentActiveMode = MODE_DELIVERY;

    private final List<String> mCruiseSelectedStations = new ArrayList<>();
    private int mCruiseDwellTimeSec = 20;
    private int mCruiseTotalLoops = 1; // -1 for infinite
    private int mCruiseCurrentLoop = 1;
    private int mCruiseCurrentIndex = 0;
    private float mCruiseSpeed = 0.6f;
    private String mCruiseGreetingSpeech = "Xin chào quý khách, mời quý khách tự nhiên dùng nước!";
    private boolean mIsCruising = false;
    private boolean mIsCruisePaused = false;
    private CountDownTimer mCruiseArrivalTimer;

    // 5. Delivering In-Progress Overlay Views
    private LinearLayout mOverlayDelivering;
    private TextView mTvRobotEyeLeft;
    private TextView mTvRobotEyeRight;
    private TextView mTvDeliveryTitle;
    private TextView mTvDeliveryCurrentTarget;
    private TextView mTvDeliveryTrayInfo;
    private TextView mTvDeliveryArrivalPrompt;
    private Button mBtnConfirmTaken;
    private Button mBtnAbortDelivery;

    // Delivery Process State
    private boolean mIsDelivering = false;
    private boolean mIsReturningHome = false;
    private final List<TraySlot> mActiveDeliveryQueue = new ArrayList<>();
    private int mCurrentQueueIndex = 0;
    private CountDownTimer mArrivalTimer;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private float mCurrentSpeed = 0.5f;

    // Configurable Settings Values
    private float mSpeedFast = 0.7f;
    private float mSpeedWater = 0.5f;
    private float mSpeedReturn = 0.6f;
    private int mTableWaitTimeSec = 30;

    // Localization State & Monitor
    private boolean mIsEstimateValid = false;
    private Runnable mLocalizationCheckerRunnable;

    public static DeliveryFragment newInstance() {
        return new DeliveryFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_delivery, container, false);
        bindViews(root);
        updateClock();
        updateBatteryStatus();
        loadStationsFromMap();
        refreshTrayShelvesUi();

        // Đăng ký lắng nghe thay đổi điểm bản đồ từ Desktop PC
        RobotMapManager.addOnMapPlacesChangedListener(mMapPlacesListener);

        // Đăng ký BroadcastReceiver theo dõi dung lượng pin thực tế
        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateBatteryStatus();
            }
        };
        try {
            if (getContext() != null) {
                IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                getContext().registerReceiver(mBatteryReceiver, filter);
            }
        } catch (Throwable ignored) {
        }

        // Khởi tạo Pose Persistence Manager & Bắt đầu tự động theo dõi vị trí
        if (getContext() != null) {
            RobotPosePersistenceManager.init(getContext());
            RobotPosePersistenceManager.getInstance().startPeriodicTracking();
        }

        // Delay 1.5s để CoreService ổn định, sau đó tự động khôi phục vị trí (Cách 1)
        // và bật giám sát định vị
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                performAutoPoseRestore();
                startLocalizationMonitor();
            }
        }, 1500);

        return root;
    }

    public void switchFragment(Fragment fragment) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).switchFragment(fragment);
        }
    }

    private void bindViews(View root) {
        // Main Screen
        mPanelDeliveryConfig = root.findViewById(R.id.panel_delivery_config);
        mBtnMenuDots = root.findViewById(R.id.btn_menu_dots);
        mBtnLocationFloor = root.findViewById(R.id.btn_location_floor);
        mTvLocalizationStatus = root.findViewById(R.id.tv_localization_status);
        mBtnChargeDock = root.findViewById(R.id.btn_charge_dock);
        mBtnLanguage = root.findViewById(R.id.btn_language);
        mBtnDishCollection = root.findViewById(R.id.btn_dish_collection);
        mBtnModeTray = root.findViewById(R.id.btn_mode_tray);
        mTvBadgeTray1 = root.findViewById(R.id.tv_badge_tray_1);
        mTvBadgeTray2 = root.findViewById(R.id.tv_badge_tray_2);
        mTvBadgeTray3 = root.findViewById(R.id.tv_badge_tray_3);
        mTvHeaderDestinations = root.findViewById(R.id.tv_header_destinations);

        // Language Select Overlay Views
        mPanelLanguageSelect = root.findViewById(R.id.panel_language_select);
        mBtnLangBack = root.findViewById(R.id.btn_lang_back);
        mTvLangTitle = root.findViewById(R.id.tv_lang_title);
        mTvLangClock = root.findViewById(R.id.tv_lang_clock);
        mTvLangBattery = root.findViewById(R.id.tv_lang_battery);
        mLangItemVi = root.findViewById(R.id.lang_item_vi);
        mLangItemEn = root.findViewById(R.id.lang_item_en);
        mLangItemZh = root.findViewById(R.id.lang_item_zh);
        mLangItemZht = root.findViewById(R.id.lang_item_zht);
        mLangItemJa = root.findViewById(R.id.lang_item_ja);
        mLangItemKo = root.findViewById(R.id.lang_item_ko);
        mLangItemTh = root.findViewById(R.id.lang_item_th);
        mLangItemId = root.findViewById(R.id.lang_item_id);
        mLangItemDe = root.findViewById(R.id.lang_item_de);
        mIcCheckVi = root.findViewById(R.id.ic_check_vi);
        mIcCheckEn = root.findViewById(R.id.ic_check_en);
        mIcCheckZh = root.findViewById(R.id.ic_check_zh);

        // Collection Mode Views
        mPanelCollectionMode = root.findViewById(R.id.panel_collection_mode);
        mBtnCollectionMenuDots = root.findViewById(R.id.btn_collection_menu_dots);
        mTvCollectionClock = root.findViewById(R.id.tv_collection_clock);
        mTvCollectionBattery = root.findViewById(R.id.tv_collection_battery);
        mBtnCollectionLocal = root.findViewById(R.id.btn_collection_local);
        mBtnCollectionWashroom = root.findViewById(R.id.btn_collection_washroom);

        try {
            if (getContext() != null) {
                SharedPreferences sp = getContext().getSharedPreferences("lehoang_prefs", Context.MODE_PRIVATE);
                mIsEnglish = sp.getBoolean(PREF_KEY_LANGUAGE_IS_EN, false);
            }
        } catch (Throwable ignored) {
        }
        mTvClockTime = root.findViewById(R.id.tv_clock_time);
        mTvBatteryPill = root.findViewById(R.id.tv_battery_pill);
        mBtnRefreshStations = root.findViewById(R.id.btn_refresh_stations);

        if (mBtnRefreshStations != null) {
            mBtnRefreshStations.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadStationsFromMap();
                    updateBatteryStatus();
                    Toast.makeText(getContext(), "Đã tải lại danh sách điểm bàn!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        mShelfTray1 = root.findViewById(R.id.shelf_tray_1);
        mTvTray1Status = root.findViewById(R.id.tv_tray_1_status);
        mBtnClearTray1 = root.findViewById(R.id.btn_clear_tray_1);

        mShelfTray2 = root.findViewById(R.id.shelf_tray_2);
        mTvTray2Status = root.findViewById(R.id.tv_tray_2_status);
        mBtnClearTray2 = root.findViewById(R.id.btn_clear_tray_2);

        mShelfTray3 = root.findViewById(R.id.shelf_tray_3);
        mTvTray3Status = root.findViewById(R.id.tv_tray_3_status);
        mBtnClearTray3 = root.findViewById(R.id.btn_clear_tray_3);

        mBtnTrayFloatingBell = root.findViewById(R.id.btn_tray_floating_bell);
        mContainerStationButtons = root.findViewById(R.id.container_station_buttons);
        mBtnSlowDelivery = root.findViewById(R.id.btn_slow_delivery);
        mBtnFastDelivery = root.findViewById(R.id.btn_fast_delivery);

        // Drawer
        mPanelLeftDrawer = root.findViewById(R.id.panel_left_drawer);
        mViewDrawerScrim = root.findViewById(R.id.view_drawer_scrim);
        mBtnDrawerPower = root.findViewById(R.id.btn_drawer_power);
        mBtnDrawerClose = root.findViewById(R.id.btn_drawer_close);
        mBtnMenuReturnHome = root.findViewById(R.id.btn_menu_return_home);
        mBtnMenuRelocalize = root.findViewById(R.id.btn_menu_relocalize);
        mBtnMenuSwitchMode = root.findViewById(R.id.btn_menu_switch_mode);
        mBtnMenuSettings = root.findViewById(R.id.btn_menu_settings);
        mBtnMenuMusic = root.findViewById(R.id.btn_menu_music);
        mBtnMenuHistory = root.findViewById(R.id.btn_menu_history);
        mTvMusicTitle = root.findViewById(R.id.tv_music_title);
        mBtnMusicSwitch = root.findViewById(R.id.btn_music_switch);

        // Mode Switch
        mPanelModeSwitch = root.findViewById(R.id.panel_mode_switch);
        mBtnModeBack = root.findViewById(R.id.btn_mode_back);
        mModeBtnTransport = root.findViewById(R.id.mode_btn_transport);
        mModeBtnPromo = root.findViewById(R.id.mode_btn_promo);
        mModeBtnCruise = root.findViewById(R.id.mode_btn_cruise);
        mModeBtnCollection = root.findViewById(R.id.mode_btn_collection);

        // Settings
        mPanelSettings = root.findViewById(R.id.panel_settings);
        mBtnSettingsBack = root.findViewById(R.id.btn_settings_back);
        mRowSpeedFast = root.findViewById(R.id.row_speed_fast);
        mTvValSpeedFast = root.findViewById(R.id.tv_val_speed_fast);
        mRowSpeedWater = root.findViewById(R.id.row_speed_water);
        mTvValSpeedWater = root.findViewById(R.id.tv_val_speed_water);
        mRowSpeedReturn = root.findViewById(R.id.row_speed_return);
        mTvValSpeedReturn = root.findViewById(R.id.tv_val_speed_return);
        mRowDestCategories = root.findViewById(R.id.row_dest_categories);
        mRowStartPoint = root.findViewById(R.id.row_start_point);
        mRowStopScreenTime = root.findViewById(R.id.row_stop_screen_time);
        mTvValStopConfirm = root.findViewById(R.id.tv_val_stop_confirm);
        mRowTableWaitTime = root.findViewById(R.id.row_table_wait_time);
        mTvValTableWaitTime = root.findViewById(R.id.tv_val_table_wait_time);
        mRowBroadcastInterval = root.findViewById(R.id.row_broadcast_interval);
        mTvValBroadcastInterval = root.findViewById(R.id.tv_val_broadcast_interval);
        mBtnOpenDevTools = root.findViewById(R.id.btn_open_dev_tools);

        // Delivering Overlay
        mOverlayDelivering = root.findViewById(R.id.overlay_delivering);
        mTvRobotEyeLeft = root.findViewById(R.id.tv_robot_eye_left);
        mTvRobotEyeRight = root.findViewById(R.id.tv_robot_eye_right);
        mTvDeliveryTitle = root.findViewById(R.id.tv_delivery_title);
        mTvDeliveryCurrentTarget = root.findViewById(R.id.tv_delivery_current_target);
        mTvDeliveryTrayInfo = root.findViewById(R.id.tv_delivery_tray_info);
        mTvDeliveryArrivalPrompt = root.findViewById(R.id.tv_delivery_arrival_prompt);
        mBtnConfirmTaken = root.findViewById(R.id.btn_confirm_taken);

        // Bind Minimalist Cruise & Mode Switch views
        mTagUsingTransport = root.findViewById(R.id.tag_using_transport);
        mTagUsingCruise = root.findViewById(R.id.tag_using_cruise);
        mPanelCruiseConfig = root.findViewById(R.id.panel_cruise_config);
        mBtnCruiseBack = root.findViewById(R.id.btn_cruise_back);
        mTvCruiseModeBadge = root.findViewById(R.id.tv_cruise_mode_badge);
        mBtnCruiseSelectAll = root.findViewById(R.id.btn_cruise_select_all);
        mBtnCruiseClearAll = root.findViewById(R.id.btn_cruise_clear_all);
        mContainerCruiseStations = root.findViewById(R.id.container_cruise_station_buttons);
        mTvCruiseRouteSummary = root.findViewById(R.id.tv_cruise_route_summary);
        mBtnCruiseDwell10s = root.findViewById(R.id.btn_cruise_dwell_10s);
        mBtnCruiseDwell20s = root.findViewById(R.id.btn_cruise_dwell_20s);
        mBtnCruiseDwell30s = root.findViewById(R.id.btn_cruise_dwell_30s);
        mBtnCruiseLoop1 = root.findViewById(R.id.btn_cruise_loop_1);
        mBtnCruiseLoop2 = root.findViewById(R.id.btn_cruise_loop_2);
        mBtnCruiseLoopInfinite = root.findViewById(R.id.btn_cruise_loop_infinite);
        mBtnStartCruise = root.findViewById(R.id.btn_start_cruise);

        mOverlayCruising = root.findViewById(R.id.overlay_cruising);
        mTvCruiseActiveTitle = root.findViewById(R.id.tv_cruise_active_title);
        mTvCruiseActiveLoopInfo = root.findViewById(R.id.tv_cruise_active_loop_info);
        mTvCruiseActiveCurrentDest = root.findViewById(R.id.tv_cruise_active_current_dest);
        mPanelCruiseArrival = root.findViewById(R.id.panel_cruise_arrival);
        mTvCruiseArrivalStation = root.findViewById(R.id.tv_cruise_arrival_station);
        mTvCruiseCountdown = root.findViewById(R.id.tv_cruise_countdown);
        mBtnCruiseNext = root.findViewById(R.id.btn_cruise_next);
        mBtnCruisePauseResume = root.findViewById(R.id.btn_cruise_pause_resume);
        mBtnCruiseFinish = root.findViewById(R.id.btn_cruise_finish);

        setupCruiseControls();
        mBtnAbortDelivery = root.findViewById(R.id.btn_abort_delivery);

        setupClickListeners();
        applyLanguage(mIsEnglish, false);
    }

    private void setupClickListeners() {
        // Shelf Tray Clicks
        mShelfTray1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActiveTray = 1;
                refreshTrayShelvesUi();
            }
        });

        mShelfTray2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActiveTray = 2;
                refreshTrayShelvesUi();
            }
        });

        mShelfTray3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActiveTray = 3;
                refreshTrayShelvesUi();
            }
        });

        // Clear buttons on shelves
        mBtnClearTray1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTraySlots[0].destination = "";
                mActiveTray = 1;
                refreshTrayShelvesUi();
            }
        });

        mBtnClearTray2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTraySlots[1].destination = "";
                mActiveTray = 2;
                refreshTrayShelvesUi();
            }
        });

        mBtnClearTray3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTraySlots[2].destination = "";
                mActiveTray = 3;
                refreshTrayShelvesUi();
            }
        });

        // Top Menu Actions
        mBtnMenuDots.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openLeftDrawer();
            }
        });

        // Left Action Strip: Charge Dock, Language, Dish Collection
        if (mBtnChargeDock != null) {
            mBtnChargeDock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAutoChargeDialog();
                }
            });
        }

        if (mBtnLanguage != null) {
            mBtnLanguage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mPanelDeliveryConfig != null) mPanelDeliveryConfig.setVisibility(View.GONE);
                    if (mPanelLanguageSelect != null) mPanelLanguageSelect.setVisibility(View.VISIBLE);
                }
            });
        }

        if (mBtnLangBack != null) {
            mBtnLangBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mPanelLanguageSelect != null) mPanelLanguageSelect.setVisibility(View.GONE);
                    if (mPanelDeliveryConfig != null) mPanelDeliveryConfig.setVisibility(View.VISIBLE);
                }
            });
        }

        setupLanguageSelectionHandlers();

        if (mBtnDishCollection != null) {
            mBtnDishCollection.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchActiveMode(MODE_COLLECTION);
                }
            });
        }

        setupCollectionModeHandlers();

        if (mBtnLocationFloor != null) {
            mBtnLocationFloor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showFloorSwitchDialog();
                }
            });
            mBtnLocationFloor.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showQuickRelocalizeDialog();
                    return true;
                }
            });
        }

        if (mTvLocalizationStatus != null) {
            mTvLocalizationStatus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showQuickRelocalizeDialog();
                }
            });
        }

        if (mBtnModeTray != null) {
            mBtnModeTray.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(getContext(), "Chế độ giao hàng đa điểm 3 tầng", Toast.LENGTH_SHORT).show();
                }
            });
        }

        mBtnTrayFloatingBell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TTSPlayer.playVietnameseText("Phục vụ khách hàng", null);
            }
        });

        // Delivery Action Buttons
        mBtnSlowDelivery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float spd = (mSpeedWater > 0.1f) ? mSpeedWater : 0.5f;
                Log.i(TAG, "User clicked Giao hàng chậm, speed=" + spd);
                startDelivery(spd, mIsEnglish ? "Slow Delivery (Liquids/Soup)" : "Giao hàng chậm (Món nước/Súp)");
            }
        });

        mBtnFastDelivery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float spd = (mSpeedFast > 0.1f) ? mSpeedFast : 0.7f;
                Log.i(TAG, "User clicked Giao hàng nhanh, speed=" + spd);
                startDelivery(spd, mIsEnglish ? "Fast Delivery" : "Giao hàng nhanh");
            }
        });

        mBtnConfirmTaken.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCustomerTakenFood();
            }
        });

        mBtnAbortDelivery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abortDelivery();
            }
        });

        // Drawer Actions
        mViewDrawerScrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeLeftDrawer();
            }
        });

        mBtnDrawerClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeLeftDrawer();
            }
        });

        mBtnDrawerPower.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPowerDialog();
            }
        });

        mBtnMenuReturnHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeLeftDrawer();
                String home = findStandbyStation();
                if (mIsEnglish) {
                    TTSPlayer.playEnglishText("Returning robot to " + home, null);
                } else {
                    TTSPlayer.playVietnameseText("Gửi robot trở về " + home, null);
                }
                mIsReturningHome = true;
                mCurrentSpeed = (mSpeedReturn > 0.1f) ? mSpeedReturn : 0.6f;
                navigateRobotTo(home);
            }
        });

        if (mBtnMenuRelocalize != null) {
            mBtnMenuRelocalize.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeLeftDrawer();
                    showQuickRelocalizeDialog();
                }
            });
        }

        mBtnMenuSwitchMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeLeftDrawer();
                mPanelDeliveryConfig.setVisibility(View.GONE);
                mPanelModeSwitch.setVisibility(View.VISIBLE);
            }
        });

        mBtnMenuSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeLeftDrawer();
                mPanelDeliveryConfig.setVisibility(View.GONE);
                mPanelSettings.setVisibility(View.VISIBLE);
            }
        });

        mBtnMenuMusic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMusicSelection();
            }
        });

        mBtnMusicSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMusicSelection();
            }
        });

        mBtnMenuHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "Lịch sử giao: Đã hoàn tất 12 chuyến an toàn hôm nay", Toast.LENGTH_LONG)
                        .show();
            }
        });

        // Mode Switch Screen Actions
        mBtnModeBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPanelModeSwitch.setVisibility(View.GONE);
                mPanelDeliveryConfig.setVisibility(View.VISIBLE);
            }
        });

        mModeBtnTransport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchActiveMode(MODE_DELIVERY);
            }
        });

        mModeBtnPromo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchActiveMode(MODE_PROMO);
            }
        });

        mModeBtnCruise.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchActiveMode(MODE_CRUISE);
            }
        });

        mModeBtnCollection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchActiveMode(MODE_COLLECTION);
            }
        });

        // Settings Screen Actions
        mBtnSettingsBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPanelSettings.setVisibility(View.GONE);
                mPanelDeliveryConfig.setVisibility(View.VISIBLE);
            }
        });

        mRowSpeedFast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSpeedFastDialog();
            }
        });

        mRowSpeedWater.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSpeedWaterDialog();
            }
        });

        mRowSpeedReturn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSpeedReturnDialog();
            }
        });

        mRowTableWaitTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTableWaitTimeDialog();
            }
        });

        mRowDestCategories.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStationsListDialog();
            }
        });

        mRowStartPoint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "Điểm xuất phát: Stand-by Point", Toast.LENGTH_SHORT).show();
            }
        });

        mRowStopScreenTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "Thời gian dừng khi chạm màn hình: 5 giây", Toast.LENGTH_SHORT).show();
            }
        });

        mRowBroadcastInterval.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "Khoảng thời gian phát sóng: 15 giây", Toast.LENGTH_SHORT).show();
            }
        });

        mBtnOpenDevTools.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchFragment(MainFragment.newInstance());
            }
        });
    }

    private void updateClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = sdf.format(new Date());
        if (mTvClockTime != null) mTvClockTime.setText(timeStr);
        if (mTvLangClock != null) mTvLangClock.setText(timeStr);
        if (mTvCollectionClock != null) mTvCollectionClock.setText(timeStr);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateClock();
        updateBatteryStatus();
        loadStationsFromMap();
    }

    private void updateBatteryStatus() {
        if (mTvBatteryPill == null || getContext() == null)
            return;
        int level = -1;
        boolean isCharging = false;

        // 1. Android BatteryManager / Sticky Intent
        try {
            Context ctx = getContext();
            if (ctx != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
                    if (bm != null) {
                        int cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                        if (cap > 0 && cap <= 100) {
                            level = cap;
                        }
                    }
                }
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent bStatus = ctx.registerReceiver(null, ifilter);
                if (bStatus != null) {
                    int status = bStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL);
                    int rawLevel = bStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = bStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    if (rawLevel >= 0 && scale > 0) {
                        level = (int) ((rawLevel / (float) scale) * 100);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. OrionStar RobotSettingApi
        try {
            String bStr = RobotSettingApi.getInstance().getRobotString(Definition.ROBOT_SETTINGS_BATTERY_INFO);
            if (!TextUtils.isEmpty(bStr)) {
                String digits = bStr.replaceAll("[^0-9]", "");
                if (!TextUtils.isEmpty(digits)) {
                    int robotBat = Integer.parseInt(digits);
                    if (robotBat > 0 && robotBat <= 100) {
                        level = robotBat;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (level >= 0) {
            final String icon = isCharging ? "⚡ " : "🔋 ";
            final String text = icon + level + "%";
            final int color;
            if (level <= 20) {
                color = 0xFFEF4444; // Red
            } else if (level <= 40) {
                color = 0xFFF59E0B; // Amber
            } else {
                color = 0xFFFFFFFF; // Clean White like original OrionStar Lucki
            }
            if (getActivity() != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mTvBatteryPill != null) {
                            mTvBatteryPill.setText(text);
                            mTvBatteryPill.setTextColor(color);
                        }
                        if (mTvLangBattery != null) {
                            mTvLangBattery.setText(text);
                            mTvLangBattery.setTextColor(color);
                        }
                        if (mTvCollectionBattery != null) {
                            mTvCollectionBattery.setText(text);
                            mTvCollectionBattery.setTextColor(color);
                        }
                    }
                });
            } else {
                if (mTvBatteryPill != null) {
                    mTvBatteryPill.setText(text);
                    mTvBatteryPill.setTextColor(color);
                }
                if (mTvLangBattery != null) {
                    mTvLangBattery.setText(text);
                    mTvLangBattery.setTextColor(color);
                }
                if (mTvCollectionBattery != null) {
                    mTvCollectionBattery.setText(text);
                    mTvCollectionBattery.setTextColor(color);
                }
            }
        }
    }

    private void refreshTrayShelvesUi() {
        // TẦNG 1
        boolean a1 = (mActiveTray == 1);
        if (mTraySlots[0].hasDestination()) {
            mShelfTray1.setBackgroundResource(R.drawable.bg_tray_shelf_filled);
            mTvTray1Status.setText(mTraySlots[0].destination);
            mTvTray1Status.setTextColor(0xFFFFFFFF);
            mBtnClearTray1.setVisibility(View.VISIBLE);
        } else {
            mShelfTray1.setBackgroundResource(a1 ? R.drawable.bg_tray_shelf_active : R.drawable.bg_tray_shelf_normal);
            mTvTray1Status.setText(a1 ? (mIsEnglish ? "Please select" : "Xin hãy lựa chọn")
                    : (mIsEnglish ? "Please select" : "Xin hãy lựa chọn"));
            mTvTray1Status.setTextColor(a1 ? 0xFFFFFFFF : 0xFFBAE6FD);
            mBtnClearTray1.setVisibility(View.GONE);
        }

        // TẦNG 2
        boolean a2 = (mActiveTray == 2);
        if (mTraySlots[1].hasDestination()) {
            mShelfTray2.setBackgroundResource(R.drawable.bg_tray_shelf_filled);
            mTvTray2Status.setText(mTraySlots[1].destination);
            mTvTray2Status.setTextColor(0xFFFFFFFF);
            mBtnClearTray2.setVisibility(View.VISIBLE);
        } else {
            mShelfTray2.setBackgroundResource(a2 ? R.drawable.bg_tray_shelf_active : R.drawable.bg_tray_shelf_normal);
            mTvTray2Status.setText(a2 ? (mIsEnglish ? "Please select" : "Xin hãy lựa chọn")
                    : (mIsEnglish ? "Tray 2 Tap to add" : "tầng2 Nhấn để thêm"));
            mTvTray2Status.setTextColor(a2 ? 0xFFFFFFFF : 0xFFBAE6FD);
            mBtnClearTray2.setVisibility(View.GONE);
        }

        // TẦNG 3
        boolean a3 = (mActiveTray == 3);
        if (mTraySlots[2].hasDestination()) {
            mShelfTray3.setBackgroundResource(R.drawable.bg_tray_shelf_filled);
            mTvTray3Status.setText(mTraySlots[2].destination);
            mTvTray3Status.setTextColor(0xFFFFFFFF);
            mBtnClearTray3.setVisibility(View.VISIBLE);
        } else {
            mShelfTray3.setBackgroundResource(a3 ? R.drawable.bg_tray_shelf_active : R.drawable.bg_tray_shelf_normal);
            mTvTray3Status.setText(a3 ? (mIsEnglish ? "Please select" : "Xin hãy lựa chọn")
                    : (mIsEnglish ? "Tray 3 Tap to add" : "tầng3 Nhấn để thêm"));
            mTvTray3Status.setTextColor(a3 ? 0xFFFFFFFF : 0xFFBAE6FD);
            mBtnClearTray3.setVisibility(View.GONE);
        }
    }

    private boolean isDeliveryDestination(String name) {
        if (TextUtils.isEmpty(name))
            return false;
        String lower = name.trim().toLowerCase(Locale.ROOT);

        // 1. Loại trừ các điểm sạc (charging, charge, pole, sạc, dock)
        if (lower.contains("charg") || lower.contains("sạc") || lower.contains("pile") || lower.contains("dock")) {
            return false;
        }

        // 2. Loại trừ các điểm chọn món, lấy món, bếp, quầy order
        if (lower.contains("chọn món") || lower.contains("chon mon") ||
                lower.contains("lấy món") || lower.contains("lay mon") ||
                lower.contains("order") || lower.contains("bếp") ||
                lower.contains("kitchen") || lower.contains("quầy") ||
                lower.contains("bar") || lower.contains("nhận món") ||
                lower.contains("nhan mon")) {
            return false;
        }

        // 3. Loại trừ các điểm kỹ thuật hệ thống (thang máy, positioning...)
        if (lower.contains("chotm") || lower.contains("trongtm") ||
                lower.contains("chờ thang") || lower.contains("trong thang") ||
                lower.contains("positioning") || lower.contains("locate point") ||
                lower.contains("elevator")) {
            return false;
        }

        // Giữ lại: Điểm chờ (Stand-by Point) và tất cả các điểm bàn (cafe, phòng họp,
        // tivi, Bàn 1..n)
        return true;
    }

    private void loadStationsFromMap() {
        mStationsList.clear();
        try {
            JsonObject mapData = RobotMapManager.buildMapDataJson(mCurrentMap);
            if (mapData != null && mapData.has("currentMap")) {
                mCurrentMap = mapData.get("currentMap").getAsString();
            }
            if (mapData != null && mapData.has("places")) {
                JsonArray places = mapData.getAsJsonArray("places");
                for (JsonElement el : places) {
                    JsonObject p = el.getAsJsonObject();
                    if (p.has("name")) {
                        String name = p.get("name").getAsString();
                        // CHỈ THÊM ĐIỂM BÀN VÀ ĐIỂM CHỜ, LOẠI BỎ ĐIỂM SẠC & CHỌN MÓN
                        if (isDeliveryDestination(name) && !mStationsList.contains(name)) {
                            mStationsList.add(name);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error loading places: " + t.getMessage());
        }

        // Default stations matching the user's setup if empty
        if (mStationsList.isEmpty()) {
            mStationsList.add("Stand-by Point");
            mStationsList.add("cafe");
            mStationsList.add("phòng họp");
            mStationsList.add("tivi");
            mStationsList.add("Bàn 1");
            mStationsList.add("Bàn 2");
            mStationsList.add("Bàn 3");
            mStationsList.add("Bàn 4");
            mStationsList.add("Bàn 5");
        } else {
            // Prioritize: cafe, phòng họp, tivi, Stand-by Point
            prioritizeStation("tivi");
            prioritizeStation("phòng họp");
            prioritizeStation("cafe");
            prioritizeStation("Stand-by Point");
        }

        rebuildStationButtons();
    }

    private void prioritizeStation(String name) {
        for (int i = 0; i < mStationsList.size(); i++) {
            if (mStationsList.get(i).equalsIgnoreCase(name)) {
                String item = mStationsList.remove(i);
                mStationsList.add(0, item);
                break;
            }
        }
    }

    private void rebuildStationButtons() {
        if (mContainerStationButtons == null || getContext() == null)
            return;
        mContainerStationButtons.removeAllViews();

        int cols = 4;
        LinearLayout row = null;

        for (int i = 0; i < mStationsList.size(); i++) {
            final String stationName = mStationsList.get(i);

            if (i % cols == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = dpToPx(10);
                row.setLayoutParams(rowLp);
                mContainerStationButtons.addView(row);
            }

            Button btn = new Button(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(48), 1.0f);
            lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            btn.setLayoutParams(lp);

            btn.setBackgroundResource(R.drawable.bg_station_glass_button);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(14);
            btn.setText(stationName);

            // Click destination -> Assign to current active tray!
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    assignStationToActiveTray(stationName);
                }
            });

            if (row != null) {
                row.addView(btn);
            }
        }
    }

    private void assignStationToActiveTray(String stationName) {
        int idx = mActiveTray - 1;
        mTraySlots[idx].destination = stationName;

        // Voice pronunciation feedback
        TTSPlayer.playVietnameseText(stationName, null);

        // Auto-advance to next empty tray
        if (mActiveTray == 1) {
            mActiveTray = !mTraySlots[1].hasDestination() ? 2 : (!mTraySlots[2].hasDestination() ? 3 : 1);
        } else if (mActiveTray == 2) {
            mActiveTray = !mTraySlots[2].hasDestination() ? 3 : (!mTraySlots[0].hasDestination() ? 1 : 2);
        } else {
            mActiveTray = !mTraySlots[0].hasDestination() ? 1 : (!mTraySlots[1].hasDestination() ? 2 : 3);
        }

        refreshTrayShelvesUi();
    }

    // Drawer Management
    private void openLeftDrawer() {
        mPanelLeftDrawer.setVisibility(View.VISIBLE);
        if (mDrawerTimer != null)
            mDrawerTimer.cancel();
        mDrawerTimer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                mBtnDrawerClose.setText("✕ " + (millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                closeLeftDrawer();
            }
        }.start();
    }

    private void closeLeftDrawer() {
        if (mDrawerTimer != null) {
            mDrawerTimer.cancel();
            mDrawerTimer = null;
        }
        mPanelLeftDrawer.setVisibility(View.GONE);
    }

    private void showPowerDialog() {
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("⏻ Tùy chọn nguồn")
                .setMessage("Bạn muốn đưa robot vào trạng thái nào?")
                .setPositiveButton("Khởi động lại", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        TTSPlayer.playVietnameseText("Khởi động lại ứng dụng robot", null);
                        if (getActivity() != null)
                            getActivity().recreate();
                    }
                })
                .setNegativeButton("Chế độ chờ", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        TTSPlayer.playVietnameseText("Robot chuyển sang chế độ chờ", null);
                        closeLeftDrawer();
                    }
                })
                .setNeutralButton("Hủy", null)
                .show();
    }

    private void toggleMusicSelection() {
        final String[] musics = new String[] { "Không có", "Nhạc du dương nhẹ nhàng", "Giai điệu vui tươi",
                "Chuông phục vụ" };
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Chọn nhạc giao hàng:")
                .setItems(musics, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mTvMusicTitle.setText(musics[which]);
                        TTSPlayer.playVietnameseText("Đã chọn " + musics[which], null);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // Settings Dialogs
    private void showSpeedFastDialog() {
        final String[] opts = new String[] { "0.5 m/s", "0.7 m/s (Tiêu chuẩn)", "0.9 m/s (Nhanh)", "1.1 m/s (Tối đa)" };
        final float[] vals = new float[] { 0.5f, 0.7f, 0.9f, 1.1f };
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Cài đặt tốc độ giao hàng nhanh:")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSpeedFast = vals[which];
                        mTvValSpeedFast.setText(opts[which] + " ›");
                        Toast.makeText(getContext(), "Đã lưu tốc độ nhanh: " + opts[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showSpeedWaterDialog() {
        final String[] opts = new String[] { "0.3 m/s (Cực êm)", "0.4 m/s", "0.5 m/s (Tiêu chuẩn)", "0.6 m/s" };
        final float[] vals = new float[] { 0.3f, 0.4f, 0.5f, 0.6f };
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Cài đặt tốc độ giao các món nước:")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSpeedWater = vals[which];
                        mTvValSpeedWater.setText(opts[which] + " ›");
                        Toast.makeText(getContext(), "Đã lưu tốc độ món nước: " + opts[which], Toast.LENGTH_SHORT)
                                .show();
                    }
                })
                .show();
    }

    private void showSpeedReturnDialog() {
        final String[] opts = new String[] { "0.5 m/s", "0.6 m/s (Tiêu chuẩn)", "0.8 m/s" };
        final float[] vals = new float[] { 0.5f, 0.6f, 0.8f };
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Tốc độ trở về vị trí ban đầu:")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSpeedReturn = vals[which];
                        mTvValSpeedReturn.setText(opts[which] + " ›");
                        Toast.makeText(getContext(), "Đã lưu tốc độ trở về: " + opts[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showTableWaitTimeDialog() {
        final String[] opts = new String[] { "15 giây", "20 giây", "30 giây (Tiêu chuẩn)", "45 giây", "60 giây" };
        final int[] vals = new int[] { 15, 20, 30, 45, 60 };
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Thời gian chờ tại bàn:")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mTableWaitTimeSec = vals[which];
                        mTvValTableWaitTime.setText(opts[which] + " ›");
                    }
                })
                .show();
    }

    private void showStationsListDialog() {
        CharSequence[] items = new CharSequence[mStationsList.size()];
        for (int i = 0; i < mStationsList.size(); i++) {
            items[i] = "📍 " + mStationsList.get(i);
        }
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Danh mục điểm đến (" + mStationsList.size() + " điểm):")
                .setItems(items, null)
                .setPositiveButton("Xong", null)
                .show();
    }

    private void showFloorSwitchDialog() {
        final List<String> maps = RobotMapManager.getAvailableMapList();
        if (maps.isEmpty()) {
            maps.add("tăng 2-1110154527");
            maps.add("MT1map-0721090621");
        }

        CharSequence[] items = new CharSequence[maps.size()];
        for (int i = 0; i < maps.size(); i++) {
            String m = maps.get(i);
            String label = m.contains("1") ? "Tầng 1" : "Tầng 2";
            items[i] = "🏢 " + label + " (" + m + ")";
        }

        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Chọn tầng hoạt động:")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mCurrentMap = maps.get(which);
                        loadStationsFromMap();
                        TTSPlayer.playVietnameseText(
                                "Đã chuyển sang " + (mCurrentMap.contains("1") ? "Tầng 1" : "Tầng 2"), null);
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    // ==========================================
    // CÁCH 1 & CÁCH 2: RELOCALIZATION & POSE RESTORE
    // ==========================================

    private void performAutoPoseRestore() {
        if (RobotPosePersistenceManager.getInstance() == null
                || !RobotPosePersistenceManager.getInstance().hasSavedPose()) {
            return;
        }
        RobotPosePersistenceManager.getInstance()
                .checkAndAutoRestorePose(new RobotPosePersistenceManager.RelocalizeCallback() {
                    @Override
                    public void onResult(final boolean success, final String message) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (success) {
                                    updateLocalizationBadge(true);
                                    Toast.makeText(getContext(), "✅ Đã tự động khôi phục vị trí robot!",
                                            Toast.LENGTH_SHORT).show();
                                    TTSPlayer.playVietnameseText("Đã tự động khôi phục vị trí robot", null);
                                } else {
                                    Log.w(TAG, "Không thể tự động khôi phục: " + message);
                                }
                            }
                        });
                    }
                });
    }

    private void startLocalizationMonitor() {
        if (mLocalizationCheckerRunnable != null) {
            mHandler.removeCallbacks(mLocalizationCheckerRunnable);
        }
        mLocalizationCheckerRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    updateClock();
                    updateBatteryStatus();
                    if (RobotApi.getInstance().isApiConnectedService()) {
                        RobotApi.getInstance().isRobotEstimate(0, new CommandListener() {
                            @Override
                            public void onResult(int res, final String msg) {
                                final boolean localized = "true".equalsIgnoreCase(msg);
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateLocalizationBadge(localized);
                                    }
                                });
                            }
                        });
                    }
                } catch (Throwable ignored) {
                }
                mHandler.postDelayed(this, 4000);
            }
        };
        mHandler.post(mLocalizationCheckerRunnable);
    }

    private void updateLocalizationBadge(boolean localized) {
        mIsEstimateValid = localized;
        if (mTvLocalizationStatus != null) {
            if (localized) {
                mTvLocalizationStatus.setVisibility(View.GONE);
            } else {
                mTvLocalizationStatus.setVisibility(View.VISIBLE);
                mTvLocalizationStatus.setText("⚠️ MẤT VỊ TRÍ");
            }
        }
    }

    /**
     * Cách 2: Hộp thoại 1-chạm định vị tại Trạm Sạc / Điểm Chờ / Khôi phục vị trí
     * đã lưu
     */
    private void showQuickRelocalizeDialog() {
        if (getContext() == null)
            return;
        List<String> options = new ArrayList<>();
        options.add("⚡ 1-Chạm: Định vị tại Trạm Sạc");
        options.add("🏠 1-Chạm: Định vị tại Điểm Chờ ");
        if (RobotPosePersistenceManager.getInstance() != null
                && RobotPosePersistenceManager.getInstance().hasSavedPose()) {
            options.add("💾 Khôi phục vị trí lưu gần nhất ("
                    + RobotPosePersistenceManager.getInstance().getLastPlaceName() + ")");
        }
        options.add("📍 Chọn điểm trên bản đồ...");
        options.add("🔄 Tự động quét");

        final CharSequence[] items = options.toArray(new CharSequence[0]);
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("📍 LẤY LẠI VỊ TRÍ ROBOT")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String chosen = items[which].toString();
                        if (chosen.contains("Trạm Sạc")) {
                            relocalizeToChargingDock();
                        } else if (chosen.contains("Điểm Chờ")) {
                            relocalizeToStandbyPoint();
                        } else if (chosen.contains("Khôi phục vị trí lưu")) {
                            relocalizeToLastSavedPose();
                        } else if (chosen.contains("Chọn điểm bất kỳ")) {
                            showRelocalizePickStationDialog();
                        } else if (chosen.contains("360°")) {
                            performAuto360ScanRelocalize();
                        }
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void relocalizeToChargingDock() {
        JsonObject place = RobotMapManager.getPlaceByName(mCurrentMap, "Charging Point");
        if (place == null)
            place = RobotMapManager.getPlaceByName(mCurrentMap, "Charging");
        float x = 0.6f;
        float y = -0.31f;
        float theta = 0.0f;
        String exactName = "Charging Point";
        if (place != null) {
            x = (float) place.get("x").getAsDouble();
            y = (float) place.get("y").getAsDouble();
            theta = (float) place.get("theta").getAsDouble();
            if (place.has("name"))
                exactName = place.get("name").getAsString();
        }
        relocalizeRobotTo(x, y, theta, exactName);
    }

    private void relocalizeToStandbyPoint() {
        JsonObject place = RobotMapManager.getPlaceByName(mCurrentMap, "Stand-by Point");
        if (place == null)
            place = RobotMapManager.getPlaceByName(mCurrentMap, "Stand-by");
        float x = 2.0f;
        float y = 6.01f;
        float theta = 0.0f;
        String exactName = "Stand-by Point";
        if (place != null) {
            x = (float) place.get("x").getAsDouble();
            y = (float) place.get("y").getAsDouble();
            theta = (float) place.get("theta").getAsDouble();
            if (place.has("name"))
                exactName = place.get("name").getAsString();
        }
        relocalizeRobotTo(x, y, theta, exactName);
    }

    private void relocalizeToLastSavedPose() {
        if (RobotPosePersistenceManager.getInstance() == null
                || !RobotPosePersistenceManager.getInstance().hasSavedPose()) {
            Toast.makeText(getContext(), "Chưa có vị trí lưu trước đó", Toast.LENGTH_SHORT).show();
            return;
        }
        float x = RobotPosePersistenceManager.getInstance().getLastX();
        float y = RobotPosePersistenceManager.getInstance().getLastY();
        float theta = RobotPosePersistenceManager.getInstance().getLastTheta();
        String place = RobotPosePersistenceManager.getInstance().getLastPlaceName();
        relocalizeRobotTo(x, y, theta, "Vị trí đã lưu (" + place + ")");
    }

    private void showRelocalizePickStationDialog() {
        try {
            JsonObject mapData = RobotMapManager.buildMapDataJson(mCurrentMap);
            if (mapData != null && mapData.has("places")) {
                com.google.gson.JsonArray places = mapData.getAsJsonArray("places");
                final List<JsonObject> placeObjects = new ArrayList<>();
                List<String> names = new ArrayList<>();
                for (com.google.gson.JsonElement el : places) {
                    JsonObject p = el.getAsJsonObject();
                    placeObjects.add(p);
                    String name = p.get("name").getAsString();
                    double px = p.get("x").getAsDouble();
                    double py = p.get("y").getAsDouble();
                    names.add("📍 " + name + " (X=" + px + ", Y=" + py + ")");
                }

                final CharSequence[] items = names.toArray(new CharSequence[0]);
                new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                        .setTitle("Chọn điểm đặt vị trí:")
                        .setItems(items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                JsonObject p = placeObjects.get(which);
                                float x = (float) p.get("x").getAsDouble();
                                float y = (float) p.get("y").getAsDouble();
                                float theta = (float) p.get("theta").getAsDouble();
                                String name = p.get("name").getAsString();
                                relocalizeRobotTo(x, y, theta, name);
                            }
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
                return;
            }
        } catch (Throwable ignored) {
        }
        Toast.makeText(getContext(), "Không tìm thấy danh sách điểm trên bản đồ", Toast.LENGTH_SHORT).show();
    }

    private boolean mIsScanning360 = false;
    private AlertDialog mScan360ProgressDialog = null;

    private void performAuto360ScanRelocalize() {
        if (mIsScanning360)
            return;
        mIsScanning360 = true;

        TTSPlayer.playVietnameseText("Robot bắt đầu xoay 360 độ để quét Laser tìm lại vị trí...", null);

        // 1. Nếu có tọa độ lưu gần nhất, nạp tọa độ mồi trước vào thuật toán
        if (RobotPosePersistenceManager.getInstance() != null
                && RobotPosePersistenceManager.getInstance().hasSavedPose()) {
            float sx = RobotPosePersistenceManager.getInstance().getLastX();
            float sy = RobotPosePersistenceManager.getInstance().getLastY();
            float sth = RobotPosePersistenceManager.getInstance().getLastTheta();
            try {
                org.json.JSONObject params = new org.json.JSONObject();
                params.put(Definition.JSON_NAVI_POSITION_X, sx);
                params.put(Definition.JSON_NAVI_POSITION_Y, sy);
                params.put(Definition.JSON_NAVI_POSITION_THETA, sth);
                RobotApi.getInstance().setPoseEstimate(0, params.toString(), null);
            } catch (Throwable ignored) {
            }
        }

        // 2. Kích hoạt thuật toán resetEstimate (Action ID 24) chính thức của OrionStar
        // với 5 lần thử
        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().resetEstimate(0, 5, new CommandListener() {
                    @Override
                    public void onResult(int res, String msg) {
                        Log.i(TAG, "resetEstimate result: " + res + ", msg=" + msg);
                    }
                });
            }
        } catch (Throwable ignored) {
        }

        // 3. Ra lệnh cho khung gầm robot quay nhẹ nhàng tại chỗ (20 độ/giây) để Laser
        // quét các vách tường
        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().turnLeft(0, 20.0f, new CommandListener() {
                    @Override
                    public void onResult(int res, String msg) {
                        Log.i(TAG, "turnLeft result: " + res + ", msg=" + msg);
                    }
                });
            }
        } catch (Throwable ignored) {
        }

        // 4. Hiển thị Dialog thông báo tiến trình quét
        if (getContext() != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK);
            builder.setTitle("🔄 Đang quét Laser 360° tìm vị trí");
            builder.setMessage(
                    "Robot đang từ từ xoay quanh để đối chiếu Laser với bản đồ...\n\nQuá trình này mất khoảng 10 - 18 giây.");
            builder.setCancelable(false);
            builder.setNegativeButton("Dừng quét", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    stop360ScanRelocalize(false);
                }
            });
            mScan360ProgressDialog = builder.show();
        }

        // 5. Vòng lặp kiểm tra định vị mỗi 1.2 giây trong tối đa 20 giây
        final long startTime = System.currentTimeMillis();
        final Runnable checkScanRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mIsScanning360)
                    return;

                long elapsed = System.currentTimeMillis() - startTime;
                if (RobotApi.getInstance().isApiConnectedService()) {
                    RobotApi.getInstance().isRobotEstimate(0, new CommandListener() {
                        @Override
                        public void onResult(int res, String msg) {
                            if ("true".equalsIgnoreCase(msg)) {
                                // ĐÃ TÌM THẤY VỊ TRÍ!
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        stop360ScanRelocalize(true);
                                    }
                                });
                                return;
                            }
                        }
                    });
                }

                if (elapsed > 20000) {
                    // Hết thời gian xoay 360 độ (1 vòng hoàn chỉnh) mà chưa nhận diện được
                    stop360ScanRelocalize(false);
                } else {
                    mHandler.postDelayed(this, 1200);
                }
            }
        };
        mHandler.postDelayed(checkScanRunnable, 2000);
    }

    private void stop360ScanRelocalize(boolean success) {
        mIsScanning360 = false;
        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().stopMove(0, null);
            }
        } catch (Throwable ignored) {
        }

        if (mScan360ProgressDialog != null && mScan360ProgressDialog.isShowing()) {
            mScan360ProgressDialog.dismiss();
            mScan360ProgressDialog = null;
        }

        if (success) {
            updateLocalizationBadge(true);
            if (RobotPosePersistenceManager.getInstance() != null) {
                RobotPosePersistenceManager.getInstance().pollAndSaveCurrentPose("Quét 360° thành công");
            }
            Toast.makeText(getContext(), "✅ Đã tìm thấy vị trí Robot thành công!", Toast.LENGTH_LONG).show();
            TTSPlayer.playVietnameseText("Đã tìm thấy vị trí robot thành công!", null);
        } else {
            Toast.makeText(getContext(),
                    "⚠️ Không thể nhận diện vị trí tự động. Hãy dùng nút 'Định vị tại Trạm Sạc / Điểm Chờ'.",
                    Toast.LENGTH_LONG).show();
            TTSPlayer.playVietnameseText("Không thể xác định vị trí. Vui lòng bấm định vị tại Trạm Sạc hoặc Điểm Chờ.",
                    null);
            showQuickRelocalizeDialog();
        }
    }

    private void relocalizeRobotTo(final float x, final float y, final float theta, final String label) {
        TTSPlayer.playVietnameseText("Đang định vị tại " + label, null);
        Toast.makeText(getContext(), "📍 Đang thiết lập vị trí: " + label + "...", Toast.LENGTH_SHORT).show();

        if (RobotPosePersistenceManager.getInstance() != null) {
            RobotPosePersistenceManager.getInstance().applyPoseEstimate(x, y, theta, label,
                    new RobotPosePersistenceManager.RelocalizeCallback() {
                        @Override
                        public void onResult(final boolean success, final String message) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (success) {
                                        updateLocalizationBadge(true);
                                        Toast.makeText(getContext(), "✅ Định vị thành công tại " + label + "!",
                                                Toast.LENGTH_LONG).show();
                                        TTSPlayer.playVietnameseText("Định vị thành công", null);
                                    } else {
                                        Toast.makeText(getContext(), "⚠️ Không thể định vị: " + message,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    });
        }
    }

    // Delivery Process
    private void startDelivery(float speed, String modeName) {
        mIsReturningHome = false;
        mCurrentSpeed = (speed > 0.1f) ? speed : 0.6f;
        mActiveDeliveryQueue.clear();

        for (TraySlot slot : mTraySlots) {
            if (slot.hasDestination()) {
                mActiveDeliveryQueue.add(slot);
            }
        }

        if (mActiveDeliveryQueue.isEmpty()) {
            Toast.makeText(getContext(), "⚠️ Vui lòng chọn điểm đến cho ít nhất 1 tầng!", Toast.LENGTH_SHORT).show();
            if (mIsEnglish) {
                TTSPlayer.playEnglishText("Please select destination for at least one tray.", null);
            } else {
                TTSPlayer.playVietnameseText("Vui lòng chọn điểm đến cần giao món.", null);
            }
            return;
        }

        mIsDelivering = true;
        mCurrentQueueIndex = 0;

        // Show delivering overlay
        mPanelDeliveryConfig.setVisibility(View.GONE);
        mOverlayDelivering.setVisibility(View.VISIBLE);

        String speedMsg = modeName + " (" + String.format(Locale.US, "%.1f", mCurrentSpeed) + " m/s)";
        Toast.makeText(getContext(), speedMsg, Toast.LENGTH_SHORT).show();

        if (mIsEnglish) {
            TTSPlayer.playEnglishText("Robot is starting delivery, please watch out!", null);
        } else {
            TTSPlayer.playVietnameseText("Robot bắt đầu đi giao hàng, quý khách chú ý!", null);
        }
        broadcastStatusToPc("DELIVERY_STARTED");

        executeNextDeliveryLeg();
    }

    private void executeNextDeliveryLeg() {
        if (!mIsDelivering)
            return;

        if (mCurrentQueueIndex >= mActiveDeliveryQueue.size()) {
            onAllDeliveriesCompleted();
            return;
        }

        TraySlot current = mActiveDeliveryQueue.get(mCurrentQueueIndex);

        // Eyes traveling
        mTvRobotEyeLeft.setText("●  ●");
        mTvRobotEyeRight.setText("●  ●");

        mTvDeliveryTitle.setText(mIsEnglish ? ("ROBOT DELIVERING (Tray " + current.number + "/3)")
                : ("ROBOT ĐANG GIAO HÀNG (Tầng " + current.number + "/3)"));
        mTvDeliveryCurrentTarget
                .setText((mIsEnglish ? "GOING TO: " : "ĐANG TỚI: ") + current.destination.toUpperCase());
        mTvDeliveryTrayInfo.setText(mIsEnglish ? ("Delivering items on Tray " + current.number)
                : ("Giao thức ăn tại Tầng " + current.number));

        mTvDeliveryArrivalPrompt.setVisibility(View.GONE);
        mBtnConfirmTaken.setVisibility(View.GONE);

        // SLAM navigate
        navigateRobotTo(current.destination);
    }

    private void onArrivedAtDestination() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!mIsDelivering || mCurrentQueueIndex >= mActiveDeliveryQueue.size())
                    return;

                TraySlot current = mActiveDeliveryQueue.get(mCurrentQueueIndex);

                // Happy eyes
                mTvRobotEyeLeft.setText("^  ^");
                mTvRobotEyeRight.setText("^  ^");

                mTvDeliveryTitle.setText(mIsEnglish ? "🎉 ARRIVED!" : "🎉 ĐÃ TỚI NƠI!");
                mTvDeliveryCurrentTarget.setText(current.destination.toUpperCase());
                mTvDeliveryArrivalPrompt.setText(mIsEnglish ? ("Please receive items on Tray " + current.number + "!")
                        : ("Kính mời quý khách nhận món tại Tầng " + current.number + "!"));
                mTvDeliveryArrivalPrompt.setVisibility(View.VISIBLE);
                mBtnConfirmTaken.setVisibility(View.VISIBLE);

                if (mIsEnglish) {
                    TTSPlayer.playEnglishText(
                            "Welcome! Your items have arrived on Tray " + current.number + ". Please take your items!",
                            null);
                } else {
                    TTSPlayer.playVietnameseText("Kính chào quý khách! Món ăn đã tới tại Tầng " + current.number
                            + ", xin kính mời quý khách nhận món!", null);
                }

                startCountdownTimer();
            }
        });
    }

    private void startCountdownTimer() {
        if (mArrivalTimer != null)
            mArrivalTimer.cancel();
        mArrivalTimer = new CountDownTimer(mTableWaitTimeSec * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int s = (int) (millisUntilFinished / 1000);
                mBtnConfirmTaken.setText(
                        mIsEnglish ? ("✅ FINISHED TAKING (" + s + "s)") : ("✅ ĐÃ LẤY MÓN - TIẾP TỤC (" + s + "s)"));
            }

            @Override
            public void onFinish() {
                onCustomerTakenFood();
            }
        }.start();
    }

    private void onCustomerTakenFood() {
        if (mArrivalTimer != null) {
            mArrivalTimer.cancel();
            mArrivalTimer = null;
        }

        if (mIsEnglish) {
            TTSPlayer.playEnglishText("Thank you, enjoy your meal!", null);
        } else {
            TTSPlayer.playVietnameseText("Cảm ơn quý khách, chúc quý khách ngon miệng!", null);
        }

        mCurrentQueueIndex++;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                executeNextDeliveryLeg();
            }
        }, 2000);
    }

    private String findStandbyStation() {
        // 1. Search in mStationsList for Stand-by or Điểm chờ
        for (String s : mStationsList) {
            String lower = s.toLowerCase(Locale.ROOT).trim();
            if (lower.equals("stand-by point") || lower.equals("standby point") || lower.equals("standby")
                    || lower.equals("điểm chờ") || lower.equals("diem cho")) {
                return s;
            }
        }
        for (String s : mStationsList) {
            String lower = s.toLowerCase(Locale.ROOT).trim();
            if (lower.contains("stand") || lower.contains("chờ") || lower.contains("cho")) {
                return s;
            }
        }
        // 2. Search for charging stations as fallback
        for (String s : mStationsList) {
            String lower = s.toLowerCase(Locale.ROOT).trim();
            if (lower.contains("charg") || lower.contains("sạc") || lower.contains("sac")) {
                return s;
            }
        }
        // 3. Search in all map places
        try {
            JsonObject mapData = RobotMapManager.buildMapDataJson(mCurrentMap);
            if (mapData != null && mapData.has("places")) {
                JsonArray places = mapData.getAsJsonArray("places");
                for (JsonElement el : places) {
                    String name = el.getAsJsonObject().get("name").getAsString();
                    String lower = name.toLowerCase(Locale.ROOT).trim();
                    if (lower.contains("stand") || lower.contains("chờ") || lower.contains("cho")) {
                        return name;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "Stand-by Point";
    }

    private void resetAllTrays() {
        mTraySlots[0].destination = "";
        mTraySlots[1].destination = "";
        mTraySlots[2].destination = "";
        mActiveTray = 1;
        mActiveDeliveryQueue.clear();
        mCurrentQueueIndex = 0;
        refreshTrayShelvesUi();
    }

    private void onReturnedHome() {
        mIsDelivering = false;
        mIsReturningHome = false;

        mTvRobotEyeLeft.setText("^  ^");
        mTvRobotEyeRight.setText("^  ^");

        if (mIsEnglish) {
            TTSPlayer.playEnglishText("Robot has returned to standby station. Ready for next service!", null);
        } else {
            TTSPlayer.playVietnameseText("Robot đã trở về vị trí chờ, sẵn sàng tiếp tục phục vụ!", null);
        }

        Toast.makeText(getContext(), "✅ Robot đã trở về điểm chờ thành công!", Toast.LENGTH_LONG).show();
        broadcastStatusToPc("DELIVERY_COMPLETED");

        resetAllTrays();
        mOverlayDelivering.setVisibility(View.GONE);
        mPanelDeliveryConfig.setVisibility(View.VISIBLE);
    }

    private void onAllDeliveriesCompleted() {
        mIsReturningHome = true;
        mTvRobotEyeLeft.setText("★  ★");
        mTvRobotEyeRight.setText("★  ★");
        mTvDeliveryTitle.setText(mIsEnglish ? "TASK COMPLETED!" : "NHIỆM VỤ HOÀN THÀNH!");

        final String homeStation = findStandbyStation();
        mTvDeliveryCurrentTarget.setText((mIsEnglish ? "RETURNING TO: " : "TRỞ VỀ: ") + homeStation.toUpperCase());
        mTvDeliveryTrayInfo.setText(mIsEnglish ? "All trays delivered. Returning to standby." : "Đã giao xong tất cả các tầng. Đang về điểm chờ.");
        mTvDeliveryArrivalPrompt.setVisibility(View.GONE);
        mBtnConfirmTaken.setVisibility(View.GONE);

        if (mIsEnglish) {
            TTSPlayer.playEnglishText("Task completed! Robot is returning to " + homeStation + ".", null);
        } else {
            TTSPlayer.playVietnameseText("Nhiệm vụ hoàn thành, robot đang trở về " + homeStation + " phục vụ!", null);
        }
        broadcastStatusToPc("DELIVERY_RETURNING_HOME");

        // Set return speed
        mCurrentSpeed = (mSpeedReturn > 0.1f) ? mSpeedReturn : 0.6f;
        navigateRobotTo(homeStation);
    }

    private void abortDelivery() {
        if (mArrivalTimer != null) {
            mArrivalTimer.cancel();
            mArrivalTimer = null;
        }
        mIsDelivering = false;
        mIsReturningHome = false;

        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().stopNavigation(0);
                RobotApi.getInstance().stopMove(0, null);
            }
        } catch (Throwable ignored) {
        }

        mOverlayDelivering.setVisibility(View.GONE);
        mPanelDeliveryConfig.setVisibility(View.VISIBLE);
        refreshTrayShelvesUi();
    }

    private void navigateRobotTo(final String dest) {
        if (TextUtils.isEmpty(dest))
            return;
        Log.i(TAG, "Navigating to: " + dest + ", speed: " + mCurrentSpeed + ", returningHome=" + mIsReturningHome);

        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                // Apply speed to RobotApi
                try {
                    RobotApi.getInstance().setLineSpeed(0, String.format(Locale.US, "%.1f", mCurrentSpeed), null);
                } catch (Throwable t) {
                    Log.w(TAG, "setLineSpeed error: " + t.getMessage());
                }

                RobotApi.getInstance().startNavigation(0, dest, 1.2, (double) mCurrentSpeed, 90 * 1000, new ActionListener() {
                    @Override
                    public void onResult(int status, String responseString) throws android.os.RemoteException {
                        Log.i(TAG, "startNavigation onResult: " + status + ", " + responseString);
                        if (status == Definition.RESULT_OK || "succeed".equalsIgnoreCase(responseString)) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (mIsReturningHome) {
                                        onReturnedHome();
                                    } else {
                                        onArrivedAtDestination();
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(int errorCode, String errorString) throws android.os.RemoteException {
                        Log.e(TAG, "startNavigation error: " + errorCode + ", " + errorString);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getContext(), "Lỗi điều hướng (" + errorCode + "): " + errorString, Toast.LENGTH_LONG).show();
                                if (mIsEnglish) {
                                    TTSPlayer.playEnglishText("Navigation error, please check path.", null);
                                } else {
                                    TTSPlayer.playVietnameseText("Không thể di chuyển đến " + dest + ", vui lòng kiểm tra vật cản.", null);
                                }
                            }
                        });
                    }

                    @Override
                    public void onStatusUpdate(int status, String data) throws android.os.RemoteException {
                        Log.i(TAG, "onStatusUpdate: " + status + " data=" + data);
                    }
                });
            } else {
                // Testing simulation when CoreService is not available
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsReturningHome) {
                            onReturnedHome();
                        } else {
                            onArrivedAtDestination();
                        }
                    }
                }, 4000);
            }
        } catch (Throwable t) {
            Log.e(TAG, "navigate error: " + t.getMessage());
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mIsReturningHome) {
                        onReturnedHome();
                    } else {
                        onArrivedAtDestination();
                    }
                }
            }, 4000);
        }
    }

    private void broadcastStatusToPc(String event) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", "ROBOT_DELIVERY_EVENT");
            obj.addProperty("event", event);
            obj.addProperty("currentLeg", mCurrentQueueIndex + 1);
            obj.addProperty("totalLegs", mActiveDeliveryQueue.size());

            RobotBridgeServer server = RobotBridgeManager.getInstance().getServer();
            if (server != null) {
                server.broadcast(obj.toString());
            }
        } catch (Throwable ignored) {
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    // =========================================================
    // CHẾ ĐỘ DU THUYỀN (MINIMALIST CRUISE ENGINE)
    // =========================================================

    private void switchActiveMode(int mode) {
        mCurrentActiveMode = mode;
        if (mPanelModeSwitch != null)
            mPanelModeSwitch.setVisibility(View.GONE);
        if (mPanelDeliveryConfig != null)
            mPanelDeliveryConfig.setVisibility(View.GONE);
        if (mPanelCruiseConfig != null)
            mPanelCruiseConfig.setVisibility(View.GONE);
        if (mPanelCollectionMode != null)
            mPanelCollectionMode.setVisibility(View.GONE);
        if (mPanelLanguageSelect != null)
            mPanelLanguageSelect.setVisibility(View.GONE);

        // Update tag indicators in mode switch panel
        if (mTagUsingTransport != null) {
            mTagUsingTransport.setVisibility(mode == MODE_DELIVERY ? View.VISIBLE : View.GONE);
        }
        if (mTagUsingCruise != null) {
            mTagUsingCruise.setVisibility(mode == MODE_CRUISE ? View.VISIBLE : View.GONE);
        }

        switch (mode) {
            case MODE_DELIVERY:
                if (mPanelDeliveryConfig != null)
                    mPanelDeliveryConfig.setVisibility(View.VISIBLE);
                TTSPlayer.playVietnameseText("Chế độ Vận chuyển", null);
                break;

            case MODE_CRUISE:
                if (mPanelCruiseConfig != null)
                    mPanelCruiseConfig.setVisibility(View.VISIBLE);
                rebuildCruiseStationChips();
                TTSPlayer.playVietnameseText("Chế độ Du thuyền", null);
                break;

            case MODE_PROMO:
                if (mPanelDeliveryConfig != null)
                    mPanelDeliveryConfig.setVisibility(View.VISIBLE);
                TTSPlayer.playVietnameseText("Chế độ Khuyến mãi", null);
                Toast.makeText(getContext(), "Chế độ Khuyến mãi", Toast.LENGTH_SHORT).show();
                break;

            case MODE_COLLECTION:
                if (mPanelCollectionMode != null)
                    mPanelCollectionMode.setVisibility(View.VISIBLE);
                TTSPlayer.playVietnameseText("Đã vào chế độ thu thập. Sẵn sàng cho bộ sưu tập", null);
                break;
        }
    }

    private void showAutoChargeDialog() {
        if (getContext() == null)
            return;
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(mIsEnglish ? "Auto Recharge Station" : "Về Trạm sạc tự động")
                .setMessage(mIsEnglish ? "Send robot back to charging dock now?"
                        : "Bạn có muốn gửi robot quay về trạm sạc tự động ngay bây giờ?")
                .setPositiveButton(mIsEnglish ? "Recharge" : "Về trạm sạc", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mIsEnglish) {
                            TTSPlayer.playEnglishText("Robot navigating to auto charging station", null);
                        } else {
                            TTSPlayer.playVietnameseText("Robot đang quay về trạm sạc tự động", null);
                        }
                        Toast.makeText(getContext(),
                                mIsEnglish ? "Navigating to Auto Charge Dock..." : "Đang về trạm sạc tự động...",
                                Toast.LENGTH_SHORT).show();
                        RobotApi.getInstance().startNaviToAutoChargeAction(0, 5 * Definition.MINUTE, new ActionListener() {
                            @Override
                            public void onResult(int status, String responseString) {
                                Log.i(TAG, "startNaviToAutoChargeAction result: " + status + " " + responseString);
                            }

                            @Override
                            public void onError(int errorCode, String errorString) {
                                Log.e(TAG, "startNaviToAutoChargeAction error: " + errorCode + " " + errorString);
                            }

                            @Override
                            public void onStatusUpdate(int status, String data) {
                            }
                        });
                    }
                })
                .setNegativeButton(mIsEnglish ? "Cancel" : "Hủy", null)
                .show();
    }

    private void setupLanguageSelectionHandlers() {
        View.OnClickListener langClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.lang_item_vi) {
                    mIsEnglish = false;
                    applyLanguage(false, true);
                    Toast.makeText(getContext(), "Đã chuyển sang Tiếng Việt", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.lang_item_en) {
                    mIsEnglish = true;
                    applyLanguage(true, true);
                    Toast.makeText(getContext(), "Switched to English", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.lang_item_zh || id == R.id.lang_item_zht) {
                    Toast.makeText(getContext(), "已选择中文模式", Toast.LENGTH_SHORT).show();
                    TTSPlayer.playVietnameseText("Chế độ tiếng Trung", null);
                } else {
                    Toast.makeText(getContext(), "Đã chọn ngôn ngữ!", Toast.LENGTH_SHORT).show();
                }
                updateLanguageCheckmarks();
                if (mPanelLanguageSelect != null)
                    mPanelLanguageSelect.setVisibility(View.GONE);
                if (mPanelDeliveryConfig != null)
                    mPanelDeliveryConfig.setVisibility(View.VISIBLE);
            }
        };

        if (mLangItemVi != null)
            mLangItemVi.setOnClickListener(langClick);
        if (mLangItemEn != null)
            mLangItemEn.setOnClickListener(langClick);
        if (mLangItemZh != null)
            mLangItemZh.setOnClickListener(langClick);
        if (mLangItemZht != null)
            mLangItemZht.setOnClickListener(langClick);
        if (mLangItemJa != null)
            mLangItemJa.setOnClickListener(langClick);
        if (mLangItemKo != null)
            mLangItemKo.setOnClickListener(langClick);
        if (mLangItemTh != null)
            mLangItemTh.setOnClickListener(langClick);
        if (mLangItemId != null)
            mLangItemId.setOnClickListener(langClick);
        if (mLangItemDe != null)
            mLangItemDe.setOnClickListener(langClick);

        updateLanguageCheckmarks();
    }

    private void updateLanguageCheckmarks() {
        if (mIcCheckVi != null)
            mIcCheckVi.setVisibility(!mIsEnglish ? View.VISIBLE : View.GONE);
        if (mIcCheckEn != null)
            mIcCheckEn.setVisibility(mIsEnglish ? View.VISIBLE : View.GONE);
    }

    private void setupCollectionModeHandlers() {
        if (mBtnCollectionMenuDots != null) {
            mBtnCollectionMenuDots.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openLeftDrawer();
                }
            });
        }

        if (mBtnCollectionLocal != null) {
            mBtnCollectionLocal.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TTSPlayer.playVietnameseText("Bắt đầu thu thập địa phương", null);
                    Toast.makeText(getContext(), "Đang tuần tra thu dọn bát đĩa...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (mBtnCollectionWashroom != null) {
            mBtnCollectionWashroom.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String standby = findStandbyStation();
                    TTSPlayer.playVietnameseText("Quay lại khu vực thu thập", null);
                    Toast.makeText(getContext(), "Quay lại khu vực thu thập...", Toast.LENGTH_SHORT).show();
                    mIsReturningHome = true;
                    mCurrentSpeed = (mSpeedReturn > 0.1f) ? mSpeedReturn : 0.6f;
                    navigateRobotTo(standby);
                }
            });
        }
    }

    private void setupCruiseControls() {
        if (mBtnCruiseBack != null) {
            mBtnCruiseBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mPanelCruiseConfig != null)
                        mPanelCruiseConfig.setVisibility(View.GONE);
                    if (mPanelDeliveryConfig != null)
                        mPanelDeliveryConfig.setVisibility(View.VISIBLE);
                    mCurrentActiveMode = MODE_DELIVERY;
                }
            });
        }

        if (mTvCruiseModeBadge != null) {
            mTvCruiseModeBadge.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mPanelCruiseConfig != null)
                        mPanelCruiseConfig.setVisibility(View.GONE);
                    if (mPanelModeSwitch != null)
                        mPanelModeSwitch.setVisibility(View.VISIBLE);
                }
            });
        }

        if (mBtnCruiseSelectAll != null) {
            mBtnCruiseSelectAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mCruiseSelectedStations.clear();
                    for (String s : mStationsList) {
                        if (!s.equalsIgnoreCase("Stand-by Point")) {
                            mCruiseSelectedStations.add(s);
                        }
                    }
                    rebuildCruiseStationChips();
                }
            });
        }

        if (mBtnCruiseClearAll != null) {
            mBtnCruiseClearAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mCruiseSelectedStations.clear();
                    rebuildCruiseStationChips();
                }
            });
        }

        // Dwell Time buttons (10s, 20s, 30s)
        if (mBtnCruiseDwell10s != null) {
            mBtnCruiseDwell10s.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setCruiseDwellTime(10);
                }
            });
        }
        if (mBtnCruiseDwell20s != null) {
            mBtnCruiseDwell20s.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setCruiseDwellTime(20);
                }
            });
        }
        if (mBtnCruiseDwell30s != null) {
            mBtnCruiseDwell30s.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setCruiseDwellTime(30);
                }
            });
        }

        // Loop buttons (1 vòng, 2 vòng, vô tận)
        if (mBtnCruiseLoop1 != null) {
            mBtnCruiseLoop1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setCruiseLoops(1);
                }
            });
        }
        if (mBtnCruiseLoop2 != null) {
            mBtnCruiseLoop2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setCruiseLoops(2);
                }
            });
        }
        if (mBtnCruiseLoopInfinite != null) {
            mBtnCruiseLoopInfinite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setCruiseLoops(-1);
                }
            });
        }

        // Start Cruise Button
        if (mBtnStartCruise != null) {
            mBtnStartCruise.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startCruiseProcess();
                }
            });
        }

        // Active overlay controls
        if (mBtnCruiseNext != null) {
            mBtnCruiseNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    skipToNextCruiseStop();
                }
            });
        }

        if (mBtnCruisePauseResume != null) {
            mBtnCruisePauseResume.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleCruisePauseResume();
                }
            });
        }

        if (mBtnCruiseFinish != null) {
            mBtnCruiseFinish.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopCruiseAndReturnHome();
                }
            });
        }

        if (mPanelCruiseArrival != null) {
            mPanelCruiseArrival.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TTSPlayer.playVietnameseText("Cảm ơn quý khách!", null);
                }
            });
        }
    }

    private void setCruiseDwellTime(int seconds) {
        mCruiseDwellTimeSec = seconds;
        if (mBtnCruiseDwell10s != null) {
            mBtnCruiseDwell10s.setBackgroundResource(
                    seconds == 10 ? R.drawable.bg_btn_table_selected : R.drawable.bg_station_glass_button);
            mBtnCruiseDwell10s.setTextColor(seconds == 10 ? Color.WHITE : Color.parseColor("#94A3B8"));
        }
        if (mBtnCruiseDwell20s != null) {
            mBtnCruiseDwell20s.setBackgroundResource(
                    seconds == 20 ? R.drawable.bg_btn_table_selected : R.drawable.bg_station_glass_button);
            mBtnCruiseDwell20s.setTextColor(seconds == 20 ? Color.WHITE : Color.parseColor("#94A3B8"));
        }
        if (mBtnCruiseDwell30s != null) {
            mBtnCruiseDwell30s.setBackgroundResource(
                    seconds == 30 ? R.drawable.bg_btn_table_selected : R.drawable.bg_station_glass_button);
            mBtnCruiseDwell30s.setTextColor(seconds == 30 ? Color.WHITE : Color.parseColor("#94A3B8"));
        }
    }

    private void setCruiseLoops(int loops) {
        mCruiseTotalLoops = loops;
        if (mBtnCruiseLoop1 != null) {
            mBtnCruiseLoop1.setBackgroundResource(
                    loops == 1 ? R.drawable.bg_btn_table_selected : R.drawable.bg_station_glass_button);
            mBtnCruiseLoop1.setTextColor(loops == 1 ? Color.WHITE : Color.parseColor("#94A3B8"));
        }
        if (mBtnCruiseLoop2 != null) {
            mBtnCruiseLoop2.setBackgroundResource(
                    loops == 2 ? R.drawable.bg_btn_table_selected : R.drawable.bg_station_glass_button);
            mBtnCruiseLoop2.setTextColor(loops == 2 ? Color.WHITE : Color.parseColor("#94A3B8"));
        }
        if (mBtnCruiseLoopInfinite != null) {
            mBtnCruiseLoopInfinite.setBackgroundResource(
                    loops == -1 ? R.drawable.bg_btn_table_selected : R.drawable.bg_station_glass_button);
            mBtnCruiseLoopInfinite.setTextColor(loops == -1 ? Color.WHITE : Color.parseColor("#94A3B8"));
        }
    }

    private void rebuildCruiseStationChips() {
        if (mContainerCruiseStations == null)
            return;
        mContainerCruiseStations.removeAllViews();

        int columns = 3;
        LinearLayout currentRow = null;

        for (int i = 0; i < mStationsList.size(); i++) {
            final String stationName = mStationsList.get(i);
            if (stationName.equalsIgnoreCase("Stand-by Point"))
                continue;

            if (i % columns == 0 || currentRow == null) {
                currentRow = new LinearLayout(getContext());
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                currentRow.setPadding(0, 0, 0, 8);
                mContainerCruiseStations.addView(currentRow);
            }

            final boolean isSelected = mCruiseSelectedStations.contains(stationName);
            int orderIndex = isSelected ? (mCruiseSelectedStations.indexOf(stationName) + 1) : -1;

            final TextView chip = new TextView(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, 48, 1.0f);
            lp.setMargins(4, 0, 4, 0);
            chip.setLayoutParams(lp);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(13);
            chip.setTypeface(null, Typeface.BOLD);

            if (isSelected) {
                chip.setBackgroundResource(R.drawable.bg_btn_table_selected);
                chip.setTextColor(Color.WHITE);
                chip.setText("✔ [" + orderIndex + "] " + stationName);
            } else {
                chip.setBackgroundResource(R.drawable.bg_station_glass_button);
                chip.setTextColor(Color.parseColor("#BAE6FD"));
                chip.setText(stationName);
            }

            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mCruiseSelectedStations.contains(stationName)) {
                        mCruiseSelectedStations.remove(stationName);
                    } else {
                        mCruiseSelectedStations.add(stationName);
                    }
                    rebuildCruiseStationChips();
                }
            });

            currentRow.addView(chip);
        }

        // Update Route Summary Text
        if (mTvCruiseRouteSummary != null) {
            if (mCruiseSelectedStations.isEmpty()) {
                mTvCruiseRouteSummary.setText("Chưa chọn điểm dừng");
                mTvCruiseRouteSummary.setTextColor(Color.parseColor("#FCA5A5"));
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < mCruiseSelectedStations.size(); i++) {
                    sb.append(mCruiseSelectedStations.get(i));
                    if (i < mCruiseSelectedStations.size() - 1) {
                        sb.append(" ➔ ");
                    }
                }
                sb.append(" (" + mCruiseSelectedStations.size() + " bàn)");
                mTvCruiseRouteSummary.setText(sb.toString());
                mTvCruiseRouteSummary.setTextColor(Color.parseColor("#38BDF8"));
            }
        }
    }

    private void startCruiseProcess() {
        if (mCruiseSelectedStations.isEmpty()) {
            Toast.makeText(getContext(), "Vui lòng chọn bàn tuần tra!", Toast.LENGTH_SHORT).show();
            TTSPlayer.playVietnameseText("Vui lòng chọn bàn để bắt đầu du thuyền!", null);
            return;
        }

        mIsCruising = true;
        mIsCruisePaused = false;
        mCruiseCurrentLoop = 1;
        mCruiseCurrentIndex = 0;

        if (mPanelCruiseConfig != null)
            mPanelCruiseConfig.setVisibility(View.GONE);
        if (mOverlayCruising != null)
            mOverlayCruising.setVisibility(View.VISIBLE);
        if (mPanelCruiseArrival != null)
            mPanelCruiseArrival.setVisibility(View.GONE);

        String firstDest = mCruiseSelectedStations.get(0);
        TTSPlayer.playVietnameseText("Bắt đầu du thuyền tới " + firstDest, null);
        navigateCruiseTo(firstDest);
    }

    private void navigateCruiseTo(final String dest) {
        if (mPanelCruiseArrival != null)
            mPanelCruiseArrival.setVisibility(View.GONE);
        if (mTvCruiseActiveCurrentDest != null) {
            mTvCruiseActiveCurrentDest.setText(dest.toUpperCase());
        }

        if (mTvCruiseActiveLoopInfo != null) {
            String loopText = (mCruiseTotalLoops == -1) ? ("Vòng " + mCruiseCurrentLoop)
                    : ("Vòng " + mCruiseCurrentLoop + "/" + mCruiseTotalLoops);
            mTvCruiseActiveLoopInfo
                    .setText(loopText + " • Điểm " + (mCruiseCurrentIndex + 1) + "/" + mCruiseSelectedStations.size());
        }

        if (mBtnCruisePauseResume != null) {
            mBtnCruisePauseResume.setText("⏸️ Tạm dừng");
            mIsCruisePaused = false;
        }

        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().startNavigation(0, dest, 1.5, 90 * 1000, new ActionListener() {
                    @Override
                    public void onResult(int status, String responseString) throws android.os.RemoteException {
                        if (status == Definition.RESULT_OK || "succeed".equalsIgnoreCase(responseString)) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    onArrivedAtCruiseStation(dest);
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(int errorCode, String errorString) throws android.os.RemoteException {
                        Log.e(TAG, "Cruise nav error: " + errorCode + " " + errorString);
                    }

                    @Override
                    public void onStatusUpdate(int status, String data) throws android.os.RemoteException {
                    }
                });
            } else {
                // Emulation fallback
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsCruising && !mIsCruisePaused) {
                            onArrivedAtCruiseStation(dest);
                        }
                    }
                }, 3500);
            }
        } catch (Throwable t) {
            Log.e(TAG, "navigateCruiseTo error: " + t.getMessage());
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mIsCruising)
                        onArrivedAtCruiseStation(dest);
                }
            }, 3500);
        }
    }

    private void onArrivedAtCruiseStation(final String dest) {
        if (!mIsCruising)
            return;

        if (mPanelCruiseArrival != null)
            mPanelCruiseArrival.setVisibility(View.VISIBLE);
        if (mTvCruiseArrivalStation != null) {
            mTvCruiseArrivalStation.setText("XIN CHÀO " + dest.toUpperCase() + "!");
        }

        // Play voice announcement
        TTSPlayer.playVietnameseText(mCruiseGreetingSpeech, null);

        if (mCruiseArrivalTimer != null) {
            mCruiseArrivalTimer.cancel();
            mCruiseArrivalTimer = null;
        }

        if (mCruiseDwellTimeSec > 0) {
            mCruiseArrivalTimer = new CountDownTimer(mCruiseDwellTimeSec * 1000L, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    if (mTvCruiseCountdown != null) {
                        int secLeft = (int) Math.ceil(millisUntilFinished / 1000.0);
                        mTvCruiseCountdown.setText("⏳ " + secLeft + "s");
                    }
                }

                @Override
                public void onFinish() {
                    skipToNextCruiseStop();
                }
            }.start();
        } else {
            if (mTvCruiseCountdown != null) {
                mTvCruiseCountdown.setText("Chờ bấm Đi tiếp");
            }
        }
    }

    private void skipToNextCruiseStop() {
        if (mCruiseArrivalTimer != null) {
            mCruiseArrivalTimer.cancel();
            mCruiseArrivalTimer = null;
        }

        if (!mIsCruising)
            return;

        mCruiseCurrentIndex++;
        if (mCruiseCurrentIndex < mCruiseSelectedStations.size()) {
            String nextDest = mCruiseSelectedStations.get(mCruiseCurrentIndex);
            navigateCruiseTo(nextDest);
        } else {
            // Completed current loop!
            if (mCruiseTotalLoops == -1 || mCruiseCurrentLoop < mCruiseTotalLoops) {
                mCruiseCurrentLoop++;
                mCruiseCurrentIndex = 0;
                String loopNextDest = mCruiseSelectedStations.get(0);
                TTSPlayer.playVietnameseText("Bắt đầu vòng du thuyền tiếp theo.", null);
                navigateCruiseTo(loopNextDest);
            } else {
                // Completed all loops!
                TTSPlayer.playVietnameseText("Hoàn tất du thuyền. Robot quay về vị trí chờ.", null);
                stopCruiseAndReturnHome();
            }
        }
    }

    private void toggleCruisePauseResume() {
        mIsCruisePaused = !mIsCruisePaused;
        if (mIsCruisePaused) {
            if (mBtnCruisePauseResume != null)
                mBtnCruisePauseResume.setText("▶️ Tiếp tục");
            try {
                if (RobotApi.getInstance().isApiConnectedService()) {
                    RobotApi.getInstance().stopMove(0, null);
                }
            } catch (Throwable ignored) {
            }
            if (mCruiseArrivalTimer != null) {
                mCruiseArrivalTimer.cancel();
            }
            TTSPlayer.playVietnameseText("Tạm dừng.", null);
        } else {
            if (mBtnCruisePauseResume != null)
                mBtnCruisePauseResume.setText("⏸️ Tạm dừng");
            TTSPlayer.playVietnameseText("Tiếp tục du thuyền.", null);
            if (mCruiseCurrentIndex < mCruiseSelectedStations.size()) {
                navigateCruiseTo(mCruiseSelectedStations.get(mCruiseCurrentIndex));
            }
        }
    }

    private void stopCruiseAndReturnHome() {
        mIsCruising = false;
        if (mCruiseArrivalTimer != null) {
            mCruiseArrivalTimer.cancel();
            mCruiseArrivalTimer = null;
        }

        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().stopMove(0, null);
            }
        } catch (Throwable ignored) {
        }

        if (mOverlayCruising != null)
            mOverlayCruising.setVisibility(View.GONE);
        if (mPanelCruiseConfig != null)
            mPanelCruiseConfig.setVisibility(View.VISIBLE);

        TTSPlayer.playVietnameseText("Robot quay về vị trí chờ.", null);
        String homeStation = "Stand-by Point";
        for (String s : mStationsList) {
            if (s.toLowerCase().contains("stand-by") || s.toLowerCase().contains("sạc")
                    || s.toLowerCase().contains("reception")) {
                homeStation = s;
                break;
            }
        }

        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().startNavigation(0, homeStation, 1.5, 90 * 1000, null);
            }
        } catch (Throwable ignored) {
        }
    }

    // =========================================================
    // QUICK LANGUAGE SWITCHER (TIẾNG VIỆT / ENGLISH)
    // =========================================================

    private void showLanguageSelectDialog() {
        if (getContext() == null)
            return;

        final android.app.Dialog dialog = new android.app.Dialog(getContext());
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        // Futuristic frosted dark container
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(0xF00D1527);
        gd.setStroke(dpToPx(1.5f), 0xFF00E5FF);
        gd.setCornerRadius(dpToPx(16));
        root.setBackground(gd);
        root.setPadding(dpToPx(24), dpToPx(22), dpToPx(24), dpToPx(24));

        // Header Title
        TextView tvTitle = new TextView(getContext());
        tvTitle.setText(mIsEnglish ? "🌐 SELECT LANGUAGE" : "🌐 CHỌN NGÔN NGỮ");
        tvTitle.setTextColor(0xFF00E5FF);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, dpToPx(6));
        root.addView(tvTitle);

        TextView tvSub = new TextView(getContext());
        tvSub.setText(mIsEnglish ? "Switch instant language for Robot UI & Speech"
                : "Chuyển đổi ngôn ngữ hiển thị & giọng nói");
        tvSub.setTextColor(0xFF94A3B8);
        tvSub.setTextSize(11);
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setPadding(0, 0, 0, dpToPx(18));
        root.addView(tvSub);

        // Option 1: Tiếng Việt Button
        Button btnVi = new Button(getContext());
        btnVi.setText(!mIsEnglish ? "🇻🇳 Tiếng Việt  (Đang chọn)" : "🇻🇳 Tiếng Việt");
        btnVi.setTextColor(!mIsEnglish ? 0xFFFFFFFF : 0xFFBAE6FD);
        btnVi.setTextSize(15);
        btnVi.setTypeface(Typeface.DEFAULT_BOLD);

        android.graphics.drawable.GradientDrawable gdVi = new android.graphics.drawable.GradientDrawable();
        gdVi.setColor(!mIsEnglish ? 0xFF0284C7 : 0xFF17253D);
        gdVi.setStroke(dpToPx(1.2f), !mIsEnglish ? 0xFF00E5FF : 0xFF1E2D4A);
        gdVi.setCornerRadius(dpToPx(12));
        btnVi.setBackground(gdVi);

        LinearLayout.LayoutParams lpVi = new LinearLayout.LayoutParams(dpToPx(260), dpToPx(50));
        lpVi.setMargins(0, 0, 0, dpToPx(12));
        btnVi.setLayoutParams(lpVi);
        btnVi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyLanguage(false, true);
                dialog.dismiss();
            }
        });
        root.addView(btnVi);

        // Option 2: English Button
        Button btnEn = new Button(getContext());
        btnEn.setText(mIsEnglish ? "🇬🇧 English  (Selected ✔)" : "🇬🇧 English");
        btnEn.setTextColor(mIsEnglish ? 0xFFFFFFFF : 0xFFBAE6FD);
        btnEn.setTextSize(15);
        btnEn.setTypeface(Typeface.DEFAULT_BOLD);

        android.graphics.drawable.GradientDrawable gdEn = new android.graphics.drawable.GradientDrawable();
        gdEn.setColor(mIsEnglish ? 0xFF0284C7 : 0xFF17253D);
        gdEn.setStroke(dpToPx(1.2f), mIsEnglish ? 0xFF00E5FF : 0xFF1E2D4A);
        gdEn.setCornerRadius(dpToPx(12));
        btnEn.setBackground(gdEn);

        LinearLayout.LayoutParams lpEn = new LinearLayout.LayoutParams(dpToPx(260), dpToPx(50));
        lpEn.setMargins(0, 0, 0, dpToPx(16));
        btnEn.setLayoutParams(lpEn);
        btnEn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyLanguage(true, true);
                dialog.dismiss();
            }
        });
        root.addView(btnEn);

        // Close Button
        TextView btnClose = new TextView(getContext());
        btnClose.setText(mIsEnglish ? "Close" : "Đóng");
        btnClose.setTextColor(0xFF64748B);
        btnClose.setTextSize(13);
        btnClose.setTypeface(Typeface.DEFAULT_BOLD);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        root.addView(btnClose);

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private int dpToPx(float dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void applyLanguage(boolean isEnglish, boolean speakGreeting) {
        mIsEnglish = isEnglish;

        try {
            if (getContext() != null) {
                SharedPreferences sp = getContext().getSharedPreferences("lehoang_prefs", Context.MODE_PRIVATE);
                sp.edit().putBoolean(PREF_KEY_LANGUAGE_IS_EN, isEnglish).apply();
            }
        } catch (Throwable ignored) {
        }

        // 1. Language button top status bar
        if (mBtnLanguage != null) {
            mBtnLanguage.setText(isEnglish ? "🇬🇧 EN" : "🇻🇳 VI");
        }

        // 2. Tray Badges
        if (mTvBadgeTray1 != null)
            mTvBadgeTray1.setText(isEnglish ? "TRAY 1" : "TẦNG 1");
        if (mTvBadgeTray2 != null)
            mTvBadgeTray2.setText(isEnglish ? "TRAY 2" : "TẦNG 2");
        if (mTvBadgeTray3 != null)
            mTvBadgeTray3.setText(isEnglish ? "TRAY 3" : "TẦNG 3");

        // 3. Station Section Header
        if (mTvHeaderDestinations != null) {
            mTvHeaderDestinations.setText(isEnglish ? "Please select destination" : "Vui lòng chọn: điểm đến");
        }
        if (mBtnRefreshStations != null) {
            mBtnRefreshStations.setText(isEnglish ? "🔄 Refresh points" : "🔄 Tải lại điểm");
        }

        // 4. Delivery Action Buttons
        if (mBtnSlowDelivery != null) {
            mBtnSlowDelivery.setText(isEnglish ? "\"Slow Delivery\"" : "\"Giao hàng chậm\"");
        }
        if (mBtnFastDelivery != null) {
            mBtnFastDelivery.setText(isEnglish ? "\"Fast Delivery\"" : "\"Giao hàng nhanh\"");
        }

        // 5. Localization Alert
        if (mTvLocalizationStatus != null) {
            mTvLocalizationStatus.setText(isEnglish ? "⚠️ LOST POSITION" : "⚠️ MẤT VỊ TRÍ");
        }

        // 6. Cruise Mode Badge & Labels
        if (mTvCruiseModeBadge != null) {
            mTvCruiseModeBadge.setText(isEnglish ? "CRUISE PATROL MODE" : "CHẾ ĐỘ DU THUYỀN (CRUISE MODE)");
        }
        if (mBtnCruiseSelectAll != null) {
            mBtnCruiseSelectAll.setText(isEnglish ? "✔ Select All" : "✔ Chọn tất cả");
        }
        if (mBtnCruiseClearAll != null) {
            mBtnCruiseClearAll.setText(isEnglish ? "✕ Clear" : "✕ Bỏ chọn");
        }
        if (mBtnStartCruise != null) {
            mBtnStartCruise.setText(isEnglish ? "🚀 START CRUISE PATROL" : "🚀 BẮT ĐẦU DU THUYỀN TUẦN TRA");
        }

        // Refresh Tray shelves status
        refreshTrayShelvesUi();

        if (speakGreeting) {
            if (isEnglish) {
                TTSPlayer.playEnglishText("Welcome to Le Hoang Robotics!", null);
                Toast.makeText(getContext(), "Switched to English", Toast.LENGTH_SHORT).show();
            } else {
                TTSPlayer.playVietnameseText("Lê Hoàng Robotics kính chào quý khách!", null);
                Toast.makeText(getContext(), "Đã chuyển sang Tiếng Việt", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        RobotMapManager.removeOnMapPlacesChangedListener(mMapPlacesListener);
        if (mBatteryReceiver != null) {
            try {
                if (getContext() != null) {
                    getContext().unregisterReceiver(mBatteryReceiver);
                }
            } catch (Throwable ignored) {
            }
            mBatteryReceiver = null;
        }
        if (mIsScanning360) {
            stop360ScanRelocalize(false);
        }
        if (mLocalizationCheckerRunnable != null) {
            mHandler.removeCallbacks(mLocalizationCheckerRunnable);
            mLocalizationCheckerRunnable = null;
        }
        if (RobotPosePersistenceManager.getInstance() != null) {
            RobotPosePersistenceManager.getInstance().stopPeriodicTracking();
        }
        if (mArrivalTimer != null) {
            mArrivalTimer.cancel();
            mArrivalTimer = null;
        }
        if (mDrawerTimer != null) {
            mDrawerTimer.cancel();
            mDrawerTimer = null;
        }
    }
}
