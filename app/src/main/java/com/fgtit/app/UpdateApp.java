package com.fgtit.app;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.fgtit.R;
import com.fgtit.data.GlobalData;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;

public class UpdateApp {
    private static final int DOWN_NOSDCARD = 0;
    private static final int DOWN_UPDATE = 1;
    private static final int DOWN_OVER = 2;
    private static final int DOWN_ERROR=3;
    private static final int DIALOG_TYPE_LATEST = 0;
    private static final int DIALOG_TYPE_FAIL = 1;
    private static final int DIALOG_TYPE_INTERNETERROR = 2;
    private static UpdateApp updateapp;
    private Context mContext;
    private Dialog noticeDialog;
    private Dialog downloadDialog;
    private ProgressBar mProgress;
    private TextView mProgressText;
    private ProgressDialog mProDialog;
    private Dialog latestOrFailDialog;
    private String apkUrl = "";
    private int progress;
    private Thread downLoadThread;
    private boolean interceptFlag;
    private String updateMsg = "";
    private String savePath = "";
    private String apkFilePath = "";
    private String tmpFilePath = "";
    private String apkFileSize;
    private String tmpFileSize;
    private String curVersionName = "";
    private int curVersionCode;
    private AppDetail mDownload;
    //private String checkUrl="http://192.168.1.124/apk/update.xml";
    
    private boolean ischeck=false;
    
    private Handler mHandler = new Handler() {
    	public void handleMessage(Message msg) {
    		ischeck=false;
    	    switch (msg.what) {
    	    case DOWN_UPDATE:
        		mProgress.setProgress(progress);
        		mProgressText.setText(tmpFileSize + "/" + apkFileSize);
        		break;
    	    case DOWN_OVER:
    	    	downloadDialog.dismiss();
        		installApk();
        		break;
    	    case DOWN_NOSDCARD:
        		downloadDialog.dismiss();
        		Toast.makeText(mContext, mContext.getString(R.string.txt_upd06),Toast.LENGTH_SHORT).show();
        		break;
    	    case DOWN_ERROR:
        		downloadDialog.dismiss();
        		if(msg.arg1==0){
        			Toast.makeText(mContext, mContext.getString(R.string.txt_upd07), Toast.LENGTH_SHORT).show();
        		}else if(msg.arg1==1||msg.arg1==2){
        			Toast.makeText(mContext, mContext.getString(R.string.txt_upd08),Toast.LENGTH_SHORT).show();
        		}
        		break;
    	    }
    	};
    };
    
    public static UpdateApp getInstance() {
    	if (updateapp == null) {
    	    updateapp = new UpdateApp();
    	}
    	updateapp.interceptFlag = false;
    	return updateapp;
    }
    
    public void DownLoader(Context context, AppDetail download) {
    	this.mContext = context;
    	this.mDownload = download;
    	showDownloadDialog();
    }
    
    public void setAppContext(Context context){
    	this.mContext = context;
    }
    
    public void checkAppUpdate(/*Context context, */final boolean isShowMsg,final boolean notmain) {
    	//this.mContext = context;
    	if(ischeck)
    		return;
    	ischeck=true;
    	
    	getCurrentVersion(mContext);    	
    	if (isShowMsg) {
    	    if (mProDialog == null)
    	    	mProDialog = ProgressDialog.show(mContext, null, mContext.getString(R.string.txt_upd09),true, true);
    	    else if (mProDialog.isShowing()|| (latestOrFailDialog != null && latestOrFailDialog.isShowing()))
    	    	return;
    	}
    	
    	final Handler handler = new Handler() {
    	    public void handleMessage(Message msg) {
    	    	
    	    	ischeck=false;
    	    	
        		if (mProDialog != null && !mProDialog.isShowing()) {
        		    return;
        		}

        		if (isShowMsg && mProDialog != null) {
        		    mProDialog.dismiss();
        		    mProDialog = null;
        		}

        		if (msg.what == 1) {
        		    mDownload = (AppDetail) msg.obj;
        		    if (mDownload != null) {
        			if (curVersionCode < mDownload.getVersionCode()) {
        			    apkUrl = mDownload.getUri()+mDownload.getFileName();
        			    updateMsg = mDownload.getAppHistory();
        			    showNoticeDialog();
        			} else if (isShowMsg) {
        			    if (notmain) {
        			    	showLatestOrFailDialog(DIALOG_TYPE_LATEST);
        			    }
        			}
        		    }
        		}else if(msg.what==-1&&isShowMsg){
        			 showLatestOrFailDialog(DIALOG_TYPE_INTERNETERROR);
        		}else if (isShowMsg) {
        		    showLatestOrFailDialog(DIALOG_TYPE_FAIL);
        		}
    	    }
    	};
	
    	new Thread() {
    		public void run() {
    			Message msg = new Message();
    			try {
    				DefaultHttpClient client = new DefaultHttpClient();
    				client.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 3000);
    				HttpGet get = new HttpGet(GlobalData.getInstance().UpdateUrl);
    				HttpResponse response = client.execute(get);
    				if (response.getStatusLine().getStatusCode() == 200) {
    					HttpEntity entity = response.getEntity();
    					//InputStream stream = new ByteArrayInputStream( EntityUtils.toString(entity, "gb2312").getBytes());
    					InputStream stream = new ByteArrayInputStream( EntityUtils.toString(entity, "UTF-8").getBytes());
    					AppDetail update = AppDetail.parseXML(stream);
    					msg.what = 1;
    					msg.obj = update;
    				}else{
    					msg.what = -1;
    				}
    			} catch (Exception e) {
    			    e.printStackTrace();
    			    msg.what = -1;
    			}
    			handler.sendMessage(msg);
    		}
    	}.start();
    	
    }
    
    private void showLatestOrFailDialog(int dialogType) {
    	String ToastMsg="";
    	if (latestOrFailDialog != null) {
    	    latestOrFailDialog.dismiss();
    	    latestOrFailDialog = null;
    	}
    	if (dialogType == DIALOG_TYPE_LATEST) {
    	    ToastMsg=mContext.getString(R.string.txt_upd10);
    	} else if (dialogType == DIALOG_TYPE_FAIL) {
    		ToastMsg=mContext.getString(R.string.txt_upd11);
    	}else if(dialogType==DIALOG_TYPE_INTERNETERROR){
    		ToastMsg=mContext.getString(R.string.txt_upd12);
    	}
    	Toast.makeText(mContext, ToastMsg, Toast.LENGTH_SHORT).show();
        }
        public String getCurrentVersion(Context context) {
    	try {
    	    PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    	    curVersionName = info.versionName;
    	    curVersionCode = info.versionCode;
    	} catch (NameNotFoundException e) {
    	    e.printStackTrace(System.err);
    	}
    	return curVersionName;
    }
    
    private void showNoticeDialog() {
    	AlertDialog.Builder builder = new Builder(mContext);
    	builder.setTitle(mContext.getString(R.string.txt_upd01));
    	builder.setMessage(updateMsg);
    	builder.setCancelable(false);
    	builder.setPositiveButton(mContext.getString(R.string.txt_upd02), new OnClickListener() {
    	    @Override
    	    public void onClick(DialogInterface dialog, int which) {
    		dialog.dismiss();
    		showDownloadDialog();
    	    }
    	});
    	builder.setNegativeButton(mContext.getString(R.string.txt_upd03), new OnClickListener() {
    	    @Override
    	    public void onClick(DialogInterface dialog, int which) {
    		dialog.dismiss();
    	    }
    	});
    	noticeDialog = builder.create();
    	noticeDialog.show();
    }
    
    private void showDownloadDialog() {
    	AlertDialog.Builder builder = new Builder(mContext);
    	builder.setTitle(mContext.getString(R.string.txt_upd04));
    	final LayoutInflater inflater = LayoutInflater.from(mContext);
    	View v = inflater.inflate(R.layout.dialog_updateapp, null);
    	mProgress = (ProgressBar) v.findViewById(R.id.progress);
    	mProgressText = (TextView) v.findViewById(R.id.title);
    	builder.setView(v);
    	builder.setCancelable(false);
    	builder.setNegativeButton(mContext.getString(R.string.txt_upd05), new OnClickListener() {
    	    @Override
    	    public void onClick(DialogInterface dialog, int which) {
    		dialog.dismiss();
    		interceptFlag = true;
    	    }
    	});
    	builder.setOnCancelListener(new OnCancelListener() {
    	    @Override
    	    public void onCancel(DialogInterface dialog) {
    		dialog.dismiss();
    		interceptFlag = true;
    	    }
    	});
    	downloadDialog = builder.create();
    	downloadDialog.setCanceledOnTouchOutside(false);
    	downloadDialog.show();
    	downloadApk();
    }
    
    private Runnable mdownApkRunnable = new Runnable() {
    	Message error_msg=new Message();
    	@Override
    	public void run(){
    	    try {
    	    	String apkName = mDownload.getFileName().replace(".apk","")+".apk";
        		String tmpApk =  mDownload.getFileName().replace(".apk","")+".tmp";
        		String storageState = Environment.getExternalStorageState();
        		if (storageState.equals(Environment.MEDIA_MOUNTED)) {
        		    savePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/FGTIT/";
        		    File file = new File(savePath);
        		    if (!file.exists()) {
        			file.mkdirs();
        		    }
        		    apkFilePath = savePath + apkName;
        		    tmpFilePath = savePath + tmpApk;
        		}
        		if (apkFilePath == null || apkFilePath == "") {
        		    mHandler.sendEmptyMessage(DOWN_NOSDCARD);
        		    return;
        		}
        		File ApkFile = new File(apkFilePath);
        		File tmpFile = new File(tmpFilePath);
        		FileOutputStream fos = new FileOutputStream(tmpFile);
        		URL url = new URL(mDownload.getUri()+mDownload.getFileName());
        		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        		try {
        		    conn.connect();
        		} catch (ConnectTimeoutException e) {
        		    error_msg.what=DOWN_ERROR;
        		    error_msg.arg1=0;
        		    mHandler.sendMessage(error_msg);
        		}
        		int length = conn.getContentLength();
        		InputStream is = conn.getInputStream();
        		DecimalFormat df = new DecimalFormat("0.00");
        		apkFileSize = df.format((float) length / 1024 / 1024) + "MB";
        		int count = 0;
        		byte buf[] = new byte[1024];
    			do {
    		    	int numread = is.read(buf);
    		    	count += numread;
    		    	tmpFileSize = df.format((float) count / 1024 / 1024) + "MB";
    		    	progress = (int) (((float) count / length) * 100);
    		    	mHandler.sendEmptyMessage(DOWN_UPDATE);
    		    	if (numread <= 0) {
    		    		if (tmpFile.renameTo(ApkFile)) {
    		    			mHandler.sendEmptyMessage(DOWN_OVER);
    		    		}
    		    		break;
    		    	}
    		    	fos.write(buf, 0, numread);
    			} while (!interceptFlag);
    			fos.close();
    			is.close();
    	    } catch (MalformedURLException e) {
    	    	error_msg.what=DOWN_ERROR;
    	    	error_msg.arg1=1;
    	    	mHandler.sendMessage(error_msg);
    	    	e.printStackTrace();
    	    } catch (IOException e) {
    	    	error_msg.what=DOWN_ERROR;
    	    	error_msg.arg1=2;
    	    	mHandler.sendMessage(error_msg);
    	    	e.printStackTrace();
    	    }
    	}
    };
    

    private void downloadApk() {
    	downLoadThread = new Thread(mdownApkRunnable);
    	downLoadThread.start();
    }
    
    private void installApk() {
    	File apkfile = new File(apkFilePath);
    	if (!apkfile.exists()) {
    	    return;
    	}
    	Intent i = new Intent(Intent.ACTION_VIEW);
    	i.setDataAndType(Uri.parse("file://" + apkfile.toString()),"application/vnd.android.package-archive");
    	mContext.startActivity(i);
    }
}