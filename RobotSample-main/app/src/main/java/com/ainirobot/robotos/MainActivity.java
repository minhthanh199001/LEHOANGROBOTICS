/*
 *  Copyright (C) 2017 OrionStar Technology Project
 *  Modified by Le Hoang Robotics 2026
 */

package com.ainirobot.robotos;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;

import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.robotos.fragment.DeliveryFragment;
import com.ainirobot.robotos.fragment.MainFragment;

public class MainActivity extends AppCompatActivity {

    private FrameLayout mContent;
    private static MainActivity mInstance;

    public static MainActivity getInstance() {
        return mInstance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInstance = MainActivity.this;
        setContentView(R.layout.activity_main);
        mContent = findViewById(R.id.container_content);

        // Luôn luôn khởi động thẳng vào màn hình giao hàng Kiosk
        showDeliveryScreen();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showDeliveryScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.container_content);
        if (current == null) {
            showDeliveryScreen();
        }
    }

    public void showDeliveryScreen() {
        switchFragment(DeliveryFragment.newInstance());
    }

    public void switchFragment(Fragment fragment) {
        try {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container_content, fragment, fragment.getClass().getName())
                    .commitAllowingStateLoss();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
