package com.ilab.checkysy;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.heima.easysp.SharedPreferencesUtils;
import com.ilab.checkysy.cloud.EZCloudRecordFile;
import com.ilab.checkysy.database.ErrorFileDao;
import com.ilab.checkysy.database.SkipEnty;
import com.ilab.checkysy.database.SkipEntyDao;
import com.ilab.checkysy.database.SuccessEnty;
import com.ilab.checkysy.database.SuccessEntyDao;
import com.ilab.checkysy.database.SuccessPicEnty;
import com.ilab.checkysy.database.SuccessPicEntyDao;
import com.ilab.checkysy.database.UsefulEntyDao;
import com.ilab.checkysy.entity.FileEntity;
import com.ilab.checkysy.helper.DeleteFileThread;
import com.ilab.checkysy.helper.DownloadHelper;
import com.ilab.checkysy.helper.NewUploadHelper;
import com.ilab.checkysy.helper.QueryHelper;
import com.ilab.checkysy.helper.TokenHelper;
import com.ilab.checkysy.util.Util;
import com.videogo.openapi.bean.resp.CloudPartInfoFile;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.OnClick;

import static com.ilab.checkysy.Constants.checkJson;
import static com.ilab.checkysy.util.CrashHandler.restartApp;

public class MainActivity extends BaseActivity {
    @BindView(R.id.btn_quzhou)
    Button btn_quzhou;
    @BindView(R.id.btn_zheda)
    Button btn_zheda;
    @BindView(R.id.et_camera)
    EditText etCamera;
    @BindView(R.id.btn_search)
    Button btn_search;
    @BindView(R.id.tv_screen)
    TextView tv_screen;
    @BindView(R.id.btn_downandup)
    Button btn_downAndUp;
    @BindView(R.id.tv_token)
    TextView tvToken;
    @BindView(R.id.btn_date)
    Button btn_date;
    @BindView(R.id.btn_clear_all)
    Button btn_clear_all;
    private SharedPreferencesUtils sp = App.getInstances().getSp();
    private QueryHelper queryHelper;
    private String searchCamera, currentDateList;
    private SuccessEntyDao greenEntyDao;
    private SuccessPicEntyDao greenPicDao;
    private UsefulEntyDao greenUsefulDao;
    private ErrorFileDao greenErrorDao;
    private SkipEntyDao greenSkipEntyDao;
    private int downSize = 0;//待下载的视频数量
    private Map<String, EZCloudRecordFile> maps = new HashMap<>();//剩余需要下载的视频map
    private boolean loginStatus = false;
    private Handler handler = new Handler(new Handler.Callback() {
        @SuppressLint("SetTextI18n")
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case Constants.TOKEN_RESULT:
                    myLogE("--------登录错误，重启app--------");
                    restartApp(2000);
                    break;
                case Constants.LOGIN_SUCCESS:
                    loginStatus = true;
                    if (msg.arg1 == 0) tvToken.setText("衢州--登陆成功，萤石云初始化成功");
                    else if (msg.arg1 == 1) tvToken.setText("浙大--登陆成功，萤石云初始化成功");
                    break;
                case Constants.QUERY_ING:
                    int size = msg.arg1;
                    tv_screen.setText("云视频列表.size()==" + size + "个");
                    break;
                case Constants.QUERY_END:
                    List<CloudPartInfoFile> cloudPartInfoFiles = queryHelper.getCloudPartInfoFiles();
                    currentDateList = new Gson().toJson(cloudPartInfoFiles);
                    sp.putString("currentDateList", currentDateList);
                    myLogE("size:" + msg.arg1 + "");
                    if (msg.arg1 > 0) {
                        tv_screen.setText("查询完毕，所选日期有视频，size:" + msg.arg1);
                        btn_downAndUp.setEnabled(true);
                    } else tv_screen.setText("查询完毕，所选日期没有视频");
                    btn_search.setEnabled(true);
                    break;
                case Constants.DOWN_SUCCESS:
                case Constants.DOWN_FAILED:
                    tv_screen.setText("视频下载共计" + downSize + " ,success=" + msg.arg1 + " ,fail==" + msg.arg2);
                    break;
                case Constants.SHOW_STRING:
                    tv_screen.setText((String) msg.obj);
                    break;
                case Constants.UPLOADPIC_FINISH:
                    tv_screen.setText("");
                    greenPicDao.deleteAll();
                    downAndUploadVideo();
                    break;
                case Constants.UPLOADVideo_FINISH:
                    tv_screen.setText("上传完毕，准备删除文件");
                    myLogE("上传完毕，准备删除文件");
                    cleanAll();
                    break;
            }
            return false;
        }
    });

    private void over_query(List<CloudPartInfoFile> cloudPartInfoFiles) {
        maps = Util.convert(cloudPartInfoFiles);
        tv_screen.setText("已准备转换后的下载列表");
    }

    private void cleanAll() {
        searchCamera = "";
        currentDateList = "";
        downSize = 0;
        sp.clear();
        if (!maps.isEmpty()) maps.clear();
        clean_greenDao();
        new DeleteFileThread(true, sp).start();
    }

    private void distributeTask() {
        if (SharedPreferencesUtils.init(this).getString("savePic" + searchCamera, "error").equals("ok")) {
            myLogE("图片任务上次已完成，开始视频任务");
            downAndUploadVideo();
        } else {
            myLogE("开始图片任务");
            //获取图片url列表
            List<EZCloudRecordFile> imageList = new ArrayList<>(maps.values());
            myLogE("size:" + imageList.size());
            List<SuccessPicEnty> successPicEnties = greenPicDao.loadAll();
            List<EZCloudRecordFile> dropList = new ArrayList<>();
            for (EZCloudRecordFile ezCloudRecordFile : imageList) {
                for (SuccessPicEnty successPicEnty : successPicEnties) {
                    if (ezCloudRecordFile.getFileId().equals(successPicEnty.getFileId())) {
                        dropList.add(ezCloudRecordFile);
                    }
                }
            }
            imageList.removeAll(dropList);
            /**
             * 下载并上传图片
             * 上下文，handler，类型（1、2），TextView（上传控件）、列表、任务序号
             */
            new NewUploadHelper(this, handler, 2, tv_screen, imageList, searchCamera).execute();
        }
    }

    @Override
    public int getContentViewResId() {
        return R.layout.activity_main;
    }

    @Override
    public void init(Bundle savedInstanceState) {
        myLogE("---------" + sp.getLong("lastMovieTime") + "---------");
        greenEntyDao = App.getInstances().getDaoSession().getSuccessEntyDao();
        greenPicDao = App.getInstances().getDaoSession().getSuccessPicEntyDao();
        greenUsefulDao = App.getInstances().getDaoSession().getUsefulEntyDao();
        greenErrorDao = App.getInstances().getDaoSession().getErrorFileDao();
        greenSkipEntyDao = App.getInstances().getDaoSession().getSkipEntyDao();
        btn_downAndUp.setEnabled(false);
        btn_search.setEnabled(false);
        String camera = sp.getString("currentCamera", "");
        if (!camera.equals("")) {
            searchCamera = camera;
            etCamera.setText(camera);
        }
        String str = etCamera.getText().toString().trim();
        if (checkJson.length() > 0) {
            etCamera.setText("有列表，无需输入");
            etCamera.setEnabled(false);
            btn_search.setEnabled(true);
        } else if (str.length() == 9) btn_search.setEnabled(true);
        else btn_search.setEnabled(false);
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String str = etCamera.getText().toString().trim();
                if (str.length() == 9) btn_search.setEnabled(true);
                else btn_search.setEnabled(false);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        etCamera.addTextChangedListener(watcher);
        if (!sp.getString("customDate").equals(""))
            btn_date.setEnabled(false);
        myLogE("工作状态:" + sp.getBoolean("isworking", false) + "");
        myLogE("项目:" + sp.getInt("projectId", 0) + "");
        myLogE("查询摄像头:" + sp.getString("currentCamera") + "");
        myLogE("查询日期:" + sp.getString("customDate") + "");
        myLogE("日期全部列表" + sp.getString("currentDateList") + "");
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void judgeTask() {
        myLogE("-------judgeTask一次-------");
        int projectId = sp.getInt("projectId", -1);
        if (projectId != -1) {
            if (projectId == 0) {
                btn_quzhou.setTextColor(Color.GREEN);
                btn_quzhou.setEnabled(false);
                btn_zheda.setEnabled(false);
            } else if (projectId == 1) {
                btn_zheda.setTextColor(Color.GREEN);
                btn_quzhou.setEnabled(false);
                btn_zheda.setEnabled(false);
            } else {
                btn_quzhou.setEnabled(true);
                btn_zheda.setEnabled(true);
            }
            //初始化YSY
            new TokenHelper(handler).initToken(projectId);
            currentDateList = sp.getString("currentDateList", "null");
            if (!currentDateList.equals("null")) {
                Gson gson = new Gson();
                Type listType = new TypeToken<List<CloudPartInfoFile>>() {
                }.getType();
                over_query(gson.fromJson(currentDateList, listType));
                tv_screen.setText("目标摄像头列表已查找完成,size:" + maps.size());
            }
            //判断是否可以工作
            boolean canwork = sp.getBoolean("isworking", false);
            if (!canwork) {
                btn_downAndUp.setEnabled(true);
                return;
            }
            searchCamera = sp.getString("currentCamera");
            if (!searchCamera.isEmpty()) {
                btn_search.setEnabled(false);
                btn_downAndUp.setEnabled(false);
                btn_date.setEnabled(false);
                etCamera.setEnabled(false);
                //下载摄像头的视频
                distributeTask();
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @OnClick({R.id.btn_quzhou, R.id.btn_zheda, R.id.btn_search, R.id.btn_downandup, R.id.btn_date, R.id.btn_clear_all})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_quzhou:
                sp.putInt("projectId", 0);
                btn_quzhou.setTextColor(Color.GREEN);
                btn_quzhou.setEnabled(false);
                btn_zheda.setEnabled(false);
                new TokenHelper(handler).initToken(0);
                break;
            case R.id.btn_zheda:
                sp.putInt("projectId", 1);
                btn_zheda.setTextColor(Color.GREEN);
                btn_quzhou.setEnabled(false);
                btn_zheda.setEnabled(false);
                new TokenHelper(handler).initToken(1);
                break;
            case R.id.btn_search:
                if (loginStatus) {
                    if (sp.getInt("projectId", -1) != -1) {
                        if (!sp.getString("customDate", "").equals("")) {
                            String str;
                            if (etCamera.isEnabled()) str = etCamera.getText().toString().trim();
                            else str = checkJson;
                            sp.putString("currentCamera", str);
                            searchCamera = str;
                            tv_screen.setText("正在查询云列表。。。");
                            btn_downAndUp.setEnabled(false);
                            btn_search.setEnabled(false);
                            //今天的未扫描云文件
                            new Timer().schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    queryHelper = new QueryHelper(str, handler);
                                    queryHelper.startQueryCould();
                                }
                            }, 500);
                        } else tv_screen.setText("请先选择查询日期");
                    } else tv_screen.setText("请先选择要查询摄像头的位置");
                } else tv_screen.setText("请等待YSY登录成功");
                break;
            case R.id.btn_downandup:
                sp.putBoolean("isworking", true);
                btn_downAndUp.setEnabled(false);
                btn_date.setEnabled(false);
                btn_search.setEnabled(false);
                etCamera.setEnabled(false);
                tv_screen.setText("开始传输...");
                currentDateList = sp.getString("currentDateList", "null");
                if (!currentDateList.equals("null")) {
                    Gson gson = new Gson();
                    Type listType = new TypeToken<List<CloudPartInfoFile>>() {
                    }.getType();
                    over_query(gson.fromJson(currentDateList, listType));
                    distributeTask();
                }
                break;
            case R.id.btn_date:
                Calendar d = Calendar.getInstance(Locale.CHINA);
                Date myDate = new Date();
                d.setTime(myDate);
                int year = d.get(Calendar.YEAR);
                int month = d.get(Calendar.MONTH);
                int day = d.get(Calendar.DAY_OF_MONTH);
                @SuppressLint("SetTextI18n")
                DatePickerDialog datePickerDialog = new DatePickerDialog(this, R.style.MyDatePickerDialogTheme, (view1, year1, monthOfYear, dayOfMonth) -> {
                    String str_m = (monthOfYear + 1) > 9 ? (monthOfYear + 1) + "" : "0" + (monthOfYear + 1);
                    String str_d = dayOfMonth > 9 ? dayOfMonth + "" : "0" + dayOfMonth;
                    String s = year1 + str_m + str_d;
                    sp.putString("customDate", s);
                    SharedPreferencesUtils.init(this).putString("customDate", s);
                    btn_date.setEnabled(false);
                    tv_screen.setText("选择的日期:" + s);
                }, year, month, day);
                DatePicker dp = datePickerDialog.getDatePicker();
                dp.setMaxDate(new Date().getTime() - 1000 * 60 * 60 * 24);
                datePickerDialog.show();
                break;
            case R.id.btn_clear_all:
                String currentTvScreen = tv_screen.getText().toString().trim();
                tv_screen.setText("---警告---");
                tv_screen.setTextColor(Color.RED);
                AlertDialog alertDialog = new AlertDialog.Builder(this)
                        .setTitle("确定清空SP数据？")
                        .setMessage("此行为将丢失全部数据和正在执行的任务，请谨慎操作")
                        .setPositiveButton("确认", (dialog, which) -> {
                            myToast("清理中，等待重启...");
                            cleanAll();
                        })
                        .setNegativeButton("取消", (dialog, which) -> {
                            tv_screen.setText(currentTvScreen);
                            tv_screen.setTextColor(Color.BLACK);
                        }).create();
                alertDialog.setCanceledOnTouchOutside(false);
                alertDialog.show();
                break;
        }
    }


    //上传下载视频
    private void downAndUploadVideo() {
        myLogE("--------分发一次--------");
        //获取sd卡视频文件
        Map<String, FileEntity> existFiles = Util.scanFiles(1);
        myLogE("sd卡文件数量==" + existFiles.size());
        List<SuccessEnty> success = greenEntyDao.loadAll();
        List<SkipEnty> skipEntyList = greenSkipEntyDao.loadAll();
        myLogE("--------已经上传的共计size:" + success.size());
        int count = maps.size() - skipEntyList.size();
        if (existFiles.size() == count) {
            //已经全部下载完毕，开始上传任务
            if (success.size() > 0) {
                for (SuccessEnty enty : success) {
                    existFiles.remove(enty.getFileId());
                }
            }
            //清理无效视频
            new Thread(() -> {
                Message msg = Message.obtain();
                msg.what = Constants.SHOW_STRING;
                msg.obj = "正在清理无效视频...";
                handler.sendMessage(msg);
                List<FileEntity> uploadVideoList = new ArrayList<>(existFiles.values());
                Message m = Message.obtain();
                m.what = Constants.SHOW_STRING;
                if (Util.scanSD_ValidVideo(true, 0)) {
                    m.obj = "清理成功，准备上传";
                    //待上传的视频数量
                    myLogE("---------待上传共计--------" + uploadVideoList.size());
                    new NewUploadHelper(this, 1, tv_screen, handler, uploadVideoList).execute();
                } else {
                    m.obj = "仍存在无效视频，准备重启并重新下载";
                    restartApp(2000);
                    myLogE("重启中...");
                }
                handler.sendMessage(m);
            }).start();
        } else if (existFiles.size() > count) {
            Message message = Message.obtain();
            message.what = Constants.SHOW_STRING;
            message.obj = "文件数目异常，重拉未下载文件";
            handler.sendMessage(message);
            new Thread(() -> {
                //删除上个任务最后一个文件修改时间以前的视频
                Util.removeFileByTime(sp.getLong("lastMovieTime"), Constants.path);
                greenSkipEntyDao.deleteAll();
                restartApp(2000);
            }).start();
        } else {
            //说明还没有完全下载完，开启下载任务
            myLogE("下载任务，数量==" + maps.size());
            //去除掉已经下载成功的;
            for (String key : existFiles.keySet())
                maps.remove(key);
            myLogE("去除掉已下载成功的，剩余==" + maps.size());
            downSize = maps.size();
            tv_screen.setText("准备下载中...");
            new DownloadHelper(this, handler, maps).execute();
        }
    }

    private void clean_greenDao() {
        greenUsefulDao.deleteAll();
        greenEntyDao.deleteAll();
        greenPicDao.deleteAll();
        greenErrorDao.deleteAll();
        greenSkipEntyDao.deleteAll();
    }
}