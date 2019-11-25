package com.ilab.checkysy.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.heima.easysp.SharedPreferencesUtils;
import com.ilab.checkysy.App;
import com.ilab.checkysy.Constants;
import com.ilab.checkysy.cloud.QueryPlayBackCloudListAsyncTask;
import com.ilab.checkysy.cloud.QueryPlayBackListTaskCallback;
import com.ilab.checkysy.entity.CloudPartInfoFileEx;
import com.ilab.checkysy.util.Util;
import com.videogo.openapi.EZOpenSDK;
import com.videogo.openapi.bean.EZDeviceInfo;
import com.videogo.openapi.bean.resp.CloudPartInfoFile;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.ilab.checkysy.Constants.QUERY_ING;

public class QueryHelper {
    private Timer timer;
    private QueryPlayBackCloudListAsyncTask queryPlayBackCloudListAsyncTask;
    private Handler mHandler;
    private String cameraSerial;
    private int currentIndex = 0;
    private int addCount = 0;
    private Context context;
    private List<CloudPartInfoFile> cloudPartInfoFiles = new ArrayList<>();
    private SharedPreferencesUtils sp = App.getInstances().getSp();
    private String[] cameraSerials;

    public QueryHelper(String cameraSerial, Handler mHandler, Context context) {
        this.cameraSerial = cameraSerial;
        this.mHandler = mHandler;
        this.context = context;
    }

    public List<CloudPartInfoFile> getCloudPartInfoFiles() {
        return cloudPartInfoFiles;
    }

    public void startQueryCould() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                new GetCamersInfoListTask().execute();
            }
        }, 1000);
    }

    private void startQueryCouldList(List<EZDeviceInfo> result, Date date) {
        if (cameraSerial.length() == 9) {
            cameraSerials = new String[1];
            cameraSerials[0] = cameraSerial;
        } else {
            List<String> ca = new Gson().fromJson(cameraSerial, new TypeToken<List<String>>() {
            }.getType());
            cameraSerials = ca.toArray(new String[0]);
        }
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (addCount == result.size() * cameraSerials.length) {
                    timer.cancel();
                    Log.e("aaa", "查询完毕结束了");
                    Message msg = new Message();
                    msg.what = Constants.QUERY_END;
                    msg.arg1 = cloudPartInfoFiles.size();
                    mHandler.sendMessage(msg);
                }
            }
        }, 0, 500);
        for (; currentIndex < result.size(); currentIndex++) {
            for (int i = 0; i < cameraSerials.length; i++) {
                if (result.get(currentIndex).getDeviceSerial().equals(cameraSerials[i])) {
                    queryPlayBackCloudListAsyncTask = new QueryPlayBackCloudListAsyncTask(cameraSerials[i], 1, new QueryPlayBackListTaskCallback() {
                        @Override
                        public void queryHasNoData() {
                        }

                        @Override
                        public void queryOnlyHasLocalFile() {
                        }

                        @Override
                        public void queryOnlyLocalNoData() {
                        }

                        @Override
                        public void queryLocalException() {
                        }

                        @Override
                        public void queryCloudSucess(List<CloudPartInfoFileEx> cloudPartInfoFileEx, int queryMLocalStatus, List<CloudPartInfoFile> cloudPartInfoFile) {
                            cloudPartInfoFiles.addAll(cloudPartInfoFile);
                            Message message = Message.obtain();
                            message.what = QUERY_ING;
                            message.arg1 = cloudPartInfoFiles.size();
                            addCount++;
                            mHandler.sendMessage(message);
                        }

                        @Override
                        public void queryLocalSucess(List<CloudPartInfoFileEx> cloudPartInfoFileEx, int position, List<CloudPartInfoFile> cloudPartInfoFile) {
                        }

                        @Override
                        public void queryLocalNoData() {
                        }

                        @Override
                        public void queryException() {
                            addCount++;
                        }

                        @Override
                        public void queryTaskOver(int type, int queryMode, int queryErrorCode, String detail) {
                        }
                    });
                    queryPlayBackCloudListAsyncTask.setCalendar(date);
                    queryPlayBackCloudListAsyncTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
                } else addCount++;
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    class GetCamersInfoListTask extends AsyncTask<Void, Void, List<EZDeviceInfo>> {
        @Override
        protected List<EZDeviceInfo> doInBackground(Void... params) {
            List<EZDeviceInfo> devices = new ArrayList<>();
            try {
                devices = EZOpenSDK.getInstance().getDeviceList(0, 1000);
            } catch (Exception e) {
                Util.restartApp(context, 2000);
                e.printStackTrace();
            }
            Log.e("aaa", "-------查询摄像头完毕，共计====" + devices.size());
            return devices;
        }

        @Override
        protected void onPostExecute(List<EZDeviceInfo> result) {
            super.onPostExecute(result);
            @SuppressLint("SimpleDateFormat")
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String customDate;
            if (result.size() > 0) {
                customDate = sp.getString("customDate");
                if (customDate != null) {
                    Date newDate = null;
                    try {
                        newDate = sdf.parse(customDate);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    startQueryCouldList(result, newDate);
                } else {
                    Date date = new Date();
                    //减去一天
                    long ss = date.getTime() - 1000 * 60 * 60 * 24;
                    date.setTime(ss);
                    customDate = sdf.format(date);
                    sp.putString("customDate", customDate);
                    startQueryCouldList(result, date);
                }
            }
        }
    }
}
