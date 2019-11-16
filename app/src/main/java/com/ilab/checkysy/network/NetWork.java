package com.ilab.checkysy.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class NetWork {
    private static final String ILAB = "https://qz.qzwjtest.ilabservice.cloud/";
    private static NetWork mInstance;
    private static volatile ApiService mApiService;

    private NetWork() {
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build();
        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(ILAB)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        mApiService = retrofit.create(ApiService.class);
    }

    public static void init() {
        if (mInstance == null) {
            synchronized (NetWork.class) {
                mInstance = new NetWork();
            }
        }
    }

    public static ApiService getRequest() {
        return mApiService;
    }
}
