package com.hyq.hm.testsmb;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by 海米 on 2018/9/13.
 */

public class HttpUtils {

    public static void post(final String url, Map<String,String> post){
        FormBody.Builder builder = new FormBody.Builder();
        for (String key : post.keySet()) {
            builder.add(key,post.get(key));
        }
        RequestBody requestBody = builder.build();
        conn(url,requestBody);
    }
    public static void get(final String url){
        conn(url,null);
    }
    private static void conn(final String url, RequestBody requestBody){
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)//设置连接超时时间
                .readTimeout(10, TimeUnit.SECONDS)//设置读取超时时间
                .build();
        Request.Builder builder = new Request.Builder().url(url);
        if(requestBody != null){
            builder.post(requestBody);
        }
        Call call = client.newCall(builder.build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if(e instanceof SocketTimeoutException){//判断超时异常

                }else if(e instanceof ConnectException){//判断连接异常，我这里是报Failed to connect to 10.7.5.144

                }else {

                }
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String data = null;
                    try {
                        data = response.body().string();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if(!TextUtils.isEmpty(data)){

                    }else{

                    }
                } else {

                }
            }
        });
    }

}
