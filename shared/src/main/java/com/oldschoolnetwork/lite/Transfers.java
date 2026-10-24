package com.oldschoolnetwork.lite;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.widget.RemoteViews;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.lang.reflect.*;
import org.json.*;

public final class Transfers extends Service {
 private boolean busy;private int appId;private String current;private android.content.SharedPreferences state;
 public IBinder onBind(Intent i){return null;}
 public static boolean compatible(JSONObject p){if(p==null)return false;
  String min=p.optString("min_android","");java.util.regex.Matcher m=java.util.regex.Pattern.compile("SDK\\s*(\\d+)").matcher(min);if(!m.find()||Integer.parseInt(m.group(1))>Build.VERSION.SDK_INT)return false;
  JSONArray abis=p.optJSONArray("supported_abis");if(abis==null||abis.length()==0)return true;for(int i=0;i<abis.length();i++)if(abis.optString(i).equals(Build.CPU_ABI)||abis.optString(i).equals(Build.CPU_ABI2))return true;return false;
 }
 public static JSONObject newest(JSONObject app){JSONArray all=app.optJSONArray("packages");JSONObject found=null;if(all!=null)for(int i=0;i<all.length();i++){JSONObject p=all.optJSONObject(i);if(p!=null&&compatible(p)&&(found==null||p.optInt("version_code")>found.optInt("version_code")))found=p;}return found;}
 public static int installed(Context c,String name){try{return c.getPackageManager().getPackageInfo(name,0).versionCode;}catch(Exception e){return -1;}}
 public static String phase(Context c,String name){return c.getSharedPreferences("transfers",0).getString(name+".phase","");}
 private void phase(String p,String message){state.edit().putString(current+".phase",p).putString(current+".message",message).commit();sendBroadcast(new Intent(getPackageName()+".TRANSFER").setPackage(getPackageName()));}
 public int onStartCommand(final Intent intent,int flags,int id){
  if(intent==null){stopSelf();return START_NOT_STICKY;}if(busy){android.widget.Toast.makeText(this,"Another download or installation is running.",android.widget.Toast.LENGTH_SHORT).show();return START_NOT_STICKY;}busy=true;state=getSharedPreferences("transfers",0);current=intent.getStringExtra("package");appId=intent.getIntExtra("app_id",0);
  if(current==null||!current.matches("[A-Za-z0-9_.]+")){stopSelf();return START_NOT_STICKY;}
  state.edit().putBoolean(current+".cancel",false).remove(current+".file").commit();startForeground(810,notification("Preparing…",0,0));
  new Thread(new Runnable(){public void run(){try{
   JSONObject release=new JSONObject(intent.getStringExtra("release"));String hash=release.optString("sha256").toLowerCase(Locale.US);if(!hash.matches("[0-9a-f]{64}"))throw new IOException("A verified SHA-256 checksum is required for this APK.");
   File directory=new File(getFilesDir(),"packages");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create download directory.");final File file=new File(directory,hash+".apk");
   String url=release.optString("download_url");if(url.length()==0)url=Api.ENDPOINT+"?action=store_download_apk&package_id="+release.optInt("id");
   phase("downloading","Downloading…");HttpsTransport.download(Transfers.this,url,file,256L*1024*1024,new HttpsTransport.Progress(){public void update(long bytes,long total)throws IOException{
    if(state.getBoolean(current+".cancel",false))throw new IOException("Download cancelled.");int percent=total>0?(int)(100*bytes/total):0;state.edit().putInt(current+".progress",percent).commit();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(810,notification("Downloading…",(int)Math.min(Integer.MAX_VALUE,total),(int)Math.min(Integer.MAX_VALUE,bytes)));sendBroadcast(new Intent(getPackageName()+".TRANSFER").setPackage(getPackageName()));
   }});
   verify(Transfers.this,file,current,release.optInt("version_code"),hash);
   if(release.has("certificate_sha256")){PackageInfo signed=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNATURES);StringBuilder cert=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(signed.signatures[0].toByteArray()))cert.append(String.format(Locale.US,"%02x",b&255));if(!cert.toString().equals(release.optString("certificate_sha256")))throw new IOException("The APK is not signed by Oldschool Network.");}
   state.edit().putString(current+".file",file.getName()).commit();phase("ready","Ready to install");
   if(checkCallingOrSelfPermission("android.permission.INSTALL_PACKAGES")==PackageManager.PERMISSION_GRANTED){phase("installing","Installing…");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(810,notification("Installing…",0,0));silent(file);return;}
   stopForeground(true);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(811,completed());
  }catch(Exception error){phase(state.contains(current+".file")?"ready":"error",error.getMessage()==null?"Download failed.":error.getMessage());stopForeground(true);}finally{if(!"installing".equals(phase(Transfers.this,current))){busy=false;stopSelf();}}}},"on-lite-download").start();return START_NOT_STICKY;
 }
 private Notification completed(){Notification n=notification("Download complete · Tap to install",1,1);n.flags=Notification.FLAG_AUTO_CANCEL;return n;}
 public static void verify(Context c,File file,String name,int version,String expected)throws Exception{
  MessageDigest digest=MessageDigest.getInstance("SHA-256");InputStream in=new FileInputStream(file);try{byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}finally{in.close();}
  StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.US,"%02x",b&255));if(!expected.equalsIgnoreCase(hash.toString()))throw new IOException("APK checksum mismatch.");
  PackageManager pm=c.getPackageManager();PackageInfo apk=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNATURES);if(apk!=null&&(apk.signatures==null||apk.signatures.length==0))apk.signatures=archiveSignatures(file);if(apk==null||!name.equals(apk.packageName)||apk.versionCode!=version)throw new IOException("APK package identity mismatch.");
  try{PackageInfo old=pm.getPackageInfo(name,PackageManager.GET_SIGNATURES);if(old.versionCode>apk.versionCode)throw new IOException("A newer version is already installed.");if(!Arrays.equals(old.signatures,apk.signatures))throw new IOException("APK signing certificate differs from the installed app.");}catch(PackageManager.NameNotFoundException ignored){}
 }
 public static android.content.pm.Signature[] archiveSignatures(File file)throws Exception {
  java.util.jar.JarFile jar=new java.util.jar.JarFile(file,true);java.util.Set<String> expected=null;long expanded=0;int count=0;
  try {java.util.Enumeration<java.util.jar.JarEntry> all=jar.entries();byte[] buffer=new byte[8192];
   while(all.hasMoreElements()){java.util.jar.JarEntry entry=all.nextElement();if(entry.isDirectory()||entry.getName().startsWith("META-INF/"))continue;if(++count>12000)throw new IOException("Too many APK entries.");
    InputStream in=jar.getInputStream(entry);try{int n;while((n=in.read(buffer))!=-1){expanded+=n;if(expanded>512L*1024*1024)throw new IOException("APK expanded size exceeds the limit.");}}finally{in.close();}
    java.security.CodeSigner[] certs=entry.getCodeSigners();if(certs==null||certs.length==0)throw new IOException("APK contains an unsigned entry.");java.util.Set<String> current=new java.util.TreeSet<String>();for(java.security.CodeSigner signer:certs)current.add(new android.content.pm.Signature(signer.getSignerCertPath().getCertificates().get(0).getEncoded()).toCharsString());if(expected==null)expected=current;else if(!expected.equals(current))throw new IOException("APK entry signing certificates differ.");
   }
   if(expected==null||expected.isEmpty())throw new IOException("APK has no signing certificate.");android.content.pm.Signature[] signatures=new android.content.pm.Signature[expected.size()];int i=0;for(String value:expected)signatures[i++]=new android.content.pm.Signature(value);return signatures;
  }finally{jar.close();}
 }
 public static void install(Activity c,String name){
  String file=c.getSharedPreferences("transfers",0).getString(name+".file","");if(!file.matches("[0-9a-f]{64}\\.apk"))return;
  Uri uri;
  if(Build.VERSION.SDK_INT<24){File apk=new File(c.getFilesDir(),"packages/"+file);try{makePublic(c,apk);}catch(Exception e){throw new IllegalStateException("Cannot make the public APK readable for Android's installer.",e);}uri=Uri.fromFile(apk);}
  else uri=Uri.parse("content://"+c.getPackageName()+".apks/"+file);Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  c.startActivity(intent);
 }
 public static void makePublic(Context context,File apk)throws Exception{
  // Froyo's PackageInstaller parses a filesystem path and cannot open content: APKs.
  for(String[] command:new String[][]{{"chmod","711",context.getFilesDir().getAbsolutePath()},{"chmod","711",apk.getParentFile().getAbsolutePath()},{"chmod","644",apk.getAbsolutePath()}})if(Runtime.getRuntime().exec(command).waitFor()!=0)throw new IOException("Cannot share the APK with Android's installer.");
 }
 private void silent(final File file)throws Exception{
  final Class<?> observerClass=Class.forName("android.content.pm.IPackageInstallObserver");final String descriptor="android.content.pm.IPackageInstallObserver";
  final Binder binder=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
   if(code==IBinder.INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(descriptor);return true;}
   if(code==IBinder.FIRST_CALL_TRANSACTION){data.enforceInterface(descriptor);String name=data.readString();int result=data.readInt();if(current.equals(name)){phase(result==1?"installed":"ready",result==1?"Installed":"Automatic installation failed. Tap Install to use Android's installer.");stopForeground(true);busy=false;stopSelf();}return true;}return super.onTransact(code,data,reply,flags);
  }};
  Object observer=Proxy.newProxyInstance(observerClass.getClassLoader(),new Class<?>[]{observerClass},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args){if("asBinder".equals(method.getName()))return binder;return null;}});
  Method method=getPackageManager().getClass().getMethod("installPackage",Uri.class,observerClass,int.class,String.class);method.setAccessible(true);method.invoke(getPackageManager(),Uri.fromFile(file),observer,2,getPackageName());
  new Handler(getMainLooper()).postDelayed(new Runnable(){public void run(){if(busy){phase("ready","Installation did not finish. Tap Install to use Android's installer.");stopForeground(true);busy=false;stopSelf();}}},120000);
 }
 private Notification notification(String text,int total,int value){
  Notification n=new Notification(getApplicationInfo().icon,text,System.currentTimeMillis());n.flags=Notification.FLAG_ONGOING_EVENT;
  Intent open=appId==0&&(current.equals("com.oldschoolnetwork.core.lite")||current.equals("com.oldschoolnetwork.store.lite"))?new Intent(this,OfficialDownloadActivity.class).putExtra("service",current.contains("core")?"core":"store"):getPackageManager().getLaunchIntentForPackage(getPackageName()).putExtra("download_app_id",appId);n.contentIntent=PendingIntent.getActivity(this,0,open,0);
  RemoteViews views=new RemoteViews(getPackageName(),R.layout.lite_transfer);views.setTextViewText(R.id.transfer_title,"Oldschool Network");views.setTextViewText(R.id.transfer_status,text);views.setProgressBar(R.id.transfer_progress,total>0?total:100,value,total<=0);n.contentView=views;
  if(Build.VERSION.SDK_INT>=26)return Api26.channel(this,n);return n;
 }
 private static final class Api26 {static Notification channel(Context c,Notification n){NotificationManager m=(NotificationManager)c.getSystemService(NOTIFICATION_SERVICE);m.createNotificationChannel(new NotificationChannel("on_lite_transfer","Downloads and installation",NotificationManager.IMPORTANCE_LOW));return new Notification.Builder(c,"on_lite_transfer").setSmallIcon(n.icon).setContent(n.contentView).setContentIntent(n.contentIntent).setOngoing(true).build();}}
}
