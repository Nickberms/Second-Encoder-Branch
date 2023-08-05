package om.sstvencoder;

import android.os.Handler;

import om.sstvencoder.Output.WaveFileOutputContext;

class MainActivityMessenger {
    private final MainActivity mMainActivity;
    private final Handler mHandler;

    MainActivityMessenger(MainActivity activity) {
        mMainActivity = activity;
        mHandler = new Handler();
    }

    void carrySaveAsWaveIsDoneMessage(final WaveFileOutputContext context) {
        mHandler.post(() -> mMainActivity.completeSaving(context));
    }
}