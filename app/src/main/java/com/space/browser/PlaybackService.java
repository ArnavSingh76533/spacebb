package com.space.browser;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;

/** A foreground lifetime and transport notification for the browser's existing media player. */
public class PlaybackService extends Service {
    interface Controller {void play();void pause();void stop();}
    static Controller controller;
    static volatile boolean running;
    private MediaSession session;
    private static final String CHANNEL="space_playback";
    protected boolean privatePlayback(){return false;}
    @Override public void onCreate(){
        super.onCreate();running=true;
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Media playback",NotificationManager.IMPORTANCE_LOW));
        session=new MediaSession(this,"Space playback");
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){if(controller!=null)controller.play();}
            @Override public void onPause(){if(controller!=null)controller.pause();}
            @Override public void onStop(){if(controller!=null)controller.stop();stopSelf();}
        });session.setActive(true);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(controller==null){stopSelf();return START_NOT_STICKY;}
        String action=intent==null?null:intent.getAction();
        if("stop".equals(action)){controller.stop();stopSelf();return START_NOT_STICKY;}
        if("pause".equals(action))controller.pause();
        if("play".equals(action))controller.play();
        String title=privatePlayback()?"Private media":intent==null?"Space Browser":intent.getStringExtra("title");
        if(title==null||title.isEmpty())title="Space Browser";
        boolean playing=intent==null||intent.getBooleanExtra("playing",true);
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).putString(MediaMetadata.METADATA_KEY_ARTIST,"Space Browser").build());
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_PLAY_PAUSE).setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,PlaybackState.PLAYBACK_POSITION_UNKNOWN,playing?1:0).build());
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,privatePlayback()?PrivateActivity.class:MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText("Space Browser · background playback").setContentIntent(open).setVisibility(privatePlayback()?Notification.VISIBILITY_SECRET:Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setOngoing(playing)
            .addAction(new Notification.Action.Builder(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing?"Pause":"Play",action(playing?"pause":"play")).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop",action("stop")).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1)).build();
        startForeground(73,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        return START_NOT_STICKY;
    }
    private PendingIntent action(String action){return PendingIntent.getService(this,action.hashCode(),new Intent(this,getClass()).setAction(action).putExtra("playing",!"pause".equals(action)),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    @Override public void onTaskRemoved(Intent intent){if(controller!=null)controller.stop();stopSelf();}
    @Override public void onDestroy(){running=false;if(session!=null){session.setActive(false);session.release();}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
