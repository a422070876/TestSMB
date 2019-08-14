package com.hyq.hm.testsmb;

import android.util.Log;

import org.cybergarage.http.HTTPRequest;
import org.cybergarage.http.HTTPRequestListener;
import org.cybergarage.http.HTTPResponse;
import org.cybergarage.http.HTTPServerList;
import org.cybergarage.http.HTTPStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

public class FileServer extends Thread implements HTTPRequestListener {
    private String ip;
    private int port;
    private int HTTPPort = 2222;
    private HTTPServerList httpServerList = new HTTPServerList();
    @Override
    public void run() {
        super.run();

        int retryCnt = 0;
        int bindPort = HTTPPort;
        HTTPServerList hsl = httpServerList;
        while (!hsl.open(bindPort)) {
            retryCnt++;
            // 重试次数大于服务器重试次数时返回
            if (100 < retryCnt) {
                return;
            }
            HTTPPort = bindPort + 1;
            bindPort = HTTPPort;
        }
        hsl.addRequestListener(this);
        hsl.start();
        for (int i = 0; i < hsl.size();i++){
            ip = hsl.getHTTPServer(i).getBindAddress();
            port = hsl.getHTTPServer(i).getBindPort();
            if(ip.contains("/192.168."))break;
        }
        Log.d("=================","ip = "+ip);
        Log.d("=================","port = "+port);
    }
    private Map<String,SmbFile> fileMap = new HashMap<>();
    public String getURL(SmbFile file){
        String path = file.getCanonicalPath();
        String url = path.replace("smb://","http:/"+ip+":"+port+"/smb/");
        fileMap.put(path.replace("smb://","/smb/"),file);
        return url;
    }
    @Override
    public void httpRequestRecieved(HTTPRequest httpReq) {
        String uri = httpReq.getURI();
        Log.d("==============","uri = "+uri);
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
            httpReq.returnBadRequest();
            return;
        }
        if (uri.length() < 6) {
            httpReq.returnBadRequest();
            return;
        }
        SmbFile file = fileMap.get(uri);
        if(file != null){
            try {
                long contentLen = file.length();
                Log.d("============","contentLen = "+contentLen);
                InputStream contentIn = file.getInputStream();
                if (contentLen <= 0
                        || contentIn == null) {
                    httpReq.returnBadRequest();
                    return;
                }
                HTTPResponse httpRes = new HTTPResponse();
                httpRes.setContentType("video/mpeg4");
                httpRes.setStatusCode(HTTPStatus.OK);
                httpRes.setContentLength(contentLen);
                httpRes.setContentInputStream(contentIn);
                httpReq.post(httpRes);
                contentIn.close();
                Log.d("============","close");
            } catch (SmbException e) {
                e.printStackTrace();
                httpReq.returnBadRequest();
            } catch (IOException e) {
                e.printStackTrace();
                httpReq.returnBadRequest();
            }
        }else{
            httpReq.returnBadRequest();
        }
    }
    public void release(){
        httpServerList.stop();
        httpServerList.close();
        httpServerList.clear();
        interrupt();
    }
}
