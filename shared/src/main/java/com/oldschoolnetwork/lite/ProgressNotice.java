package com.oldschoolnetwork.lite;
import android.app.*;import android.content.*;import android.os.Build;import android.widget.RemoteViews;
public final class ProgressNotice {
 public static Notification build(Context context,String text,int total,int value){
  Notification n=new Notification(context.getApplicationInfo().icon,text,System.currentTimeMillis());n.flags=Notification.FLAG_ONGOING_EVENT;
  n.contentIntent=PendingIntent.getActivity(context,0,context.getPackageManager().getLaunchIntentForPackage(context.getPackageName()),0);
  RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.lite_transfer);views.setTextViewText(R.id.transfer_title,"Oldschool Network");views.setTextViewText(R.id.transfer_status,text);views.setProgressBar(R.id.transfer_progress,total>0?total:100,value,total<=0);n.contentView=views;
  return Build.VERSION.SDK_INT>=26?Modern.channel(context,n):n;
 }
 private static final class Modern {static Notification channel(Context c,Notification n){NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);m.createNotificationChannel(new NotificationChannel("on_lite_transfer","Downloads and installation",NotificationManager.IMPORTANCE_LOW));return new Notification.Builder(c,"on_lite_transfer").setSmallIcon(n.icon).setContent(n.contentView).setContentIntent(n.contentIntent).setOngoing(true).build();}}
}
