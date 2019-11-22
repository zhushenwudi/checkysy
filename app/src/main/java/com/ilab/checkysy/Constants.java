package com.ilab.checkysy;

import android.os.Environment;

public class Constants {
    public static final String path = Environment.getExternalStorageDirectory().getPath() + "/aaa/CloudFile/";
    public static final int QUERY_ING = 130;
    public static final int QUERY_END = 131;
    public static final int DOWN_SUCCESS = 132;
    public static final int DOWN_FAILED = 133;
    public static final int TOKEN_RESULT = 136;
    public static final int LOGIN_SUCCESS = 138;
    public static final int PROJECT_QUZHOU = 0;
    public static final int PROJECT_ZHEDA = 1;
    public static final int DELAYTIME = 1;
    public static final int UPLOADPIC_FINISH = 336;
    public static final int UPLOADVideo_FINISH = 337;
    //CPU个数
    public static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    //恢复中
    public static final String storageConnectionString = "DefaultEndpointsProtocol=https;"
            + "AccountName=ilsqzwjvideo;"
            + "AccountKey=wta7rpW6HeTzok1AwhYohbj+Ws6cFN7u102vkTlSjrOGBllUtKMuezwuenipjnaBng4hXHtS53341liEVz1MKA==;EndpointSuffix=core.chinacloudapi.cn";
    //first
    public static final String storageConnectionString2 = "DefaultEndpointsProtocol=https;"
            + "AccountName=ilsdevdiag;"
            + "AccountKey=4lMLX1nZ/NgM++3aK7WNtyNly6F4tQUDcXne42agNvEZEvNVysApVxdAUgOZFKoz+ujkZ5jkCV6FrxZPG7Ma1g==;EndpointSuffix=core.chinacloudapi.cn";
    //backup
    public static final String storageConnectionString3 = "DefaultEndpointsProtocol=https;"
            + "AccountName=ilsqzwjvideo3;"
            + "AccountKey=jtXd+4b9AR51xzqYq8eDpeT4VAMJafXfzBCxhgcLNNC3BjKLonQNB1NrPCb83RO4nZ6L0doMpzC+RxDkLy4SNQ==;EndpointSuffix=core.chinacloudapi.cn";
    static final int STORAGE_PERMISSION = 129;
    //zhushenwudi的constants
    static final int SHOW_STRING = 333;
    //static final String checkJson = "[\"C90843864\",\"D22710496\"]";
    static final String checkJson = "[C90843864]";
    public static String mAppKeyQuzhou = "a287e05ace374c3587e051db8cd4be82";
    public static String mAppSecretQuzhou = "f01b61048a1170c4d158da3752e4378d";
    public static String mAppKeyZheda = "102574957ca44d8493e6c1df6aaa1b14";
    public static String mAppSecretZheda = "2fd3f7e17d5a9590c182e6f2ee64bdfc";
    public static String mOpenApiServer = "https://open.ys7.com";
    public static String mOpenAuthApiServer = "https://openauth.ys7.com";
}
