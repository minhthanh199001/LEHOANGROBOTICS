package com.ainirobot.robotos.bridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.CommandListener;

import org.json.JSONObject;

/**
 * Quản lý lưu trữ vị trí robot liên tục vào bộ nhớ không bay hơi (Persistent Flash)
 * và tự động khôi phục tọa độ (Auto Pose Restore) khi robot bật nguồn lại.
 */
public class RobotPosePersistenceManager {
    private static final String TAG = "PosePersistence";
    private static final String PREF_NAME = "lehoang_robot_pose_persistence";

    private static final String KEY_X = "last_pos_x";
    private static final String KEY_Y = "last_pos_y";
    private static final String KEY_THETA = "last_pos_theta";
    private static final String KEY_MAP = "last_pos_map";
    private static final String KEY_TIMESTAMP = "last_pos_timestamp";
    private static final String KEY_PLACE_NAME = "last_pos_place_name";
    private static final String KEY_HAS_SAVED = "has_saved_pose";

    private static RobotPosePersistenceManager sInstance;
    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsTracking = false;

    private RobotPosePersistenceManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized void init(Context context) {
        if (sInstance == null) {
            sInstance = new RobotPosePersistenceManager(context);
        }
    }

    public static RobotPosePersistenceManager getInstance() {
        return sInstance;
    }

    /**
     * Bắt đầu vòng lặp theo dõi vị trí: Cứ 5 giây tự động lưu tọa độ nếu robot đang có định vị
     */
    public void startPeriodicTracking() {
        if (mIsTracking) return;
        mIsTracking = true;
        mHandler.post(mTrackRunnable);
    }

    public void stopPeriodicTracking() {
        mIsTracking = false;
        mHandler.removeCallbacks(mTrackRunnable);
    }

    private final Runnable mTrackRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsTracking) return;
            pollAndSaveCurrentPose(null);
            mHandler.postDelayed(this, 5000);
        }
    };

    /**
     * Lấy tọa độ hiện tại từ RobotApi và lưu vào SharedPreferences
     */
    public void pollAndSaveCurrentPose(final String optionalPlaceName) {
        try {
            if (!RobotApi.getInstance().isApiConnectedService()) return;

            RobotApi.getInstance().isRobotEstimate(0, new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    if ("true".equalsIgnoreCase(message)) {
                        RobotApi.getInstance().getPosition(0, new CommandListener() {
                            @Override
                            public void onResult(int res, String msg) {
                                try {
                                    JSONObject json = new JSONObject(msg);
                                    double x = json.getDouble(Definition.JSON_NAVI_POSITION_X);
                                    double y = json.getDouble(Definition.JSON_NAVI_POSITION_Y);
                                    double theta = json.getDouble(Definition.JSON_NAVI_POSITION_THETA);
                                    savePose((float) x, (float) y, (float) theta, optionalPlaceName);
                                } catch (Throwable ignored) {}
                            }
                        });
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    /**
     * Ghi đè tọa độ vào bộ nhớ flash
     */
    public synchronized void savePose(float x, float y, float theta, String placeName) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putFloat(KEY_X, x);
        editor.putFloat(KEY_Y, y);
        editor.putFloat(KEY_THETA, theta);
        editor.putLong(KEY_TIMESTAMP, System.currentTimeMillis());
        editor.putBoolean(KEY_HAS_SAVED, true);
        if (!TextUtils.isEmpty(placeName)) {
            editor.putString(KEY_PLACE_NAME, placeName);
        }
        editor.apply();
        Log.i(TAG, "Đã lưu vị trí Robot: (" + x + ", " + y + ", θ=" + theta + ") tại: " + placeName);
    }

    public boolean hasSavedPose() {
        return mPrefs.getBoolean(KEY_HAS_SAVED, false);
    }

    public float getLastX() { return mPrefs.getFloat(KEY_X, 0f); }
    public float getLastY() { return mPrefs.getFloat(KEY_Y, 0f); }
    public float getLastTheta() { return mPrefs.getFloat(KEY_THETA, 0f); }
    public String getLastPlaceName() { return mPrefs.getString(KEY_PLACE_NAME, "Vị trí trước khi tắt"); }
    public long getLastTimestamp() { return mPrefs.getLong(KEY_TIMESTAMP, 0L); }

    /**
     * Cách 1: Tự động kiểm tra nếu mất vị trí khi mở App, tự động nạp lại vị trí đã lưu
     */
    public void checkAndAutoRestorePose(final RelocalizeCallback callback) {
        try {
            if (!RobotApi.getInstance().isApiConnectedService()) {
                if (callback != null) callback.onResult(false, "RobotApi chưa kết nối");
                return;
            }

            RobotApi.getInstance().isRobotEstimate(0, new CommandListener() {
                @Override
                public void onResult(int result, String message) {
                    if ("true".equalsIgnoreCase(message)) {
                        Log.i(TAG, "Robot đã có vị trí sẵn, không cần khôi phục.");
                        if (callback != null) callback.onResult(true, "Robot đã có vị trí sẵn");
                    } else {
                        // Robot bị mất vị trí -> Tự động nạp vị trí lưu trước đó
                        if (hasSavedPose()) {
                            float x = getLastX();
                            float y = getLastY();
                            float theta = getLastTheta();
                            String place = getLastPlaceName();
                            Log.i(TAG, "Robot bị mất vị trí. Đang tự động khôi phục tọa độ đã lưu: (" + x + ", " + y + ", θ=" + theta + ")");
                            applyPoseEstimate(x, y, theta, "Tự động khôi phục (" + place + ")", callback);
                        } else {
                            Log.w(TAG, "Chưa có vị trí lưu trước đó để tự khôi phục.");
                            if (callback != null) callback.onResult(false, "Chưa có vị trí lưu");
                        }
                    }
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "checkAndAutoRestorePose error: " + t.getMessage());
            if (callback != null) callback.onResult(false, t.getMessage());
        }
    }

    /**
     * Nạp tọa độ bất kỳ (Trạm sạc, Điểm chờ, Vị trí đã lưu) vào hạt định vị của SLAM
     */
    public void applyPoseEstimate(final float x, final float y, final float theta, final String label, final RelocalizeCallback callback) {
        try {
            JSONObject params = new JSONObject();
            params.put(Definition.JSON_NAVI_POSITION_X, x);
            params.put(Definition.JSON_NAVI_POSITION_Y, y);
            params.put(Definition.JSON_NAVI_POSITION_THETA, theta);

            RobotApi.getInstance().setPoseEstimate(0, params.toString(), new CommandListener() {
                @Override
                public void onResult(int res, String msg) {
                    boolean ok = (res == Definition.RESULT_OK || "succeed".equalsIgnoreCase(msg));
                    Log.i(TAG, "setPoseEstimate kết quả: " + res + ", message=" + msg + " (" + label + ")");
                    if (ok) {
                        savePose(x, y, theta, label);
                    }
                    if (callback != null) {
                        callback.onResult(ok, msg);
                    }
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "applyPoseEstimate error: " + t.getMessage(), t);
            if (callback != null) callback.onResult(false, t.getMessage());
        }
    }

    public interface RelocalizeCallback {
        void onResult(boolean success, String message);
    }
}

