package com.ilab.checkysy;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.heima.easysp.SharedPreferencesUtils;
import com.ilab.checkysy.cloud.EZCloudRecordFile;
import com.ilab.checkysy.database.DayKeyToListValue;
import com.ilab.checkysy.database.DayKeyToListValueDao;
import com.ilab.checkysy.database.ErrorFile;
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
import com.ilab.checkysy.util.SFTPUtils;
import com.ilab.checkysy.util.Util;
import com.savvi.rangedatepicker.CalendarPickerView;
import com.videogo.openapi.bean.resp.CloudPartInfoFile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.OnClick;

import static com.ilab.checkysy.Constants.checkJson;
import static com.ilab.checkysy.util.Util.restartApp;
import static com.ilab.checkysy.util.Util.writeToFile;

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
    private String searchCamera;
    private SuccessEntyDao greenEntyDao;
    private SuccessPicEntyDao greenPicDao;
    private UsefulEntyDao greenUsefulDao;
    private ErrorFileDao greenErrorDao;
    private SkipEntyDao greenSkipEntyDao;
    private DayKeyToListValueDao greenDayToListDao;
    private int downSize = 0;//待下载的视频数量
    private Map<String, EZCloudRecordFile> maps = new HashMap<>();//剩余需要下载的视频map
    private boolean loginStatus = false;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    private Handler handler = new Handler(new Handler.Callback() {
        @SuppressLint("SetTextI18n")
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case Constants.TOKEN_RESULT:
                    myLogE("--------登录错误，重启app--------");
                    restartApp(MainActivity.this, 2000);
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
                    //获取查询到的需下载的文件列表
                    List<CloudPartInfoFile> cloudPartInfoFiles = queryHelper.getCloudPartInfoFiles();
                    //当前需下载的列表
                    String currentDateList = new Gson().toJson(cloudPartInfoFiles);

                    //每次查询完一天的当前摄像头的列表就保存到数据库
                    DayKeyToListValue dayKeyToListValue = new DayKeyToListValue();
                    dayKeyToListValue.setDay(sp.getString("customDate"));
                    dayKeyToListValue.setList(currentDateList);
                    greenDayToListDao.insert(dayKeyToListValue);

                    //UI
                    if (msg.arg1 > 0) {
                        tv_screen.setText("查询完毕，所选日期有视频，size:" + msg.arg1);
                    } else tv_screen.setText("查询完毕，所选日期没有视频");


                    //将所需下载的全部日期通过Gson转换为Date列表
                    List<Date> dateList = new Gson().fromJson(sp.getString("totalDate"), new TypeToken<List<Date>>() {
                    }.getType());

                    //如果列表长度为1，证明是最后要查询的日期
                    if (dateList.size() == 1) {

                        //替换为原来完整的日期列表，准备视频上传与下载
                        sp.putString("totalDate", sp.getString("spDate"));
                        sp.remove("spDate");
                        //UI
                        Runnable runnable = () -> tv_screen.setText("查询结束，待机中...");
                        btn_downAndUp.setEnabled(true);
                        btn_search.setEnabled(true);
                        handler.postDelayed(runnable, 5000);
                    }
                    //如果列表长度大于1，证明还需继续查询之后的日期列表
                    else if (dateList.size() > 1) {

                        //删掉当前查找完成的Date对象
                        dateList.remove(0);

                        //将删除后需查询的日期列表转为JSON保存
                        sp.putString("totalDate", new Gson().toJson(dateList));

                        //保存下一次需查询的日期
                        sp.putString("customDate", sdf.format(dateList.get(0)));

                        //准备启动下一次查询逻辑
                        Runnable runnable = () -> {
                            queryHelper = new QueryHelper(sp.getString("currentCamera"), handler, MainActivity.this);
                            queryHelper.startQueryCould();
                        };
                        handler.postDelayed(runnable, 3000);
                    }
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
                case 999:
                    List<Date> dataList = (List<Date>) msg.obj;
                    Bundle bundle = msg.getData();
                    List<DayKeyToListValue> dayKeyToListValues = new Gson().fromJson(bundle.getString("dayKeyToListValues"), new TypeToken<List<DayKeyToListValue>>() {
                    }.getType());
                    //判断是否为手动删除数据库操作
                    if (dayKeyToListValues.size() != 0) {
                        sp.putBoolean("isworking", true);
                        sp.putInt("projectId", msg.arg1);
                        sp.putString("totalDate", new Gson().toJson(dataList));
                        sp.putString("customDate", sdf.format(dataList.get(0)));
                        sp.putString("currentCamera", searchCamera);
                    } else {
                        greenDayToListDao.deleteAll();
                    }

                    clean_greenDao();

                    new DeleteFileThread(MainActivity.this, true, sp).start();
                    break;
            }
            return false;
        }
    });

    //通过一系列比对，获取到当前所需要传输的map对象
    private void over_query(List<CloudPartInfoFile> cloudPartInfoFiles) {
        maps = Util.convert(cloudPartInfoFiles);
    }

    private void cleanAll() {
        List<Date> list = new Gson().fromJson(sp.getString("totalDate"), new TypeToken<List<Date>>() {
        }.getType());
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //删除完成的日期的列表
        List<DayKeyToListValue> dayKeyToListValues = greenDayToListDao.loadAll();
        dayKeyToListValues.removeIf(dayKeyToListValue -> dayKeyToListValue.getDay().equals(sp.getString("customDate")));
        greenDayToListDao.deleteAll();
        for (DayKeyToListValue dayKeyToListValue : dayKeyToListValues)
            greenDayToListDao.insert(dayKeyToListValue);
        int projectId = -1;
        if (dayKeyToListValues.size() != 0) {
            projectId = sp.getInt("projectId");

            Iterator<Date> iterator = list.iterator();
            while (iterator.hasNext()) {
                if (sdf.format(iterator.next()).equals(sp.getString("customDate"))) {
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    iterator.remove();
                }
            }

        }

        downSize = 0;
        sp.clear();
        if (!maps.isEmpty()) maps.clear();

        //TODO:保存数据库全部文件，并上传至服务器

        //totalList:
        //usefulList 为了使程序稳定进行，跳出视频检测有效性进行下一个行为，包含successList + dropList + (errorList.getCount > 3)，三者相加可以 达到 和总下载列表长度相同

        //itemList:
        //successList 为 成功上传 无任何阻碍的
        //dropList 为 摄像头加密、视频时长超过30分钟
        //errorList.getCount >= 3 则为无论如何都不能检测视频有效性通过的

        //所以为了服务器端区分开，想要的数据，需要上传的列表，需要按照itemList分开统计上传。
        List<ErrorFile> errorFileList = greenErrorDao.loadAll();
        List<SkipEnty> skipEntyList = greenSkipEntyDao.loadAll();

        StringBuilder errorStringBuilder = new StringBuilder();
        for (ErrorFile errorFile : errorFileList) {
            if (errorFile.getCount() >= 3) {
                errorStringBuilder.append(errorFile.getFileName()).append("\n");
            }
        }

        StringBuilder skipStringBuilder = new StringBuilder();
        for (SkipEnty skipEnty : skipEntyList) {
            skipStringBuilder.append(skipEnty.getFileName()).append("\n");
        }

        final int id = projectId;

        new Thread(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            String now = sdf.format(new Date());
            SFTPUtils sftp = new SFTPUtils("40.73.40.129", "gr.zhu", "o1uNF7f5m0", 50022);
            if (!"".equals(errorStringBuilder.toString().trim())) {
                myLogE("开始上传...执行三次未成功的视频");
                String errorPath = writeToFile(this, "error", errorStringBuilder.toString());
                String remote_errorPath = "/hub.devops.intelab.cloud/a_zhuguirui/" + now + "/error";
                sftp.connect();
                if (!sftp.isDirExist(remote_errorPath)) {
                    sftp.mkdirs(remote_errorPath);
                }
                sftp.bacthUploadFile(remote_errorPath, errorPath, false);
                sftp.disconnect();
            }
            if (!"".equals(skipStringBuilder.toString().trim())) {
                myLogE("开始上传...摄像头加密或者超过30分钟的视频");
                String skipPath = writeToFile(this, "skip", skipStringBuilder.toString());
                String remote_skipPath = "/hub.devops.intelab.cloud/a_zhuguirui/" + now + "/skip";
                sftp.connect();
                if (!sftp.isDirExist(remote_skipPath)) {
                    sftp.mkdirs(remote_skipPath);
                }
                sftp.bacthUploadFile(remote_skipPath, skipPath, false);
                sftp.disconnect();
            }

            Message message = Message.obtain();
            message.what = 999;
            message.obj = list;
            message.arg1 = id;
            Bundle bundle = new Bundle();
            bundle.putString("dayKeyToListValues", new Gson().toJson(dayKeyToListValues));
            message.setData(bundle);
            handler.sendMessage(message);
        }).start();
    }

    private void distributeTask() {
        List<Date> dateList = new Gson().fromJson(sp.getString("totalDate"), new TypeToken<List<Date>>() {
        }.getType());
        String dateStr = sdf.format(dateList.get(0));
        sp.putString("customDate", dateStr);

        //当可以执行下载或上传任务时，先判断进行的是图片的还是视频的
        if (SharedPreferencesUtils.init(this).getString("savePic" + dateStr + searchCamera, "error").equals("ok")) {
            myLogE("图片任务上次已完成，开始视频任务");
            downAndUploadVideo();
        } else {
            myLogE("开始图片任务");

            //获取图片url列表
            List<EZCloudRecordFile> imageList = new ArrayList<>(maps.values());

            //已成功的列表
            List<SuccessPicEnty> successPicEnties = greenPicDao.loadAll();

            //现有列表除去成功的列表-->未完成的列表
            imageList.removeIf(ezCloudRecordFile -> {
                for (SuccessPicEnty successPicEnty : successPicEnties) {
                    if (ezCloudRecordFile.getFileId().equals(successPicEnty.getFileId())) {
                        return true;
                    }
                }
                return false;
            });

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
        //声明数据库Dao
        greenEntyDao = App.getInstances().getDaoSession().getSuccessEntyDao();
        greenPicDao = App.getInstances().getDaoSession().getSuccessPicEntyDao();
        greenUsefulDao = App.getInstances().getDaoSession().getUsefulEntyDao();
        greenErrorDao = App.getInstances().getDaoSession().getErrorFileDao();
        greenSkipEntyDao = App.getInstances().getDaoSession().getSkipEntyDao();
        greenDayToListDao = App.getInstances().getDaoSession().getDayKeyToListValueDao();


        btn_downAndUp.setEnabled(false);
        btn_search.setEnabled(false);


        if (checkJson.length() > 0) {
            etCamera.setText("有列表，无需输入");
            etCamera.setEnabled(false);
        } else {
            //获取当前需要查询的摄像头
            String camera = sp.getString("currentCamera", "");
            if (!camera.equals("")) {
                searchCamera = camera;
                etCamera.setText(camera);
                etCamera.setEnabled(false);
            } else {
                btn_search.setEnabled(false);
            }
        }
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

            //判断是否可以工作
            boolean canwork = sp.getBoolean("isworking", false);
            if (!canwork) {
                btn_downAndUp.setEnabled(true);
                return;
            }

            //可以工作的话，拿到当前摄像头，开始下载
            searchCamera = sp.getString("currentCamera");

            if (!searchCamera.isEmpty()) {
                btn_search.setEnabled(false);
                btn_downAndUp.setEnabled(false);
                btn_date.setEnabled(false);
                etCamera.setEnabled(false);

                //获取下一个视频的列表
                String currentDateList;
                List<DayKeyToListValue> dayKeyToListValueList = greenDayToListDao.loadAll();
                if (dayKeyToListValueList != null && dayKeyToListValueList.size() != 0) {
                    currentDateList = dayKeyToListValueList.get(0).getList();
                } else {
                    return;
                }

                over_query(new Gson().fromJson(currentDateList, new TypeToken<List<CloudPartInfoFile>>() {
                }.getType()));
                tv_screen.setText("目标摄像头列表已查找完成,size:" + maps.size());

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
                        List<Date> dateList = new Gson().fromJson(sp.getString("totalDate"), new TypeToken<List<Date>>() {
                        }.getType());
                        if (dateList != null && dateList.size() != 0) {
                            //修改当前日期
                            sp.putString("customDate", sdf.format(dateList.get(0)));

                            if (etCamera.isEnabled())
                                searchCamera = etCamera.getText().toString().trim();
                            else searchCamera = checkJson;
                            //存放当前摄像头
                            sp.putString("currentCamera", searchCamera);

                            tv_screen.setText("正在查询云列表。。。");
                            btn_downAndUp.setEnabled(false);
                            btn_search.setEnabled(false);

                            //今天的未扫描云文件
                            new Timer().schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    queryHelper = new QueryHelper(searchCamera, handler, MainActivity.this);
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

                //获取下一个视频的列表
                String currentDateList;
                List<DayKeyToListValue> dayKeyToListValueList = greenDayToListDao.loadAll();
                if (dayKeyToListValueList != null && dayKeyToListValueList.size() != 0) {
                    currentDateList = dayKeyToListValueList.get(0).getList();
                    over_query(new Gson().fromJson(currentDateList, new TypeToken<List<CloudPartInfoFile>>() {
                    }.getType()));
                    distributeTask();
                } else {
                    return;
                }
                break;
            case R.id.btn_date:
                final Calendar nextMonth = Calendar.getInstance();
                nextMonth.add(Calendar.DATE, 0);
                final Calendar lastMonth = Calendar.getInstance();
                lastMonth.add(Calendar.MONTH, -2);

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.dialog);
                AlertDialog mAlertDialog = builder.create();
                View v = LayoutInflater.from(MainActivity.this).inflate(R.layout.date_picker_dialog, null);
                CalendarPickerView calendar = v.findViewById(R.id.calendar_view);
                calendar.init(lastMonth.getTime(), nextMonth.getTime(), new SimpleDateFormat("MMMM, YYYY", Locale.getDefault()))
                        .inMode(CalendarPickerView.SelectionMode.MULTIPLE);
                Button button = v.findViewById(R.id.button);
                button.setOnClickListener(v1 -> {
                    mAlertDialog.dismiss();
                    List<Date> dateList = calendar.getSelectedDates();
                    sp.putString("totalDate", new Gson().toJson(dateList));
                    sp.putString("spDate", new Gson().toJson(dateList));
                    btn_search.setEnabled(true);
                });
                mAlertDialog.setCanceledOnTouchOutside(false);
                mAlertDialog.setView(v);
                mAlertDialog.show();
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
                    restartApp(this, 2000);
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
                restartApp(this, 2000);
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