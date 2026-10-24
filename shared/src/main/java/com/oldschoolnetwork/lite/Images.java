package com.oldschoolnetwork.lite;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.Base64;
import android.widget.ImageView;
import java.util.*;
public final class Images {
 private static final LinkedHashMap<String,Bitmap> cache=new LinkedHashMap<String,Bitmap>(16,0.75f,true);private static int bytes;
 public static void load(final Context c,final ImageView view,final String url,final int size){
  view.setTag(url);view.setImageResource(android.R.drawable.sym_def_app_icon);if(url==null||url.length()==0)return;
  final String key=size+":"+url.hashCode()+":"+url.length();synchronized(cache){Bitmap hit=cache.get(key);if(hit!=null){view.setImageBitmap(hit);return;}}
  final Handler ui=new Handler(Looper.getMainLooper());Api.WORK.execute(new Runnable(){public void run(){try{
   byte[] data=url.startsWith("data:")?Base64.decode(url.substring(url.indexOf(',')+1),Base64.DEFAULT):HttpsTransport.request(c.getApplicationContext(),url,null,2*1024*1024);
   if(data.length>2*1024*1024)return;BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);options.inSampleSize=1;
   while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>size*2)options.inSampleSize*=2;
   options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.RGB_565;
   final Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);if(bitmap==null)return;
   synchronized(cache){cache.put(key,bitmap);bytes+=bitmap.getRowBytes()*bitmap.getHeight();Iterator<Map.Entry<String,Bitmap>> it=cache.entrySet().iterator();while(bytes>1536*1024&&it.hasNext()){Bitmap old=it.next().getValue();bytes-=old.getRowBytes()*old.getHeight();it.remove();}}
   ui.post(new Runnable(){public void run(){if(url.equals(view.getTag()))view.setImageBitmap(bitmap);}});
  }catch(Exception ignored){}catch(OutOfMemoryError ignored){synchronized(cache){cache.clear();bytes=0;}}}});
 }
}
