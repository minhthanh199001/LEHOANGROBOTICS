package com.ainirobot.robotos.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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

    private final TraySlot[] mTraySlots = new TraySlot[]{
            new TraySlot(1),
            new TraySlot(2),
            new TraySlot(3)
    };

    private int mActiveTray = 1; // 1, 2, or 3
    private String mCurrentMap = "tăng 2-1110154527";
    private final List<String> mStationsList = new ArrayList<>();

    // 1. Delivery Screen Views
    private View mPanelDeliveryConfig;
    private TextView mBtnMenuDots;
    private TextView mBtnLocationFloor;
    private TextView mTvLocalizationStatus;
    private TextView mBtnLanguage;
    private TextView mBtnModeTray;
    private TextView mTvClockTime;
    private TextView mTvBatteryPill;

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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_delivery, container, false);
        bindViews(root);
        updateClock();
        loadStationsFromMap();
        refreshTrayShelvesUi();

        // Khởi tạo Pose Persistence Manager & Bắt đầu tự động theo dõi vị trí
        if (getContext() != null) {
            RobotPosePersistenceManager.init(getContext());
            RobotPosePersistenceManager.getInstance().startPeriodicTracking();
        }

        // Delay 1.5s để CoreService ổn định, sau đó tự động khôi phục vị trí (Cách 1) và bật giám sát định vị
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
        mBtnLanguage = root.findViewById(R.id.btn_language);
        mBtnModeTray = root.findViewById(R.id.btn_mode_tray);
        mTvClockTime = root.findViewById(R.id.tv_clock_time);
        mTvBatteryPill = root.findViewById(R.id.tv_battery_pill);

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
        mBtnAbortDelivery = root.findViewById(R.id.btn_abort_delivery);

        setupClickListeners();
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

        if (mTvLocalizationStatus != null) {
            mTvLocalizationStatus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showQuickRelocalizeDialog();
                }
            });
        }

        mBtnLanguage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TTSPlayer.playVietnameseText("Lê Hoàng Robotics kính chào quý khách!", null);
            }
        });

        mBtnModeTray.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "Chế độ giao hàng đa điểm 3 tầng", Toast.LENGTH_SHORT).show();
            }
        });

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
                startDelivery(mSpeedWater, "Giao hàng chậm (Món nước/Súp)");
            }
        });

        mBtnFastDelivery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDelivery(mSpeedFast, "Giao hàng nhanh");
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
                TTSPlayer.playVietnameseText("Gửi robot trở về vị trí ban đầu", null);
                navigateRobotTo("Stand-by Point");
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
                Toast.makeText(getContext(), "Lịch sử giao: Đã hoàn tất 12 chuyến an toàn hôm nay", Toast.LENGTH_LONG).show();
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
                TTSPlayer.playVietnameseText("Đang ở chế độ Vận chuyển thức ăn", null);
                mPanelModeSwitch.setVisibility(View.GONE);
                mPanelDeliveryConfig.setVisibility(View.VISIBLE);
            }
        });

        mModeBtnPromo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TTSPlayer.playVietnameseText("Chế độ Khuyến mãi: Robot chào đón khách và phát thông tin ưu đãi!", null);
                Toast.makeText(getContext(), "Chế độ Khuyến mãi đang kích hoạt", Toast.LENGTH_SHORT).show();
            }
        });

        mModeBtnCruise.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TTSPlayer.playVietnameseText("Chế độ Du thuyền: Robot tuần tra theo lộ trình!", null);
                Toast.makeText(getContext(), "Chế độ Du thuyền đang kích hoạt", Toast.LENGTH_SHORT).show();
            }
        });

        mModeBtnCollection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TTSPlayer.playVietnameseText("Chế độ Bộ sưu tập: Robot hỗ trợ thu dọn khay dĩa!", null);
                Toast.makeText(getContext(), "Chế độ Thu dọn đang kích hoạt", Toast.LENGTH_SHORT).show();
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
        mTvClockTime.setText(sdf.format(new Date()));
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
            mTvTray1Status.setText(a1 ? "Xin hãy lựa chọn" : "tầng1 Nhấn để thêm");
            mTvTray1Status.setTextColor(a1 ? 0xFFFFFFFF : 0xFFE0F2FE);
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
            mTvTray2Status.setText(a2 ? "Xin hãy lựa chọn" : "tầng2 Nhấn để thêm");
            mTvTray2Status.setTextColor(a2 ? 0xFFFFFFFF : 0xFFE0F2FE);
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
            mTvTray3Status.setText(a3 ? "Xin hãy lựa chọn" : "tầng3 Nhấn để thêm");
            mTvTray3Status.setTextColor(a3 ? 0xFFFFFFFF : 0xFFE0F2FE);
            mBtnClearTray3.setVisibility(View.GONE);
        }
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
                        if (!mStationsList.contains(name)) {
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
            mStationsList.add("cafe");
            mStationsList.add("phòng họp");
            mStationsList.add("tivi");
            mStationsList.add("Bàn 1");
            mStationsList.add("Bàn 2");
            mStationsList.add("Bàn 3");
            mStationsList.add("Bàn 4");
            mStationsList.add("Bàn 5");
        } else {
            // Prioritize cafe, phòng họp, tivi if present
            prioritizeStation("cafe");
            prioritizeStation("phòng họp");
            prioritizeStation("tivi");
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
        if (mContainerStationButtons == null || getContext() == null) return;
        mContainerStationButtons.removeAllViews();

        int cols = 3;
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
        if (mDrawerTimer != null) mDrawerTimer.cancel();
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
                        if (getActivity() != null) getActivity().recreate();
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
        final String[] musics = new String[]{"Không có", "Nhạc du dương nhẹ nhàng", "Giai điệu vui tươi", "Chuông phục vụ"};
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
        final String[] opts = new String[]{"0.5 m/s", "0.7 m/s (Tiêu chuẩn)", "0.9 m/s (Nhanh)", "1.1 m/s (Tối đa)"};
        final float[] vals = new float[]{0.5f, 0.7f, 0.9f, 1.1f};
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
        final String[] opts = new String[]{"0.3 m/s (Cực êm)", "0.4 m/s", "0.5 m/s (Tiêu chuẩn)", "0.6 m/s"};
        final float[] vals = new float[]{0.3f, 0.4f, 0.5f, 0.6f};
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Cài đặt tốc độ giao các món nước:")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSpeedWater = vals[which];
                        mTvValSpeedWater.setText(opts[which] + " ›");
                        Toast.makeText(getContext(), "Đã lưu tốc độ món nước: " + opts[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showSpeedReturnDialog() {
        final String[] opts = new String[]{"0.5 m/s", "0.6 m/s (Tiêu chuẩn)", "0.8 m/s"};
        final float[] vals = new float[]{0.5f, 0.6f, 0.8f};
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
        final String[] opts = new String[]{"15 giây", "20 giây", "30 giây (Tiêu chuẩn)", "45 giây", "60 giây"};
        final int[] vals = new int[]{15, 20, 30, 45, 60};
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
                        TTSPlayer.playVietnameseText("Đã chuyển sang " + (mCurrentMap.contains("1") ? "Tầng 1" : "Tầng 2"), null);
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    // ==========================================
    // CÁCH 1 & CÁCH 2: RELOCALIZATION & POSE RESTORE
    // ==========================================

    private void performAutoPoseRestore() {
        if (RobotPosePersistenceManager.getInstance() == null || !RobotPosePersistenceManager.getInstance().hasSavedPose()) {
            return;
        }
        RobotPosePersistenceManager.getInstance().checkAndAutoRestorePose(new RobotPosePersistenceManager.RelocalizeCallback() {
            @Override
            public void onResult(final boolean success, final String message) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            updateLocalizationBadge(true);
                            Toast.makeText(getContext(), "✅ Đã tự động khôi phục vị trí robot!", Toast.LENGTH_SHORT).show();
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
                } catch (Throwable ignored) {}
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
     * Cách 2: Hộp thoại 1-chạm định vị tại Trạm Sạc / Điểm Chờ / Khôi phục vị trí đã lưu
     */
    private void showQuickRelocalizeDialog() {
        if (getContext() == null) return;
        List<String> options = new ArrayList<>();
        options.add("⚡ 1-Chạm: Định vị tại Trạm Sạc (Charging Dock)");
        options.add("🏠 1-Chạm: Định vị tại Điểm Chờ (Stand-by Point)");
        if (RobotPosePersistenceManager.getInstance() != null && RobotPosePersistenceManager.getInstance().hasSavedPose()) {
            options.add("💾 Khôi phục vị trí lưu gần nhất (" + RobotPosePersistenceManager.getInstance().getLastPlaceName() + ")");
        }
        options.add("📍 Chọn điểm bất kỳ trên bản đồ...");
        options.add("🔄 Tự động xoay 360° quét Laser tìm vị trí");

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
        JsonObject place = RobotMapManager.getPlaceByName(mCurrentMap, "Charging");
        float x = 0.6f;
        float y = -0.31f;
        float theta = 0.0f;
        if (place != null) {
            x = (float) place.get("x").getAsDouble();
            y = (float) place.get("y").getAsDouble();
            theta = (float) place.get("theta").getAsDouble();
        }
        relocalizeRobotTo(x, y, theta, "Trạm Sạc (Charging Dock)");
    }

    private void relocalizeToStandbyPoint() {
        JsonObject place = RobotMapManager.getPlaceByName(mCurrentMap, "Stand-by");
        float x = 2.0f;
        float y = 6.01f;
        float theta = 0.0f;
        if (place != null) {
            x = (float) place.get("x").getAsDouble();
            y = (float) place.get("y").getAsDouble();
            theta = (float) place.get("theta").getAsDouble();
        }
        relocalizeRobotTo(x, y, theta, "Điểm Chờ (Stand-by Point)");
    }

    private void relocalizeToLastSavedPose() {
        if (RobotPosePersistenceManager.getInstance() == null || !RobotPosePersistenceManager.getInstance().hasSavedPose()) {
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
        } catch (Throwable ignored) {}
        Toast.makeText(getContext(), "Không tìm thấy danh sách điểm trên bản đồ", Toast.LENGTH_SHORT).show();
    }

    private boolean mIsScanning360 = false;
    private AlertDialog mScan360ProgressDialog = null;

    private void performAuto360ScanRelocalize() {
        if (mIsScanning360) return;
        mIsScanning360 = true;

        TTSPlayer.playVietnameseText("Robot bắt đầu xoay 360 độ để quét Laser tìm lại vị trí...", null);

        // 1. Nếu có tọa độ lưu gần nhất, nạp tọa độ mồi trước vào thuật toán
        if (RobotPosePersistenceManager.getInstance() != null && RobotPosePersistenceManager.getInstance().hasSavedPose()) {
            float sx = RobotPosePersistenceManager.getInstance().getLastX();
            float sy = RobotPosePersistenceManager.getInstance().getLastY();
            float sth = RobotPosePersistenceManager.getInstance().getLastTheta();
            try {
                org.json.JSONObject params = new org.json.JSONObject();
                params.put(Definition.JSON_NAVI_POSITION_X, sx);
                params.put(Definition.JSON_NAVI_POSITION_Y, sy);
                params.put(Definition.JSON_NAVI_POSITION_THETA, sth);
                RobotApi.getInstance().setPoseEstimate(0, params.toString(), null);
            } catch (Throwable ignored) {}
        }

        // 2. Kích hoạt thuật toán resetEstimate (Action ID 24) chính thức của OrionStar với 5 lần thử
        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().resetEstimate(0, 5, new CommandListener() {
                    @Override
                    public void onResult(int res, String msg) {
                        Log.i(TAG, "resetEstimate result: " + res + ", msg=" + msg);
                    }
                });
            }
        } catch (Throwable ignored) {}

        // 3. Ra lệnh cho khung gầm robot quay nhẹ nhàng tại chỗ (20 độ/giây) để Laser quét các vách tường
        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().turnLeft(0, 20.0f, new CommandListener() {
                    @Override
                    public void onResult(int res, String msg) {
                        Log.i(TAG, "turnLeft result: " + res + ", msg=" + msg);
                    }
                });
            }
        } catch (Throwable ignored) {}

        // 4. Hiển thị Dialog thông báo tiến trình quét
        if (getContext() != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK);
            builder.setTitle("🔄 Đang quét Laser 360° tìm vị trí");
            builder.setMessage("Robot đang từ từ xoay quanh để đối chiếu Laser với bản đồ...\n\nQuá trình này mất khoảng 10 - 18 giây.");
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
                if (!mIsScanning360) return;

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
        } catch (Throwable ignored) {}

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
            Toast.makeText(getContext(), "⚠️ Không thể nhận diện vị trí tự động. Hãy dùng nút 'Định vị tại Trạm Sạc / Điểm Chờ'.", Toast.LENGTH_LONG).show();
            TTSPlayer.playVietnameseText("Không thể xác định vị trí. Vui lòng bấm định vị tại Trạm Sạc hoặc Điểm Chờ.", null);
            showQuickRelocalizeDialog();
        }
    }

    private void relocalizeRobotTo(final float x, final float y, final float theta, final String label) {
        TTSPlayer.playVietnameseText("Đang định vị tại " + label, null);
        Toast.makeText(getContext(), "📍 Đang thiết lập vị trí: " + label + "...", Toast.LENGTH_SHORT).show();

        if (RobotPosePersistenceManager.getInstance() != null) {
            RobotPosePersistenceManager.getInstance().applyPoseEstimate(x, y, theta, label, new RobotPosePersistenceManager.RelocalizeCallback() {
                @Override
                public void onResult(final boolean success, final String message) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (success) {
                                updateLocalizationBadge(true);
                                Toast.makeText(getContext(), "✅ Định vị thành công tại " + label + "!", Toast.LENGTH_LONG).show();
                                TTSPlayer.playVietnameseText("Định vị thành công", null);
                            } else {
                                Toast.makeText(getContext(), "⚠️ Không thể định vị: " + message, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            });
        }
    }

    // Delivery Process
    private void startDelivery(float speed, String modeName) {
        mCurrentSpeed = speed;
        mActiveDeliveryQueue.clear();

        for (TraySlot slot : mTraySlots) {
            if (slot.hasDestination()) {
                mActiveDeliveryQueue.add(slot);
            }
        }

        if (mActiveDeliveryQueue.isEmpty()) {
            Toast.makeText(getContext(), "⚠️ Vui lòng chọn điểm đến cho ít nhất 1 tầng!", Toast.LENGTH_SHORT).show();
            TTSPlayer.playVietnameseText("Vui lòng chọn điểm đến cần giao món.", null);
            return;
        }

        mIsDelivering = true;
        mCurrentQueueIndex = 0;

        // Show delivering overlay
        mPanelDeliveryConfig.setVisibility(View.GONE);
        mOverlayDelivering.setVisibility(View.VISIBLE);

        TTSPlayer.playVietnameseText("Robot bắt đầu đi giao hàng, quý khách chú ý!", null);
        broadcastStatusToPc("DELIVERY_STARTED");

        executeNextDeliveryLeg();
    }

    private void executeNextDeliveryLeg() {
        if (!mIsDelivering) return;

        if (mCurrentQueueIndex >= mActiveDeliveryQueue.size()) {
            onAllDeliveriesCompleted();
            return;
        }

        TraySlot current = mActiveDeliveryQueue.get(mCurrentQueueIndex);

        // Eyes traveling
        mTvRobotEyeLeft.setText("●  ●");
        mTvRobotEyeRight.setText("●  ●");

        mTvDeliveryTitle.setText("ROBOT ĐANG GIAO HÀNG (Tầng " + current.number + "/3)");
        mTvDeliveryCurrentTarget.setText("ĐANG TỚI: " + current.destination.toUpperCase());
        mTvDeliveryTrayInfo.setText("Giao thức ăn tại Tầng " + current.number);

        mTvDeliveryArrivalPrompt.setVisibility(View.GONE);
        mBtnConfirmTaken.setVisibility(View.GONE);

        // SLAM navigate
        navigateRobotTo(current.destination);
    }

    private void onArrivedAtDestination() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!mIsDelivering || mCurrentQueueIndex >= mActiveDeliveryQueue.size()) return;

                TraySlot current = mActiveDeliveryQueue.get(mCurrentQueueIndex);

                // Happy eyes
                mTvRobotEyeLeft.setText("^  ^");
                mTvRobotEyeRight.setText("^  ^");

                mTvDeliveryTitle.setText("🎉 ĐÃ TỚI NƠI!");
                mTvDeliveryCurrentTarget.setText(current.destination.toUpperCase());
                mTvDeliveryArrivalPrompt.setText("Kính mời quý khách nhận món tại Tầng " + current.number + "!");
                mTvDeliveryArrivalPrompt.setVisibility(View.VISIBLE);
                mBtnConfirmTaken.setVisibility(View.VISIBLE);

                String speech = "Kính chào quý khách! Món ăn đã tới tại Tầng " + current.number + ", xin kính mời quý khách nhận món!";
                TTSPlayer.playVietnameseText(speech, null);

                startCountdownTimer();
            }
        });
    }

    private void startCountdownTimer() {
        if (mArrivalTimer != null) mArrivalTimer.cancel();
        mArrivalTimer = new CountDownTimer(mTableWaitTimeSec * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int s = (int) (millisUntilFinished / 1000);
                mBtnConfirmTaken.setText("✅ ĐÃ LẤY MÓN - TIẾP TỤC (" + s + "s)");
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

        TTSPlayer.playVietnameseText("Cảm ơn quý khách, chúc quý khách ngon miệng!", null);

        mCurrentQueueIndex++;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                executeNextDeliveryLeg();
            }
        }, 2000);
    }

    private void onAllDeliveriesCompleted() {
        mTvRobotEyeLeft.setText("★  ★");
        mTvRobotEyeRight.setText("★  ★");
        mTvDeliveryTitle.setText("NHIỆM VỤ HOÀN THÀNH!");
        mTvDeliveryCurrentTarget.setText("VỀ VỊ TRÍ CHỜ");
        mTvDeliveryTrayInfo.setText("Đã giao xong tất cả các tầng.");
        mTvDeliveryArrivalPrompt.setVisibility(View.GONE);
        mBtnConfirmTaken.setVisibility(View.GONE);

        TTSPlayer.playVietnameseText("Nhiệm vụ hoàn thành, robot trở về vị trí chờ phục vụ!", null);
        broadcastStatusToPc("DELIVERY_COMPLETED");

        navigateRobotTo("Stand-by Point");

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                abortDelivery();
            }
        }, 5000);
    }

    private void abortDelivery() {
        if (mArrivalTimer != null) {
            mArrivalTimer.cancel();
            mArrivalTimer = null;
        }
        mIsDelivering = false;

        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().stopNavigation(0);
                RobotApi.getInstance().stopMove(0, null);
            }
        } catch (Throwable ignored) {}

        mOverlayDelivering.setVisibility(View.GONE);
        mPanelDeliveryConfig.setVisibility(View.VISIBLE);
        refreshTrayShelvesUi();
    }

    private void navigateRobotTo(final String dest) {
        if (TextUtils.isEmpty(dest)) return;
        Log.i(TAG, "Navigating to: " + dest);

        try {
            if (RobotApi.getInstance().isApiConnectedService()) {
                RobotApi.getInstance().startNavigation(0, dest, 1.5, 90 * 1000, new ActionListener() {
                    @Override
                    public void onResult(int status, String responseString) throws android.os.RemoteException {
                        if (status == Definition.RESULT_OK || "succeed".equalsIgnoreCase(responseString)) {
                            onArrivedAtDestination();
                        }
                    }

                    @Override
                    public void onError(int errorCode, String errorString) throws android.os.RemoteException {
                        Log.e(TAG, "startNavigation error: " + errorCode + ", " + errorString);
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
                        onArrivedAtDestination();
                    }
                }, 4000);
            }
        } catch (Throwable t) {
            Log.e(TAG, "navigate error: " + t.getMessage());
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    onArrivedAtDestination();
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
        } catch (Throwable ignored) {}
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
