package com.oldschoolnetwork.lite;
import android.content.*;import android.app.NotificationManager;import java.io.File;
/** Tracks installation only; it never grants ownership of unrelated installed apps. */
public final class PackageChanged extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){if(intent.getData()==null)return;String name=intent.getData().getSchemeSpecificPart();SharedPreferences p=context.getSharedPreferences("transfers",0);String file=p.getString(name+".file","");if(!file.matches("[0-9a-f]{64}\\.apk"))return;new File(context.getFilesDir(),"packages/"+file).delete();p.edit().putString(name+".phase","installed").putString(name+".message","Installed").remove(name+".file").commit();((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(810);((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(811);context.sendBroadcast(new Intent(context.getPackageName()+".TRANSFER").setPackage(context.getPackageName()));}
}
