package com.oldschoolnetwork.store.lite;
import android.app.Activity;import android.os.Bundle;import android.view.*;import android.widget.*;import android.graphics.Matrix;import com.oldschoolnetwork.lite.Images;
public final class MediaActivity extends Activity {
 private float scale=1,x,y,lastX,lastY;private Matrix matrix=new Matrix();
 public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.media);final ImageView image=(ImageView)findViewById(R.id.image);Images.load(this,image,getIntent().getStringExtra("url"),1024);
  final ScaleGestureDetector zoom=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){scale=Math.max(1,Math.min(5,scale*d.getScaleFactor()));apply(image);return true;}});
  image.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,android.view.MotionEvent event){zoom.onTouchEvent(event);if(event.getAction()==MotionEvent.ACTION_DOWN){lastX=event.getX();lastY=event.getY();}else if(event.getAction()==MotionEvent.ACTION_MOVE&&!zoom.isInProgress()&&scale>1){x+=event.getX()-lastX;y+=event.getY()-lastY;lastX=event.getX();lastY=event.getY();apply(image);}return true;}});
  findViewById(R.id.close).setOnClickListener(new View.OnClickListener(){public void onClick(View v){finish();}});
 }
 private void apply(ImageView view){if(view.getDrawable()==null)return;float fit=Math.min((float)view.getWidth()/view.getDrawable().getIntrinsicWidth(),(float)view.getHeight()/view.getDrawable().getIntrinsicHeight());matrix.reset();matrix.postScale(fit*scale,fit*scale);matrix.postTranslate((view.getWidth()-view.getDrawable().getIntrinsicWidth()*fit*scale)/2+x,(view.getHeight()-view.getDrawable().getIntrinsicHeight()*fit*scale)/2+y);view.setScaleType(ImageView.ScaleType.MATRIX);view.setImageMatrix(matrix);}
}
