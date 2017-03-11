package jp.stargroove.naoya.android.n2tinyvideo;

import java.util.ArrayList;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.ViewConfiguration;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

class SmallVideo  implements OnTouchListener {
    private Handler mHandler = new Handler();
    private VideoService vs;
    private boolean isEnabled = false;
    private RelativeLayout topLayout;
    private LinearLayout llButtons;
    private MyTask mUpdateProgressTask;
    private View view;
    private BlankView mBlankView;
    private TextView tv1;
    private TextView tv2;
    private SeekBar mSeekBar;
    private CustomVideoView mVideoView;
    private ScaleGestureDetector mScaleDetector;
    private ImageButton stopButton, pauseButton;
    private int viewX = 0, viewY = 0;
    private int originalVideoWidth, originalVideoHeight;

    private boolean isNoLimits;
    
    private void updateProgress() {
        int cp = mVideoView.getCurrentPosition();
        int dur = mVideoView.getDuration();
        mSeekBar.setMax(dur);
        mSeekBar.setProgress(cp);
        cp /= 1000;
        dur /= 1000;
        if (tv2.getWidth() < 100) {
            tv2.setText(String.format("%3d:%02d\n/%3d:%02d", cp / 60, cp % 60, dur / 60, dur % 60));
        } else {
            tv2.setText(String.format("%3d:%02d/%3d:%02d", cp / 60, cp % 60, dur / 60, dur % 60));
        }
        if (isPaused) {
            pauseButton.setImageResource(R.drawable.pv_play);
        } else {
            pauseButton.setImageResource(R.drawable.pv_pause);
        }
    }
    
    class MyTask extends AsyncTask<View, Integer, View> {
        LinearLayout ll;
        boolean mPaused;
        synchronized void pause() { mPaused = true; }
        synchronized void resume() { mPaused = false; }
        @Override protected View doInBackground(View... params) {
            ll = (LinearLayout)params[0].findViewById(jp.stargroove.naoya.android.n2tinyvideo.R.id.llVideo);
            for (int i = 0; i < 25; i++) {
                if (isCancelled()) return ll;
                synchronized (this) { 
                    if (mPaused) return ll; // もしpauseされていたらソッコー終了する 
                    if (mVideoView.isSoundOnly()) i = 0; // sound onlyなら表示しつづける。 
                    if (!isPaused && !mVideoView.isPlaying()) { // pauseされてなくて再生されていなかったら 
                        try {mVideoView.start(); } catch (Exception e) {} // スタートする。
                    }
                } 
                publishProgress(new Integer[]{i, 0});
                try {
                    Thread.sleep(200);
                } catch (Exception e) {}
            }
            return ll;
        }
        protected void     onProgressUpdate(Integer... values) {
            if (this != mUpdateProgressTask) { return; }
            int d = values[0];
            if (!mVideoView.isSoundOnly()) { // 動画の場合、テキストの背景を透明に。
                tv1.setBackgroundColor(0x00000000);
            } else {
                tv1.setBackgroundColor(0xff202020);
            }
            ll.setVisibility(View.VISIBLE);
            mSeekBar.setVisibility(View.VISIBLE);
            if (d == 24) {
                dismissButtons();
            }
            updateProgress();
        }
        protected void onPostExecute(View v) {
            if (this != mUpdateProgressTask) { return; }
            if (mPaused == true) { // もしpauseされてたらなにもしない。 
                mUpdateProgressTask = null;
            } else {
                dismissButtons();
                mUpdateProgressTask = null;
            }
        }
                
    }

    private synchronized void showButtons() {
        if (mUpdateProgressTask != null) {mUpdateProgressTask.cancel(true); }
        if (!mVideoView.isSoundOnly()) {
            tv1.setBackgroundColor(0x00000000);
        }
        mUpdateProgressTask = new MyTask();
        mUpdateProgressTask.execute(new View[] {view});
    }
    private synchronized void dismissButtons() {
        if (mUpdateProgressTask != null) {
                mUpdateProgressTask.cancel(true); mUpdateProgressTask = null;
        }
        if (!mVideoView.isSoundOnly()) {  llButtons.setVisibility(View.GONE); }
        mSeekBar.setVisibility(View.GONE);
    }
    private void setButtonsWidth(int w, int h) {
        LayoutParams lp = llButtons.getLayoutParams();
        lp.width = w;
        llButtons.setLayoutParams(lp);
        
        lp = mSeekBar.getLayoutParams();
        lp.width = w;
        mSeekBar.setLayoutParams(lp);

        lp = tv1.getLayoutParams();
        lp.width = w;
        tv1.setLayoutParams(lp);

        lp = mBlankView.getLayoutParams();
        lp.width  = w; lp.height = h;
        mBlankView.setLayoutParams(lp);

        updateProgress();
    }

    private void onPauseClicked() {
        if (mVideoView != null) {
            isPaused = !isPaused;
            if (isPaused) {
                mVideoView.pause();
            } else {
                mVideoView.start();
            }
        }
        updateProgress();
    }
    
    
    public SmallVideo(VideoService service, final Uri uri) throws Exception {
        vs = service;

        // Contextからインフレータを作成し、重ね合わせするViewを作成する
        LayoutInflater layoutInflater = LayoutInflater.from(service);
        final View topView = layoutInflater.inflate(R.layout.overlay, null); 
        view = topView;
        mBlankView = (BlankView)topView.findViewById(R.id.bg);
        mVideoView = (CustomVideoView)topView.findViewById(R.id.videoView1);
        topLayout = (RelativeLayout)topView.findViewById(R.id.overlayTop);
        tv2 = (TextView)topView.findViewById(jp.stargroove.naoya.android.n2tinyvideo.R.id.textView2);
        mSeekBar = (SeekBar)topView.findViewById(jp.stargroove.naoya.android.n2tinyvideo.R.id.seekBar);
        llButtons = (LinearLayout)view.findViewById(R.id.llVideo);
        // WindowManagerを取得する
        final WindowManager wm =(WindowManager) service.getSystemService(Context.WINDOW_SERVICE); 
        this.wm = wm;
        
        mVideoView.setOnKeyListener(new OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
                Log.d(VideoActivity.TAG, "service_view_keyevent:" + event.toString());
                return false;
            }
        });
        
        mSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mVideoView.seekTo(progress);
                    updateProgress();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                mVideoView.pause();
                if (mUpdateProgressTask != null) {
                    mUpdateProgressTask.pause();
                }
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (!isPaused) mVideoView.start();
                if (mUpdateProgressTask != null) {
                    mUpdateProgressTask.resume();
                }
            }
        });

        stopButton = (ImageButton)view.findViewById(R.id.button1);
        stopButton.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                vs.removeView(SmallVideo.this);
            }
        });
        
        pauseButton = (ImageButton)view.findViewById(R.id.buttonPause);
        pauseButton.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                onPauseClicked();
                showButtons();
            }
        });
        
        view.findViewById(R.id.buttonRew).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (mVideoView != null) {
                    int cp = mVideoView.getCurrentPosition() - vs.getRewMillis();
                    if (cp < 0) cp = 0;
                    mVideoView.seekTo(cp);
                }
                showButtons();
            }        });
        view.findViewById(R.id.buttonFF).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (mVideoView != null) {   mVideoView.seekTo(mVideoView.getCurrentPosition() + vs.getFfMillis() );  }
                showButtons();
            }
        });

        
        tv1 = (TextView) view.findViewById(R.id.textView1);
        tv1.setText(uri.getLastPathSegment());
        tv1.setOnTouchListener(this);
        mBlankView.setOnTouchListener(this);

        mVideoView.setOnSizeChangedListener(new CustomVideoView.OnSizeChangedListener() {
            @Override public void onSizeChanged(int w, int h) { 
                if (w == 0 && h == 0) {
                    final float scale = vs.getResources().getDisplayMetrics().density;  
                    // sound only!
                    setButtonsWidth((int)(200 * scale), (int)(40 * scale));
                } else {
                    setButtonsWidth(w, h);
                }
            }
        });
        mVideoView.setOnErrorListener(new OnErrorListener(){
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
//                Log.d(MA.TAG, "@@@@onError:" + what + "," + extra);
                Toast.makeText(vs, vs.getStringF(R.string.video_cannotplay, uri.getLastPathSegment()), Toast.LENGTH_SHORT).show();
                stopAndRemove();
                return false;
            }
        });
        
        mVideoView.setOnPreparedListener(new OnPreparedListener(){
            @Override public void onPrepared(MediaPlayer mp) {
                originalVideoWidth = mVideoView.getVideoWidth();
                originalVideoHeight = mVideoView.getVideoHeight();
//                Log.d(MA.TAG, "@@@@onPrepared size:"+ originalVideoWidth + "x" + originalVideoHeight);
                topLayout.setLayoutParams(new FrameLayout.LayoutParams(originalVideoWidth, originalVideoHeight));
                topLayout.requestLayout();
                if (!isEnabled) {
                    wm.addView(view, createLayoutParams());
                    isEnabled = true;
                } else {
                    wm.updateViewLayout(view, createLayoutParams());
                }
            }
        });

        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
            @Override public void onCompletion(MediaPlayer mp) {
                Toast.makeText(vs, vs.getStringF(R.string.video_completed, uri.getLastPathSegment()), Toast.LENGTH_SHORT).show();
                stopAndRemove();
            }
        });
        
        mScaleDetector = new ScaleGestureDetector(service, new ScaleGestureDetector.OnScaleGestureListener() {
            float scale = 1.0f;
            @Override public void onScaleEnd(ScaleGestureDetector detector) {
                float nowScale = ((float)mVideoView.getWidth()) / originalVideoWidth;
                if (nowScale < scale) { scale = nowScale; }
                adjust(1.0f);
            }
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (originalVideoWidth <= 0 || originalVideoHeight <= 0) return false;
                float nowScale = ((float)mVideoView.getWidth()) / originalVideoWidth;
                if (nowScale < scale) { scale = nowScale; }
                return true;        
            }
            private void adjust(float sf) {
                scale *= sf;
                // ガードする
                int minHeight = llButtons.getHeight() * 2;
                int w, h;
                do {
                    w = (int)(originalVideoWidth * scale);
                    h = (int)(originalVideoHeight * scale);
                    if (h > minHeight) break;
                    scale = ((float)minHeight + 1) / originalVideoHeight;
                } while (true);
//                android.util.Log.d("@@@@", "sf:" + sf + "newsize:" + w + "x" + h);
                mVideoView.setLayoutParams(new RelativeLayout.LayoutParams(w,h));
                mVideoView.changeVideoSize(w,h);
                topLayout.setLayoutParams(new LayoutParams(w,h));
                setButtonsWidth(w, h);
                topLayout.requestLayout();
                wm.updateViewLayout(view, createLayoutParams());
            }
            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (originalVideoWidth <= 0 || originalVideoHeight <= 0) return false;
                adjust(detector.getScaleFactor());
                return true;
            }
        });

        // 再生
        try {
            mVideoView.setVideoURI(uri);
            mVideoView.start();
            showButtons();
        } catch (Exception e) {
            wm.removeView(view);
            throw e;
        }
        
        try {
            view.requestLayout();
            view.invalidate();
            // Viewを画面上に重ね合わせする
            wm.addView(view, createLayoutParams());
            isEnabled = true;
        } catch (Exception e) {
            throw e;
        }
    }
    WindowManager wm;
    private WindowManager.LayoutParams createLayoutParams() {
        int flags =                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH; 
//              WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (isNoLimits) { flags |=  WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS; }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                viewX, viewY, 
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT, //// 
//               WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                flags, PixelFormat.TRANSLUCENT);
        return params;
    }

    boolean isDragging;
    boolean isDragged;
    boolean isPaused;
    int touchX, touchY;
    @Override
    public boolean onTouch(View v, MotionEvent me) {
        //
        int x = (int) me.getRawX();
        int y = (int) me.getRawY();
        int action = me.getAction();
        
        int pointerCount = me.getPointerCount();
        
        if (pointerCount >= 2) {
            isDragging = false;
            mScaleDetector.onTouchEvent(me);            
        } 
        if (action == MotionEvent.ACTION_POINTER_3_DOWN)  {
            // もし3タッチされたら…
            isNoLimits = !isNoLimits;
        }
        
        if (action == MotionEvent.ACTION_DOWN) {
            int loc[] = new int[2];  
            view.getLocationOnScreen(loc);
            int cvx = loc[0] + view.getWidth() / 2;
            int cvy = loc[1] + view.getHeight() / 2;
            int cdx = wm.getDefaultDisplay().getWidth() / 2;
            int cdy = wm.getDefaultDisplay().getHeight() / 2;
            viewX = cvx - cdx;
            viewY = cvy - cdy;
                                
                    
            isDragging = true;
            isDragged = false;
            touchX = x;
            touchY = y;
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_OUTSIDE) {
            if (isDragging && !isDragged) {
                isDragging = false;

                if (llButtons.getVisibility() == View.VISIBLE) {
                    dismissButtons();
                } else {
                    showButtons();
                }
                return true;
            }
            isDragging = false;
            if (!isPaused && !mVideoView.isPlaying()) { // 止まっているときの対処
                mVideoView.start();
            }
            return true;
        }
        
        if (action == MotionEvent.ACTION_MOVE && isDragging) {
            int diffX = touchX - x;
            int diffY = touchY - y;
            int touchSlop = ViewConfiguration.getTouchSlop();
            if (isDragged == false) {
                if (diffX * diffX + diffY *diffY <  touchSlop * touchSlop) return true;
            }
             isDragged = true;
            
             viewX -= diffX;
             viewY -= diffY;
             view.requestLayout();
             wm.updateViewLayout(view, createLayoutParams());
             view.invalidate();
             touchX = x;
             touchY = y;
             if (isPaused) {
                 mVideoView.start();
                 mHandler.post(new Runnable() {
                    @Override public void run() { mVideoView.pause(); } 
                 });
             }
        }
        return false;
    }

    void stopAndRemove() {
        try {
            wm.removeView(view);
            mUpdateProgressTask.cancel(true); mUpdateProgressTask = null;
        } catch (Exception e) {
            Log.d(VideoActivity.TAG, "stopAndRemove: " + e);
        }
    }
    
}

public class VideoService extends Service  {
    private SharedPreferences mPreferences;
    private Resources mResources;
    private ArrayList <SmallVideo> mSmallVideos = new ArrayList<SmallVideo>();

    public String getRString(int resId) {
        if (mResources == null) return "something is out of order now.";
        return mResources.getString(resId);
    }
    public String getStringF(int resId, Object... args) {
        return String.format(getRString(resId), args);
    }
    
    public int getFfMillis() {
        try {
            return Integer.parseInt(mPreferences.getString("ffSeconds", "15")) * 1000;
        } catch (Exception e) {
            return 15000;
        }
    }
    public int getRewMillis() {
        try {
            return Integer.parseInt(mPreferences.getString("rewSeconds", "15")) * 1000;
        } catch (Exception e) {
            return 15000;
        }
    }
    
    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mResources = getResources();
        if (intent == null) { stopSelf(); return; }
        Uri uri = intent.getData();
        Notification lNotification = new Notification(0, "ticker", System.currentTimeMillis());
        startForeground(1, lNotification);

        if (uri != null) {
            android.util.Log.d("VIDEOSERVICE", uri.toString());
            try {
                SmallVideo sv = new SmallVideo(this, uri);
                mSmallVideos.add(sv);
            }  catch (Exception e) {
                android.util.Log.d("VIDEOSERVICE", e.toString());
                Toast.makeText(this, getStringF(R.string.video_cannotplay, uri.toString()), Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            stopSelf();
            return;
        }
    }
 
    @Override
    public void onCreate() {
        super.onCreate();
    }
 
    @Override
    public void onDestroy() {
        super.onDestroy();
        for (SmallVideo sv:mSmallVideos) {  sv.stopAndRemove();    }
        mSmallVideos.clear();
    }
 
    public void removeView(SmallVideo sv) {
        sv.stopAndRemove();
        mSmallVideos.remove(sv);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    

}
