package com.ilab.checkysy.network;

import com.ilab.checkysy.entity.AccessToken;
import com.ilab.checkysy.entity.AccountList;
import com.ilab.checkysy.entity.Login;
import com.ilab.checkysy.entity.SubAccessToken;

import io.reactivex.Observable;
import okhttp3.RequestBody;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {
    String zheda = "https://zju.staging.ilabservice.cloud";
    String quzhou = "https://qz.qzwj.ilabservice.cloud";

    @Headers({"Content-Type:application/x-www-form-urlencoded; charset=utf-8"})
    @POST("https://open.ys7.com/api/lapp/token/get")
    Observable<AccessToken> getAccessToken(@Query("appKey") String appKey, @Query("appSecret") String appSecret);


    @Headers({"Content-Type:application/json; charset=utf-8"})
    @POST(zheda + "/api/v2/unsecure/login")
    Observable<Login> loginZheda(@Body RequestBody body);

    @Headers({"Content-Type:application/json; charset=utf-8"})
    @POST(quzhou + "/api/v2/unsecure/login")
    Observable<Login> loginZheQuzhou(@Body RequestBody body);


    //zheda
    @Headers({"Content-Type:application/x-www-form-urlencoded; charset=utf-8"})
    @POST("https://open.ys7.com/api/lapp/ram/token/get")
    Observable<SubAccessToken> getSubAccessToken(@Query("accessToken") String accessToken, @Query("accountId") String accountId);


    @Headers({"Content-Type:application/x-www-form-urlencoded; charset=utf-8"})
    @POST("https://open.ys7.com/api/lapp/ram/account/list")
    Observable<AccountList> getAccountList(@Query("accessToken") String accessToken, @Query("pageStart") int pageStart, @Query("pageSize") int pageSize);

}
