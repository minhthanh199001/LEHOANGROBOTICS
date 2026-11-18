package com.lehoangrobotics.greetingbot;

import android.app.Application;
import android.os.Bundle;
import com.ainirobot.agent.AppAgent;
import com.ainirobot.agent.OnTranscribeListener;
import com.ainirobot.agent.action.Action;
import com.ainirobot.agent.base.Transcription;

/** One AgentOS client, with no registered AI actions or automatic motion planning. */
public final class GreetingApplication extends Application {
    interface VoiceListener { void transcription(Transcription transcription); }
    private AppAgent agent;
    private volatile VoiceListener voiceListener;
    private String agentError = "";

    @Override public void onCreate() {
        super.onCreate();
        try {
            getPackageManager().getPackageInfo("com.ainirobot.agentservice", 0);
            agent = new AppAgent(this) {
                @Override public void onCreate() {
                    setDisablePlan(true);
                    setMicrophoneMuted(true);
                    setEnableVoiceBar(false);
                    setOnTranscribeListener(new OnTranscribeListener() {
                        @Override public boolean onASRResult(Transcription text) {
                            VoiceListener listener = voiceListener;
                            if (listener == null) return false;
                            listener.transcription(text); return true;
                        }
                        @Override public boolean onTTSResult(Transcription text) { return false; }
                    });
                }
                @Override public boolean onExecuteAction(Action action, Bundle params) { return false; }
            };
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // Older RobotOS uses SkillApi instead.
        } catch (Exception error) {
            agentError = "Không khởi tạo được AgentOS: " + error.getMessage();
            android.util.Log.e("GreetingVoice", agentError, error);
        }
    }
    AppAgent agent() { return agent; }
    String agentError() { return agentError; }
    void voiceListener(VoiceListener listener) { voiceListener = listener; }
}
