package com.hyq.hm.testsmb;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jcifs.UniAddress;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbSession;

public class MainActivity extends AppCompatActivity {
    private String username = "admin";
    private String password = "xxxxxx";

    private List<SmbFile> mainList = new ArrayList<>();
    private ListAdapter listAdapter;
    private ListView listView;
    private RecyclerView titleView;

    private final ExecutorService fixedThreadPool = Executors.newFixedThreadPool(3);
    private Map<String,SmbFile[]> filesArray = new HashMap<>();
    private List<String> names = new ArrayList<>();
    public boolean isClick = false;

    public FileServer fileServer = new FileServer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        System.setProperty("jcifs.smb.client.dfs.disabled", "true");
        System.setProperty("jcifs.smb.client.soTimeout", "1000000");
        System.setProperty("jcifs.smb.client.responseTimeout", "30000");
        titleView = findViewById(R.id.title_view);
        titleAdapter = new TitleAdapter(this);
        titleView.setAdapter(titleAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        titleView.setLayoutManager(layoutManager);
        listView = findViewById(R.id.list_view);
        listAdapter = new ListAdapter(this);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(isClick){
                    return;
                }
                isClick = true;
                SmbFile sf = null;
                if(names.size()!= 0){
                    String name = names.get(names.size()-1);
                    SmbFile[] smbFiles = filesArray.get(name);
                    if(smbFiles != null){
                        sf = smbFiles[position];
                    }else{
                        names.add(name);
                        listAdapter.notifyDataSetChanged();
                        titleAdapter.notifyDataSetChanged();
                        isClick = false;
                        return;
                    }
                }else{
                    sf = mainList.get(position);
                }
                if(sf != null){
                    final String name = sf.getName();
                    final SmbFile smbFile = sf;
                    if(name.contains("/")&&name.lastIndexOf("/") == name.length()-1){
                        fixedThreadPool.submit(new Runnable() {
                            @Override
                            public void run() {
                                SmbFile[] smbFiles = null;
                                try {
                                    smbFiles = smbFile.listFiles();
                                } catch (SmbException e) {
                                    e.printStackTrace();
                                }
                                final SmbFile[] sfs = smbFiles;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        filesArray.put(name,sfs);
                                        names.add(name);
                                        listAdapter.notifyDataSetChanged();
                                        titleAdapter.notifyDataSetChanged();
                                        isClick = false;
                                    }
                                });
                            }
                        });
                    }else{
                        if(name.contains(".jpg")){
                            int jpg = name.lastIndexOf(".jpg");
                            if(name.length() - 4 == jpg){
                                fixedThreadPool.submit(new Runnable() {
                                    @Override
                                    public void run() {
                                        Bitmap bitmap = null;
                                        try {
                                            bitmap = BitmapFactory.decodeStream(smbFile.getInputStream());
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        if(bitmap != null){
                                            Log.d("===============",bitmap.getWidth()+"x"+bitmap.getHeight());
                                        }else{
                                            Log.d("===============","读取失败");
                                        }
                                        isClick = false;
                                    }
                                });
                            }else{
                                isClick = false;
                            }
                        }else if(name.contains(".mp4")){
                            int mp4 = name.lastIndexOf(".mp4");
                            if(name.length() - 4 == mp4){
                                String http = fileServer.getURL(smbFile);
                                Intent intent = new Intent();
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.setAction(android.content.Intent.ACTION_VIEW);
                                intent.setDataAndType(Uri.parse(http), "video/*");
                                startActivity(intent);
//                                Intent intent = new Intent(MainActivity.this,ExoActivity.class);
//                                intent.putExtra("http",http);
//                                startActivity(intent);
                            }
                            isClick = false;
                        }else {
                            isClick = false;
                        }
                    }
                }else{
                    isClick = false;
                }

            }
        });

        new Thread(){
            @Override
            public void run() {
                super.run();

                SmbFile  mRootFolder = smbLogin();
                SmbFile[] files = null;
                try {
                    files = mRootFolder.listFiles();
                } catch (SmbException e) {
                    e.printStackTrace();
                }
                if(files != null){
                    for (SmbFile smbfile : files) {
                        boolean isDirectory = false;
                        try {
                            isDirectory = smbfile.isDirectory();
                        } catch (SmbException e) {
                            e.printStackTrace();
                        }
                        if(isDirectory){
                            SmbFile[] fs = null;
                            try {
                                fs = smbfile.listFiles();
                            } catch (SmbException e) {
                                e.printStackTrace();
                            }
                            if(fs != null && fs.length != 0){
                                mainList.add(smbfile);
                            }
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        }.start();
        fileServer.start();


    }
    private SmbFile smbNotLogin(){
        String rootPath = "smb://192.168.1.211/";
        SmbFile mRootFolder = null;
        try {
            mRootFolder = new SmbFile(rootPath);
        } catch (MalformedURLException e) {
            Log.d("=============","异常");
            e.printStackTrace();
        }
        return mRootFolder;
    }
    private SmbFile smbLogin(){
        String ip = "192.168.1.2";
        String rootPath = "smb://" + ip + "/";
        SmbFile mRootFolder = null;
        try {
            UniAddress mDomain = UniAddress.getByName(ip);
            NtlmPasswordAuthentication mAuthentication = new NtlmPasswordAuthentication(ip, username, password);
            SmbSession.logon(mDomain, mAuthentication);
            mRootFolder = new SmbFile(rootPath, mAuthentication);
        } catch (UnknownHostException e) {
            Log.d("=============","异常");
            e.printStackTrace();
        } catch (SmbException e) {
            Log.d("=============","异常");
            e.printStackTrace();
        } catch (MalformedURLException e) {
            Log.d("=============","异常");
            e.printStackTrace();
        }
        return mRootFolder;
    }
    private TitleAdapter titleAdapter;
    private class TitleAdapter extends RecyclerView.Adapter<TitleAdapter.TitleHolder> {
        private LayoutInflater inflater = null;

        public TitleAdapter(Context context) {
            inflater = LayoutInflater.from(context);
        }


        @NonNull
        @Override
        public TitleHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            return new TitleAdapter.TitleHolder(inflater.inflate(R.layout.item_title, viewGroup,
                    false));
        }

        @Override
        public void onBindViewHolder(@NonNull TitleHolder titleHolder, int i) {
            if(i == 0){
                titleHolder.nameView.setText(username+"/");
            }else{
                titleHolder.nameView.setText(names.get(i-1));
            }
            titleHolder.position = i;
        }

        @Override
        public int getItemCount() {
            return names.size()+1;
        }
        class TitleHolder extends RecyclerView.ViewHolder {
            TextView nameView;
            int position;
            public TitleHolder(View view) {
                super(view);
                nameView = view.findViewById(R.id.title_view);
                nameView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(isClick){
                            return;
                        }
                        if(position == 0){
                            names.clear();
                        }else{
                            List<String> list = new ArrayList<>();
                            for (int i = 0; i < names.size();i++){
                                if(i < position){
                                    list.add(names.get(i));
                                }else{
                                    filesArray.remove(names.get(i));
                                }
                            }
                            names = list;
                        }
                        listAdapter.notifyDataSetChanged();
                        titleAdapter.notifyDataSetChanged();
                    }
                });
            }
        }
    }


    private class ListAdapter extends BaseAdapter{
        private LayoutInflater inflater = null;
        public ListAdapter(Context context) {
            inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            if(names.size()!= 0){
                String name = names.get(names.size()-1);
                SmbFile[] smbFiles = filesArray.get(name);
                if(smbFiles != null){
                    return smbFiles.length;
                }
                return 0;
            }
            return mainList.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if(convertView == null){
                convertView = inflater.inflate(R.layout.item_list, parent,
                        false);
                viewHolder = new ViewHolder();
                convertView.setTag(viewHolder);
                viewHolder.textView = convertView.findViewById(R.id.text_view);
                viewHolder.nameView = convertView.findViewById(R.id.name_view);
            }else{
                viewHolder = (ViewHolder) convertView.getTag();
            }
            SmbFile sf = null;
            if(names.size()!= 0){
                String name = names.get(names.size()-1);
                SmbFile[] smbFiles = filesArray.get(name);
                if(smbFiles != null){
                    sf = smbFiles[position];
                }
            }else{
                sf = mainList.get(position);
            }
            if(sf != null){
                String name = sf.getName();
                if(name.contains("/")&&name.lastIndexOf("/") == name.length()-1){
                    viewHolder.textView.setText("文件夹");
                    viewHolder.nameView.setText(name.substring(0,name.length()-1));
                }else{
                    viewHolder.textView.setText("文件");
                    viewHolder.nameView.setText(name);
                }
            }else{
                viewHolder.textView.setText("");
                viewHolder.nameView.setText("");
            }
            return convertView;
        }
    }

    private class ViewHolder{
        TextView textView;
        TextView nameView;
    }

    @Override
    protected void onDestroy() {
        fileServer.release();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if(isClick){
            return;
        }
        if(names.size() == 0){
            super.onBackPressed();
        }else{
            List<String> list = new ArrayList<>();
            int position = names.size() - 1;
            for (int i = 0; i < names.size();i++){
                if(i < position){
                    list.add(names.get(i));
                }else{
                    filesArray.remove(names.get(i));
                }
            }
            names = list;
            listAdapter.notifyDataSetChanged();
            titleAdapter.notifyDataSetChanged();
        }
    }
}
