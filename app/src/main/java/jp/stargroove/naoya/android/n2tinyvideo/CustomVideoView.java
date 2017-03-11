package jp.stargroove.naoya.android.n2tinyvideo;

/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * Displays a video file.  The VideoView class
 * can load images from various sources (such as resources or content
 * providers), takes care of computing its measurement from the video so that
 * it can be used in any layout manager, and provides various display options
 * such as scaling and tinting.
 */
public class CustomVideoView extends SurfaceView {
    private String TAG = "CustomVideoView";
    // settable by the client
    private Uri         mUri;
    private int         mDuration;

    // All the stuff we need for playing and showing a video
    private SurfaceHolder mSurfaceHolder = null;
    private MediaPlayer mMediaPlayer = null;
    private boolean     mIsPrepared;
    private int         mVideoWidth;
    private int         mVideoHeight;
    private int         mSurfaceWidth;
    private int         mSurfaceHeight;
    private OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private int         mCurrentBufferPercentage;
    private OnErrorListener mOnErrorListener;
    private boolean     mStartWhenPrepared;
    private int         mSeekWhenPrepared;
    private Context mContext;
    
    public boolean isSoundOnly() {
        return mVideoHeight == 0 && mVideoWidth == 0;
    }
    
    public interface OnSizeChangedListener {
        public void onSizeChanged(int w, int h);
    }
    private OnSizeChangedListener   mOnSizeChangedListener;

    public CustomVideoView(Context context) {
        super(context);
        mContext = context;
        initVideoView();
    }
    
    public CustomVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        mContext = context;
        initVideoView();
    }
    
    public CustomVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        initVideoView();
    }
    
    
    public void setOnSizeChangedListener(OnSizeChangedListener listener) {
        mOnSizeChangedListener = listener;
    }

    public enum DisplayMode {
        ORIGINAL,       // original aspect ratio
        FULL,    // fit to screen
        ZOOM            // zoom in
     };
     protected DisplayMode screenMode = DisplayMode.ORIGINAL;
     public DisplayMode getDisplayMode(){ return screenMode; }
     public void setDisplayMode(DisplayMode mode){ screenMode = mode; requestLayout(); invalidate(); }
     public int getVideoWidth() { return mVideoWidth; }
     public int getVideoHeight() { return mVideoHeight; }
     public void changeVideoSize(int width, int height) {
         mVideoWidth = width;       
         mVideoHeight = height;

         // not sure whether it is useful or not but safe to do so
         getHolder().setFixedSize(width, height); 
         
         requestLayout();
         invalidate();     // very important, so that onMeasure will be triggered
     } 
     @Override
     protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
         int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
         int height = getDefaultSize(mVideoHeight, heightMeasureSpec);

         if (mVideoWidth == 0 && mVideoHeight == 0) {
             // this seems to be sound only media. set to default size.
             float scale = getContext(). getResources().getDisplayMetrics().density ;
             width = (int)(240 * scale); height = (int)(120 * scale);
         }             

         
         if (screenMode == DisplayMode.ORIGINAL) {
            if (mVideoWidth > 0 && mVideoHeight > 0) {
                if ( mVideoWidth * height  > width * mVideoHeight ) {
                    // video height exceeds screen, shrink it
                    height = width * mVideoHeight / mVideoWidth;
                } else if ( mVideoWidth * height  < width * mVideoHeight ) {
                    // video width exceeds screen, shrink it
                    width = height * mVideoWidth / mVideoHeight;
                } else {
                    // aspect ratio is correct
                }
            }
         }
         else if (screenMode == DisplayMode.FULL) {
            // just use the default screen width and screen height 
         }
         else if (screenMode == DisplayMode.ZOOM) {
            // zoom video
            if (mVideoWidth > 0 && mVideoHeight > 0 && mVideoWidth < width) {
                height = mVideoHeight * width / mVideoWidth;
            }
         }
         
         // must set this at the end
         setMeasuredDimension(width, height);
         if (mOnSizeChangedListener  != null) {mOnSizeChangedListener.onSizeChanged(width, height); } 
     } 

    
    public int resolveAdjustedSize(int desiredSize, int measureSpec) {
        int result = desiredSize;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize =  MeasureSpec.getSize(measureSpec);

        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                /* Parent says we can be as big as we want. Just don't be larger
                 * than max size imposed on ourselves.
                 */
                result = desiredSize;
                break;

            case MeasureSpec.AT_MOST:
                /* Parent says we can be as big as we want, up to specSize. 
                 * Don't be larger than specSize, and don't be larger than 
                 * the max size imposed on ourselves.
                 */
                result = Math.min(desiredSize, specSize);
                break;
                
            case MeasureSpec.EXACTLY:
                // No choice. Do what we are told.
                result = specSize;
                break;
        }
        return result;
}
    
    private void initVideoView() {
        mVideoWidth = 0;
        mVideoHeight = 0;
        getHolder().addCallback(mSHCallback);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    public void setVideoPath(String path) {
        setVideoURI(Uri.parse(path));
    }

    public void setVideoURI(Uri uri) {
        mUri = uri;
        mStartWhenPrepared = false;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }
    
    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private void openVideo() {
        if (mUri == null || mSurfaceHolder == null) {
            return;             // not ready for playback just yet, will try again later
        }
        // Tell the music playback service to pause 
        // TODO: these constants need to be published somewhere in the framework.
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        mContext.sendBroadcast(i);
        
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mIsPrepared = false;
    //        Log.v(TAG, "reset duration to -1 in openVideo");
            mDuration = -1;
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mCurrentBufferPercentage = 0;
            mMediaPlayer.setDataSource(mContext, mUri);
            mMediaPlayer.setDisplay(mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.prepareAsync();
        } catch (IOException ex) {
//            Log.w(TAG, "Unable to open content: " + mUri, ex);
            openError(ex);
            return;
        } catch (IllegalArgumentException ex) {
//            Log.w(TAG, "Unable to open content: " + mUri, ex);
            openError(ex);
            return;
        }
    }
    
    private void openError(Exception ex) {
        if (mOnErrorListener != null) { mOnErrorListener.onError(mMediaPlayer, -1, -1); }

    }
    
    MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
        new MediaPlayer.OnVideoSizeChangedListener() {
            public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                mVideoWidth = mp.getVideoWidth();
                mVideoHeight = mp.getVideoHeight();
                if (mVideoWidth != 0 && mVideoHeight != 0) {
                    getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                }
            }
    };
    
    MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp) {
            mIsPrepared = true;
            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mMediaPlayer);
            }
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();
            if (mVideoWidth != 0 && mVideoHeight != 0) {
//                Log.i("@@@@", "video size: " + mVideoWidth +"/"+ mVideoHeight);
                getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                if (mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
                    // We didn't actually change the size (it was already at the size
                    // we need), so we won't get a "surface changed" callback, so
                    // start the video here instead of in the callback.
                    if (mSeekWhenPrepared != 0) {
                        mMediaPlayer.seekTo(mSeekWhenPrepared);
                        mSeekWhenPrepared = 0;
                    }
                    if (mStartWhenPrepared) {
                        mMediaPlayer.start();
                        mStartWhenPrepared = false;
                    } else if (!isPlaying() &&
                            (mSeekWhenPrepared != 0 || getCurrentPosition() > 0)) {
                   }
                }
            } else {
                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                if (mSeekWhenPrepared != 0) {
                    mMediaPlayer.seekTo(mSeekWhenPrepared);
                    mSeekWhenPrepared = 0;
                }
                if (mStartWhenPrepared) {
                    mMediaPlayer.start();
                    mStartWhenPrepared = false;
                }
            }
        }
    };

    private MediaPlayer.OnCompletionListener mCompletionListener =
        new MediaPlayer.OnCompletionListener() {
        public void onCompletion(MediaPlayer mp) {
            if (mOnCompletionListener != null) {
                mOnCompletionListener.onCompletion(mMediaPlayer);
            }
        }
    };
    private MediaPlayer.OnErrorListener mErrorListener = new MediaPlayer.OnErrorListener() {
        public boolean onError(MediaPlayer mp, int framework_err, int impl_err) {
            Log.d(TAG, "Error: " + framework_err + "," + impl_err);

            /* If an error handler has been supplied, use it and finish. */
            if (mOnErrorListener != null) {
                if (mOnErrorListener.onError(mMediaPlayer, framework_err, impl_err)) {
                    return true;
                }
            }
            return true;
        }
    };

    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener = new MediaPlayer.OnBufferingUpdateListener() {
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            mCurrentBufferPercentage = percent;
        }
    };

    /**
     * Register a callback to be invoked when the media file
     * is loaded and ready to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    /**
     * Register a callback to be invoked when the end of a media file
     * has been reached during playback.
     *
     * @param l The callback that will be run
     */
    public void setOnCompletionListener(OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    /**
     * Register a callback to be invoked when an error occurs
     * during playback or setup.  If no listener is specified,
     * or if the listener returned false, VideoView will inform
     * the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(OnErrorListener l) {
        mOnErrorListener = l;
    }

    SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback() {
        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)  {
            int prevW = mSurfaceWidth;
            mSurfaceWidth = w;
            mSurfaceHeight = h;
            if (mMediaPlayer != null && mIsPrepared && mVideoWidth == w && mVideoHeight == h) {
                if (mSeekWhenPrepared != 0) {
                    mMediaPlayer.seekTo(mSeekWhenPrepared);
                    mSeekWhenPrepared = 0;
                }
                if (mMediaPlayer.isPlaying() || prevW == 0) { //初回かスタート済み
                    mMediaPlayer.start();
                } else {
                    mMediaPlayer.start(); try{ Thread.sleep(17); }catch(Exception e) {} mMediaPlayer.pause();
                }
            }

        }

        public void surfaceCreated(SurfaceHolder holder) {
            mSurfaceHolder = holder;
            openVideo();
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // after we return from this we can't use the surface any more
            mSurfaceHolder = null;
            if (mMediaPlayer != null) {
                mMediaPlayer.reset();
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mIsPrepared &&
                keyCode != KeyEvent.KEYCODE_BACK &&
                keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                keyCode != KeyEvent.KEYCODE_MENU &&
                keyCode != KeyEvent.KEYCODE_CALL &&
                keyCode != KeyEvent.KEYCODE_ENDCALL &&
                mMediaPlayer != null) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                } else {
                    start();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP  && mMediaPlayer.isPlaying()) {
                pause();
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public void start() {
        if (mMediaPlayer != null && mIsPrepared) {
            mMediaPlayer.start();
            mStartWhenPrepared = false;
        } else {
            mStartWhenPrepared = true;
        }
    }
    
    public void pause() {
        if (mMediaPlayer != null && mIsPrepared) {
            if (mMediaPlayer.isPlaying()) { 
                mMediaPlayer.pause();
            }
        }
        mStartWhenPrepared = false;
    }
    
    public int getDuration() {
        if (mMediaPlayer != null && mIsPrepared) {
            if (mDuration > 0) { return mDuration; }
            mDuration = mMediaPlayer.getDuration();
            return mDuration;
        } else {
            mDuration = -1;
            return mDuration;
        }
    }
    
    public int getCurrentPosition() {
        if (mMediaPlayer != null && mIsPrepared) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }
    
    public void seekTo(int msec) {
        if (mMediaPlayer != null && mIsPrepared) {
            mMediaPlayer.seekTo(msec);
        } else {
            mSeekWhenPrepared = msec;
        }
    }    
            
    public boolean isPlaying() {
        if (mMediaPlayer != null && mIsPrepared) {
            return mMediaPlayer.isPlaying();
        }
        return false;
    }
    
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }
}
