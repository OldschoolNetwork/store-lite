package com.oldschoolnetwork.lite;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;

public final class Api {
    public static final String ENDPOINT="https://api.oldschoolnetwork.online/v1/on-account/api/ecosystem.php";
    public static final ExecutorService WORK=Executors.newFixedThreadPool(2);
    public interface Callback {void complete(JSONObject result,String error);}
    private Api(){}
    public static JSONObject account(Context context){
        Cursor c=null;try{c=context.getContentResolver().query(Uri.parse("content://com.oldschoolnetwork.core.lite.account/active"),new String[]{"on_id","username","token"},null,null,null);if(c!=null&&c.moveToFirst()){JSONObject result=new JSONObject();for(String key:new String[]{"on_id","username","token"})result.put(key,c.getString(c.getColumnIndexOrThrow(key)));return result;}}catch(Exception ignored){}finally{if(c!=null)c.close();}return null;
    }
    public static void signIn(Context context){try{context.startActivity(new Intent().setClassName("com.oldschoolnetwork.core.lite","com.oldschoolnetwork.core.lite.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(ActivityNotFoundException e){context.startActivity(new Intent(context,OfficialDownloadActivity.class).putExtra("service","core").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}}
    public static JSONObject request(Context c,String action,JSONObject values,boolean authenticated)throws Exception{
        if(!action.matches("[a-z_]+"))throw new IOException("Unknown service action.");
        String accountId="";if(authenticated){JSONObject user=account(c);if(user==null)throw new IOException("Sign in with ON Core Lite to use this feature.");accountId=user.optString("on_id");values.put("on_id",accountId);values.put("token",user.optString("token"));}
        byte[] data=HttpsTransport.request(c,ENDPOINT+"?action="+action,values.toString().getBytes("UTF-8"),4*1024*1024);
        JSONObject result=new JSONObject(new String(data,"UTF-8"));String status=result.optString("status");
        if(!"ok".equals(status)&&!"success".equals(status))throw new IOException(result.optString("message","Request failed. "+status));
        if(authenticated){JSONObject current=account(c);if(current==null||!accountId.equals(current.optString("on_id")))throw new IOException("Your account changed. Try again.");}
        return result;
    }
    public static void call(Context context,final String action,final JSONObject values,final boolean authenticated,final Callback callback){
        final Context app=context.getApplicationContext();final Handler main=new Handler(Looper.getMainLooper());
        WORK.execute(new Runnable(){public void run(){try{final JSONObject result=request(app,action,values,authenticated);main.post(new Runnable(){public void run(){callback.complete(result,null);}});}catch(final Exception error){main.post(new Runnable(){public void run(){callback.complete(null,error.getMessage()==null?"Could not connect. Try again.":error.getMessage());}});}}});
    }
}
