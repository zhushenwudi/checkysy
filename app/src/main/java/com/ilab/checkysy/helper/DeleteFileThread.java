package com.ilab.checkysy.helper;

import android.annotation.SuppressLint;
import android.util.Log;

import com.heima.easysp.SharedPreferencesUtils;
import com.ilab.checkysy.App;
import com.ilab.checkysy.Constants;
import com.ilab.checkysy.util.Util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import static com.ilab.checkysy.util.CrashHandler.restartApp;

public class DeleteFileThread extends Thread {
    private boolean restart;//是否重启
    private SharedPreferencesUtils sp;

    public DeleteFileThread(boolean shouldRestart, SharedPreferencesUtils sp) {
        restart = shouldRestart;
        this.sp = sp;
    }

    @Override
    public void run() {
        super.run();
        Log.e("aaa", "------------删除全部文件开始------------");
        App.getInstances().getDaoSession().getSuccessEntyDao().deleteAll();
        ArrayList<File> files = Util.orderByDate(Constants.path);
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date start = sdf.parse(sdf.format(new Date(files.get(0).lastModified())));
            sp.putLong("lastMovieTime", start.getTime());
        } catch (Exception e) {
            e.printStackTrace();
        }
        File file = new File(Constants.path);
        File[] f = file.listFiles();
        for (File value : f) {
            value.delete();
        }
        Log.e("aaa", "------------删除全部文件结束------------");
        if (restart) {
            Log.e("aaa", "------------删除完毕重启，准备下一个任务------------");
            restartApp(2000);
        }
    }
}
