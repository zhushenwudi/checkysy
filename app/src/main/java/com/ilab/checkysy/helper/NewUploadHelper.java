package com.ilab.checkysy.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.target.Target;
import com.heima.easysp.SharedPreferencesUtils;
import com.ilab.checkysy.App;
import com.ilab.checkysy.Constants;
import com.ilab.checkysy.cloud.EZCloudRecordFile;
import com.ilab.checkysy.database.SuccessEnty;
import com.ilab.checkysy.database.SuccessEntyDao;
import com.ilab.checkysy.database.SuccessPicEnty;
import com.ilab.checkysy.database.SuccessPicEntyDao;
import com.ilab.checkysy.entity.FileEntity;
import com.ilab.checkysy.util.ThreadPoolUtils;
import com.ilab.checkysy.util.Util;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.ServiceProperties;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.StorageLocation;
import com.microsoft.azure.storage.blob.BlobContainerPermissions;
import com.microsoft.azure.storage.blob.BlobContainerPublicAccessType;
import com.microsoft.azure.storage.blob.BlockEntry;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import com.microsoft.azure.storage.core.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ilab.checkysy.util.Util.restartApp;

@SuppressLint("StaticFieldLeak")
public class NewUploadHelper extends AsyncTask<Void, Void, Void> {
    private static AtomicInteger errorCount = new AtomicInteger(0);  //传输错误个数统计
    private static AtomicInteger successCount = new AtomicInteger(0);  //传输成功数目统计
    private List<FileEntity> videoEntityList;
    private List<EZCloudRecordFile> imageEntityList;
    private int type;  //传输类型(1:视频,2:图片)
    private TextView tv;
    private CloudBlobContainer container;  //声明Azure云Blob容器
    private Context context;
    private Handler mHandler;
    private String searchCamera;
    private SuccessEntyDao greenEntyDao;
    private SuccessPicEntyDao greenPicDao;
    private SharedPreferencesUtils sp = SharedPreferencesUtils.init(context);

    public NewUploadHelper(Context context, int type, TextView tv, Handler mHandler, List<FileEntity> existFiles) {
        this.type = type;
        this.tv = tv;
        this.mHandler = mHandler;
        this.videoEntityList = existFiles;
        this.context = context;
    }

    public NewUploadHelper(Context context, Handler mHandler, int type, TextView tv, List<EZCloudRecordFile> imageEntityList, String searchCamera) {
        this.type = type;
        this.tv = tv;
        this.imageEntityList = imageEntityList;
        this.context = context;
        this.mHandler = mHandler;
        this.searchCamera = searchCamera;
    }


    @SuppressLint("SetTextI18n,SimpleDateFormat")
    private boolean initAzure(int type) {
        String date = SharedPreferencesUtils.init(context).getString("customDate", Util.getYesterday());
        sendMsg("Azure云初始化中...");
        Log.e("aaa", "Azure云初始化中...上传日期:" + date);
        StringBuilder sb = new StringBuilder("初始化错误  ");
        try {
            //绑定Azure云账户
            CloudStorageAccount account = CloudStorageAccount.parse(Constants.storageConnectionString2);
            //创建Blob本地客户端
            CloudBlobClient blobClient = account.createCloudBlobClient();
            ServiceProperties props = new ServiceProperties();
            props.setDefaultServiceVersion("2009-09-19");
            blobClient.uploadServiceProperties(props);
            if (type == 1) {
                //获取Blob容器
                container = blobClient.getContainerReference("video-" + date);
            } else if (type == 2) {
                //获取Blob容器
                container = blobClient.getContainerReference("picture-" + date);
            }
            //容器不存在时创建
            container.createIfNotExists();
            //赋予RW+权限
            BlobContainerPermissions containerPermissions = new BlobContainerPermissions();
            containerPermissions.setPublicAccess(BlobContainerPublicAccessType.CONTAINER);
            container.uploadPermissions(containerPermissions);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            sb.append("数据未编码，请联系软件工程师");
            sendMsg(sb.toString());
            return false;
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            sb.append("数据加密有误，请联系软件工程师");
            sendMsg(sb.toString());
            return false;
        } catch (StorageException e) {
            e.printStackTrace();
            sb.append("Blob正在更新中...请稍候再试");
            sendMsg(sb.toString());
            return false;
        }
        return true;
    }

    //发送到主线程TextView显示消息
    private void sendMsg(final String msg) {
        new Handler(Looper.getMainLooper()).post(() -> tv.setText(msg));
    }

    @Override
    protected Void doInBackground(Void... voids) {
        //初始化Azure
        if (initAzure(type)) {
            Log.e("aaa", "初始化完成！");
            //初始化Fixed线程池，指定线程数为IO密集型传输最优值
            ThreadPoolUtils myThreadPool = new ThreadPoolUtils(ThreadPoolUtils.FixedThread, Constants.CPU_COUNT * 2 + 1);
            //视频类型
            if (type == 1) {
                successCount.getAndSet(0);
                Log.e("aaa", "准备传输的文件数目:" + videoEntityList.size());
                greenEntyDao = App.getInstances().getDaoSession().getSuccessEntyDao();
                while (videoEntityList.size() > 0) {
                    if (myThreadPool == null)
                        myThreadPool = new ThreadPoolUtils(ThreadPoolUtils.FixedThread, Constants.CPU_COUNT * 2 + 1);
                    errorCount.getAndSet(0);
                    List<FileEntity> newVideoList = new ArrayList<>(videoEntityList);
                    videoEntityList.clear();
                    //遍历扫描到的文件列表
                    for (final FileEntity fileEntity : newVideoList) {
                        //放入线程池中执行上传操作
                        myThreadPool.execute(() -> {
                            uploadVideo(fileEntity);
                            sendMsg("视频上传共计:" + newVideoList.size() + ", success=" + successCount + ",fail=" + errorCount);
                            Log.e("over", "已成功上传视频==" + successCount + "个");
                        });
                    }
                    //放入任务结束后关闭线程池（意思是所有任务结束，线程池会自然关闭，非shutdownNow()强制即刻生效方法）
                    myThreadPool.shutDown();
                    //循环判断线程池中任务是否全部执行完毕
                    while (!myThreadPool.isTerminated()) {
                        //避免高频率轮询，采用睡眠1s方式控制while频率
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    myThreadPool = null;
                }
            }
            //图片类型
            else if (type == 2) {
                successCount.getAndSet(0);
                int uploadCount = imageEntityList.size();
                Log.e("aaa", "准备传输的文件数目:" + imageEntityList.size());
                greenPicDao = App.getInstances().getDaoSession().getSuccessPicEntyDao();
                while (imageEntityList.size() > 0) {
                    if (myThreadPool == null)
                        myThreadPool = new ThreadPoolUtils(ThreadPoolUtils.FixedThread, Constants.CPU_COUNT * 2 + 1);
                    errorCount.getAndSet(0);
                    List<EZCloudRecordFile> newImageList = new ArrayList<>(imageEntityList);
                    imageEntityList.clear();
                    //遍历扫描到的文件列表
                    for (final EZCloudRecordFile fileEntity : newImageList) {
                        //放入线程池中执行上传操作
                        myThreadPool.execute(() -> {
                            String filePath = savePicture(fileEntity);
                            if (!filePath.equals("")) {
                                uploadImage(fileEntity, filePath);
                                sendMsg("图片上传共计:" + uploadCount + ", success=" + successCount + ",fail=" + errorCount);
                                Log.e("aaa", "已成功上传图片==" + successCount + "张");
                                SuccessPicEnty successPicEnty = new SuccessPicEnty();
                                successPicEnty.setFileId(fileEntity.getFileId());
                                greenPicDao.insert(successPicEnty);
                            }
                        });
                    }
                    //放入任务结束后关闭线程池（意思是所有任务结束，线程池会自然关闭，非shutdownNow()强制即刻生效方法）
                    myThreadPool.shutDown();
                    //循环判断线程池中任务是否全部执行完毕
                    while (!myThreadPool.isTerminated()) {
                        //避免高频率轮询，采用睡眠1s方式控制while频率
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    myThreadPool = null;
                }
            }
        } else {
            Log.e("aaa", "初始化失败！");
            restartApp(context, 2000);
        }
        return null;
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onPostExecute(Void aVoid) {
        tv.setText("上传完成，错误数目:" + errorCount + "个");
        if (type == 1) {
            if (errorCount.get() != 0) {
                restartApp(context, 2000);
            }
            Message msg = new Message();
            msg.what = Constants.UPLOADVideo_FINISH;
            mHandler.sendMessage(msg);
        } else if (type == 2) {
            if (errorCount.get() != 0) {
                restartApp(context, 2000);
            }
            sp.putString("savePic" + sp.getString("customDate") + searchCamera, "ok");
            Message msg = new Message();
            msg.what = Constants.UPLOADPIC_FINISH;
            mHandler.sendMessage(msg);
        }
    }

    @SuppressLint("NewApi")
    //上传本机图片
    private void uploadImage(EZCloudRecordFile fileEntity, String filePath) {
        try {
            String fileName = fileEntity.getDeviceSerial() + "/" + fileEntity.getDeviceSerial() + "_" +
                    fileEntity.getStartTime().getTimeInMillis() + "_" + fileEntity.getFileId() + ".PNG";
            CloudBlockBlob blockBlob = container.getBlockBlobReference(fileName);
            //上传文件（同步方法，错误会抛出异常）
            blockBlob.uploadFromFile(filePath);
            //获取Azure上传后的URL
            //Log.e("aaa", blockBlob.getUri().toString());
            successCount.getAndIncrement();
        }
        //抛出异常
        catch (StorageException | IOException | URISyntaxException e) {
            e.printStackTrace();
            int failcount = fileEntity.getRetry();
            if (failcount <= 2) {
                imageEntityList.add(fileEntity);
                fileEntity.setRetry(++failcount);
            } else
                errorCount.getAndIncrement();

        }
    }

    @SuppressLint({"NewApi", "DefaultLocale", "SetTextI18n"})
    //上传本机视频
    private void uploadVideo(FileEntity fileEntity) {
        //清空列表
        ArrayList<BlockEntry> blockList = new ArrayList<>();
        //声明文件输入流
        FileInputStream fileInputStream = null;
        //本地文件的位置
        int blockNum = 0;
        String blockId, blockIdEncoded;
        String filePath = fileEntity.getUri();
        try {
            CloudBlockBlob blockBlob = container.getBlockBlobReference(fileEntity.getSn() + "/" + fileEntity.getSn()
                    + "_" + fileEntity.getTime() + "_" + fileEntity.getLength() + "_" + fileEntity.getFileId() + ".MP4");
            //读取视频流
            fileInputStream = new FileInputStream(filePath);

            //循环读取文件前4M视频流
            while (fileInputStream.available() > (4 * 1024 * 1024)) {
                blockId = String.format("%05d", blockNum);
                blockIdEncoded = Base64.encode(blockId.getBytes());
                blockBlob.uploadBlock(blockIdEncoded, fileInputStream, (4 * 1024 * 1024));
                blockList.add(new BlockEntry(blockIdEncoded));
                blockNum++;
            }
            //读取文件X-4n（不足4M）的视频流
            blockId = String.format("%05d", blockNum);
            blockIdEncoded = Base64.encode(blockId.getBytes());
            blockBlob.uploadBlock(blockIdEncoded, fileInputStream, fileInputStream.available());
            blockList.add(new BlockEntry(blockIdEncoded));
            //上传（同步方法，错误会抛出异常）
            blockBlob.commitBlockList(blockList);
            successCount.getAndIncrement();

            //插入上传成功的数据库中
            SuccessEnty enty = new SuccessEnty();
            enty.setFileId(fileEntity.getFileId());
            greenEntyDao.insert(enty);
        }
        //抛出异常
        catch (StorageException | URISyntaxException | IOException e) {
            e.printStackTrace();
            errorCount.getAndIncrement();
            int count = fileEntity.getRetry();
            if (count < 3) {
                fileEntity.setRetry(++count);
                videoEntityList.add(fileEntity);
            } else {
                SuccessEnty enty = new SuccessEnty();
                enty.setFileId(fileEntity.getFileId());
                greenEntyDao.insert(enty);
            }
        } finally {
            //关闭视频流
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    restartApp(context, 2000);
                }
            }
        }
    }

    //Glide保存图片
    private String savePicture(EZCloudRecordFile fileEntity) {
        String fileUrl = fileEntity.getCoverPic();
        FutureTarget<File> future = Glide.with(context)
                .load(fileUrl)
                .downloadOnly(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
        File cacheFile = null;
        try {
            cacheFile = future.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (cacheFile != null)
            return cacheFile.getAbsolutePath();
        else
            imageEntityList.add(fileEntity);
        return "";
    }

    //下载视频
    private void downloadVideo(CloudBlob cloudBlob, String fileName) throws IOException, StorageException {
        File downloadedFile = new File(Constants.path, fileName);
        cloudBlob.downloadToFile(downloadedFile.getAbsolutePath());
    }

    //获取容器中所有文件URI
    private List<String> blobList(CloudBlobContainer container) {
        List<String> rtnlist = new ArrayList<>();
        for (ListBlobItem item : container.listBlobs()) {
            rtnlist.add(String.valueOf(item.getStorageUri().getUri(StorageLocation.PRIMARY)));
        }
        return rtnlist;
    }

    //获取所有容器
    private List<String> containerList(CloudBlobClient cloudBlobClient) {
        List<String> rtnlist = new ArrayList<>();
        for (CloudBlobContainer item : cloudBlobClient.listContainers()) {
            rtnlist.add(item.getName());
        }
        return rtnlist;
    }

    //删除容器及全部内容
    private boolean deleteContainer(CloudBlobContainer container) throws StorageException {
        if (container != null)
            return container.deleteIfExists();
        else
            return false;
    }
}