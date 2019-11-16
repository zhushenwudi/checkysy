package com.ilab.checkysy.helper;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ilab.checkysy.App;
import com.ilab.checkysy.Constants;
import com.ilab.checkysy.cloud.EZCloudRecordFile;
import com.ilab.checkysy.database.SkipEnty;
import com.ilab.checkysy.database.SkipEntyDao;
import com.ilab.checkysy.util.NetSpeed;
import com.ilab.checkysy.util.RxTimerUtil;
import com.ilab.checkysy.util.Util;
import com.videogo.openapi.EZOpenSDKListener;
import com.videogo.stream.EZCloudStreamDownload;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ilab.checkysy.util.CrashHandler.restartApp;

public class DownloadHelper {
    private static AtomicInteger downloadSuccessCount = new AtomicInteger(0);
    private static AtomicInteger downloadFailCount = new AtomicInteger(0);
    private Handler mHandler;
    private Map<String, EZCloudRecordFile> eZCMap;
    private LinkedBlockingQueue<EZCloudRecordFile> queue = new LinkedBlockingQueue<>();
    private SkipEntyDao greenSkipEntyDao;
    private int previousCount = -1;
    private Context mContext;
    private int restartCount = 0;  //重启计数

    public DownloadHelper(Context mContext, Handler mHandler, Map<String, EZCloudRecordFile> map) {
        this.mHandler = mHandler;
        this.eZCMap = map;
        this.mContext = mContext;
    }

    public void execute() {
        if (eZCMap.size() <= 0) {
            return;
        }
        greenSkipEntyDao = App.getInstances().getDaoSession().getSkipEntyDao();
        //一次性把文件丢到队列里面
        for (Map.Entry<String, EZCloudRecordFile> entry : eZCMap.entrySet()) {
            try {
                queue.put(entry.getValue());
            } catch (Exception e) {
                e.printStackTrace();
                restartApp(2000);
            }
        }
        NetSpeed netSpeed = new NetSpeed();
        RxTimerUtil.getInstance().interval(5 * 60 * 1000, 5 * 60 * 1000, number ->
                new Thread(() -> {
                    Log.e("aaa", "检索无效视频定时器start");
                    Util.scanSD_ValidVideo(false, 60 * 1000);
                    Log.e("aaa", "检索无效视频定时器end");
                }).start()
        );
        //开启定时器
        try {
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            executor.scheduleWithFixedDelay(() -> {
                int current = downloadSuccessCount.get() + downloadFailCount.get();
                if (current > previousCount) {
                    previousCount = current;
                    restartCount = 0;
                    Log.e("aaa", "------------定时器初次执行-------------");
                } else if (current == previousCount) {
                    int speed = 0;
                    for (int i = 0; i < 4; i++) {
                        speed += netSpeed.getNetSpeed(mContext.getApplicationInfo().uid).intValue();
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if ((speed / 4) < 50) {
                        Log.e("aaa", "流量已停止");
                        restartApp(2000);
                    } else {
                        Log.e("aaa", "定时器启动------网络崩坏中");
                        restartCount++;
                        //10分钟时间，下载数没有变化，则重启重试
                        if (restartCount > (8 / Constants.DELAYTIME)) {
                            restartApp(2000);
                            Log.e("aaa", "重启手机中...");
                        }
                    }
                }
            }, Constants.DELAYTIME * 2, Constants.DELAYTIME * 2, TimeUnit.MINUTES);
        } catch (Exception e) {
            Log.e("aaa", "------------定时器异常------------");
            e.printStackTrace();
            restartApp(2000);
        }
        //创建下载的文件夹
        File path = new File(Constants.path);
        if (!path.exists() || !path.isDirectory()) {
            path.mkdirs();
        }
        ExecutorService threadPool;
        if (eZCMap.size() > Constants.CPU_COUNT * 2 + 1) {
            threadPool = Executors.newFixedThreadPool(Constants.CPU_COUNT * 2 + 1);
        } else {
            threadPool = Executors.newFixedThreadPool(eZCMap.size());
        }
        int a = eZCMap.size() > 70 ? 70 : eZCMap.size();
        for (int k = 0; k < a; k++) {
            threadPool.execute(this::startNext);
        }
    }

    private void startNext() {
        if (!queue.isEmpty()) {
            try {
                startDownAnimation(queue.take());
            } catch (Exception e) {
                e.printStackTrace();
                restartApp(2000);
            }
        }
    }

    private void startDownAnimation(final EZCloudRecordFile cloudFile) {
        long start = cloudFile.getStartTime().getTimeInMillis();
        long end = cloudFile.getStopTime().getTimeInMillis();
        int length = (int) Math.ceil((end - start) / 1000);
        String strRecordFile = Constants.path + cloudFile.getFileId() + "_" + start + "_" + length + "_" + cloudFile.getDeviceSerial() + ".MP4";
        if (length > 30 * 60) {
            downloadSuccessCount.getAndIncrement();
            Log.e("aaa", "检测到超过30min的大文件，跳过");
            Message message = Message.obtain();
            message.what = Constants.DOWN_SUCCESS;
            message.arg1 = downloadSuccessCount.get();
            message.arg2 = downloadFailCount.get();
            mHandler.sendMessage(message);
            SkipEnty skipEnty = new SkipEnty();
            skipEnty.setFileName(strRecordFile);
            greenSkipEntyDao.insert(skipEnty);
            testReboot();
            startNext();
        }
        File file = new File(strRecordFile);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        EZCloudStreamDownload ezStreamDownload = new EZCloudStreamDownload(strRecordFile, cloudFile);
        ezStreamDownload.setStreamDownloadCallback(new EZOpenSDKListener.EZStreamDownloadCallback() {
            @Override
            public void onSuccess(final String filepath) {
                downloadSuccessCount.getAndIncrement();
                Log.e("aaa", "下载成功==" + downloadSuccessCount);
                Message message = Message.obtain();
                message.what = Constants.DOWN_SUCCESS;
                message.arg1 = downloadSuccessCount.get();
                message.arg2 = downloadFailCount.get();
                mHandler.sendMessage(message);
                Log.i("aaa", filepath);
                testReboot();
                startNext();
            }

            @Override
            public void onError(final EZOpenSDKListener.EZStreamDownloadError code) {
                int count = cloudFile.getRetry();
                if (count <= 2) {
                    //重试两次，第三次就放到失败列表里面，不再重试。
                    try {
                        if (code == EZOpenSDKListener.EZStreamDownloadError.ERROR_EZSTREAM_DOWNLOAD_VERIFYCODE) {
                            Log.e("aaa", "下载失败===摄像头有加密" + cloudFile.getFileId() + "sn==" + cloudFile.getDeviceSerial());
                            SkipEnty skipEnty = new SkipEnty();
                            skipEnty.setFileName(strRecordFile);
                            greenSkipEntyDao.insert(skipEnty);
                            downloadSuccessCount.getAndIncrement();
                            Message message = Message.obtain();
                            message.what = Constants.DOWN_FAILED;
                            message.arg1 = downloadSuccessCount.get();
                            message.arg2 = downloadFailCount.get();
                            mHandler.sendMessage(message);
                            testReboot();
                        } else {
                            cloudFile.setRetry(++count);
                            queue.put(cloudFile);
//                            Log.e("aaa", "下载失败===重试第" + count + "次:" + cloudFile.getFileId() + cloudFile.getCoverPic());
                            Log.e("aaa", "下载失败===重试第" + count + "次:" + cloudFile.getFileId() + ",code==" + code + ",sn==" + cloudFile.getDeviceSerial());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        restartApp(2000);
                    }
                } else {
                    downloadFailCount.getAndIncrement();
                    Log.e("aaa", "下载失败==" + downloadFailCount + ",code==" + code);
                    Message message = Message.obtain();
                    message.what = Constants.DOWN_FAILED;
                    message.arg1 = downloadSuccessCount.get();
                    message.arg2 = downloadFailCount.get();
                    mHandler.sendMessage(message);
                    testReboot();
                }
                startNext();
            }
        });
        ezStreamDownload.start();
    }

    private void testReboot() {
        if (downloadSuccessCount.get() + downloadFailCount.get() >= eZCMap.size())
            restartApp(7000);
    }
}