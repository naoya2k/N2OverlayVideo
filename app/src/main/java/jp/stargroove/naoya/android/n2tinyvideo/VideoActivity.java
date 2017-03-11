
package jp.stargroove.naoya.android.n2tinyvideo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EmptyStackException;
import java.util.Stack;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

public class VideoActivity extends Activity {
    SharedPreferences mPreferences;    
    
    static boolean TEST_MODE;
    static final String TAG = "TINYVIDEO";
    // 別クラスに分けたほうがいいかもな。
    File currentDirectory;
    File[] dirs;
    String[] descriptions;
    Stack<File> stack = new Stack<File>(); 
    
    Button startButton;
    Button stopButton;
    ListView listView1;
    ArrayAdapter<String> aa1;
    
    private String getNicoDescriptions(File d) {
        String filepath = d.getPath();
//        android.util.Log.d("VIDEOPLAYER " , "getNico "+ filepath);
        int point = filepath.lastIndexOf(".");
        String filename = "";
        if (point != -1) { 
            filepath =filepath.substring(0, point) + ".info";
        } else {
            return null;
        }
        File infoFile = new File(filepath);
        if (! infoFile.exists()) {
            String name = infoFile.getName();
            String parent = infoFile.getParent();
            filepath = parent + "/infos/" + name;
            infoFile = new File(filepath);
            if (! infoFile.exists()) {
                return null; 
            }
        }
        if (! infoFile.isFile()) {  return null; }
        try {
            BufferedReader br = new BufferedReader(new FileReader(infoFile));
            do {
                String s = br.readLine();
                if (s == null) { return null; }
                if (s.startsWith("title=")) { return s.substring(6); }
            } while (true);
        } catch (Exception e) {
            return null;
        }
    }
    
    static final int MENU_ID_SET_DEFAULT_DIR = 0;
    static final int MENU_ID_SETTING = 1;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_ID_SET_DEFAULT_DIR, Menu.NONE, getString(R.string.menu_setdirectory));
        menu.add(Menu.NONE, MENU_ID_SETTING, Menu.NONE, getString(R.string.menu_settings));
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {     // オプションメニューが表示される度に呼び出されます
        return super.onPrepareOptionsMenu(menu);
    }
    // オプションメニューアイテムが選択された時に呼び出されます
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_ID_SET_DEFAULT_DIR:
            mPreferences.edit().putString("STARTDIR", this.currentDirectory.getPath()).commit();
            return true;
        case MENU_ID_SETTING:
            this.startActivity(new Intent(this, PreferenceActivity.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    synchronized void setCurrentDirectory(File currentDir) {
        android.util.Log.d("HOGE", currentDir.toString());
        
        File parent = currentDir.getParentFile();
//        if (!currentDir.isDirectory()) { //do nothing;
//            dirs = new File[1];
//            dirs[0] = parent;
//            setDirsToList(dirs, parent);
//            return; 
//        }
        final boolean isIgnoreDot = mPreferences.getBoolean("ignoreDot", true);
        final boolean isIgnoreUnderscore = mPreferences.getBoolean("ignoreUnderscore", true);
        final boolean isIgnoreInfo = mPreferences.getBoolean("ignoreInfo", true);
        currentDirectory = currentDir;
        setTitle(currentDirectory.getPath() + " - " + getResources().getString(R.string.app_name));
        
        File[] dirs0 = currentDir.listFiles(new FilenameFilter() {
            @Override public boolean accept(File dir, String filename) {
                if (isIgnoreDot && filename.startsWith(".")) return false;
                if (isIgnoreUnderscore && filename.startsWith("_")) return false;
                if (isIgnoreDot && filename.endsWith("~")) return false;
                if (isIgnoreInfo && filename.endsWith(".info")) return false;
                if (isIgnoreDot && filename.startsWith("LOST.DIR")) return false;
                return true;
            }
        });
        
        if (dirs0 == null) { dirs0 = new File[0]; }
        
        Arrays.sort(dirs0, new Comparator<File>() {
            @Override public int compare(File lhs, File rhs) {
                boolean ld = lhs.isDirectory();
                boolean rd = rhs.isDirectory();
                if (ld && !rd) return -1;
                if (!ld && rd) return 1;
                return lhs.getName().compareTo(rhs.getName());
            }
        });

        // 親ディレクトリがあれば先頭に追加する
        if (parent != null) {
            dirs = new File[dirs0.length + 1];
            dirs[0] = parent;
            System.arraycopy(dirs0, 0, dirs, 1, dirs0.length);
        } else {
            dirs = dirs0;
        }

        setDirsToList(dirs, parent);
    }
    
    private void setDirsToList(File []dirs, File parent) {
        final boolean isGetNico = mPreferences.getBoolean("searchInfo", true);
        final int filenum = dirs.length;

        descriptions = new String[filenum];
        for (int i = 0; i < filenum; i++) {
            if (parent != null && i == 0) {
                descriptions[i] = "[parent directory]";
                continue;
            }
            if (dirs[i].isDirectory()) {
                descriptions[i] = "[" + dirs[i].getName() + "]";
            } else {
                String s = null;
                if (isGetNico) { s= getNicoDescriptions(dirs[i]); } 
                if (s != null) {
                    descriptions[i] = s + "\n  (" + dirs[i].getName() + ")"; 
                } else {
                    descriptions[i] = "" + dirs[i].getName();
                }
            }
        }
//        aa1 = new ArrayAdapter<String>(this,  android.R.layout.simple_expandable_list_item_1, descriptions);
        aa1 = new ArrayAdapter<String>(this,  R.layout.rawdata, descriptions);
        listView1.setAdapter(aa1);

    }
    
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        final Intent newIntent = new Intent(VideoActivity.this, VideoService.class);
        Intent it = getIntent();
        
        if (it != null) {
            Uri uri = it.getData(); 
            if (uri != null) {
                android.util.Log.d("VIDEOOVERLAY", it.getData().toString());
                newIntent.setData(it.getData());
                startService(newIntent); finish(); return;
            }
        }
        
        setContentView(R.layout.main);
          
        listView1 = (ListView)findViewById(R.id.listView1);
        stopButton = (Button) findViewById(R.id.stopButton);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        TEST_MODE = mPreferences.getBoolean("TESTMODE", false);
        try {
            currentDirectory = new File(mPreferences.getString("STARTDIR", null));
        } catch (Exception e) {
            // go through
       }
        if (currentDirectory == null || !currentDirectory.isDirectory()) {
//            currentDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            currentDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
        }

        stopButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(newIntent);
            }
        });
        
        listView1.setOnItemLongClickListener(new OnItemLongClickListener () {
            @Override public boolean onItemLongClick(AdapterView<?> arg0, View v, int position, long id) {
                File f = dirs[position];
                if (!f.isFile()) { return false; }
                if (!f.canRead()) { return false; }
                Uri u = Uri.fromFile(f);
                Intent it = new Intent(Intent.ACTION_VIEW);
                it.setDataAndType(u, "video/*");
//                it.setData(u);
                startActivity(it);
                return true;
            }
        });
        
        listView1.setOnItemClickListener(new OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
                File f = dirs[position];
//                if (f.isDirectory()) {
                if (!f.isFile()) {
                    stack.push(currentDirectory);
                    setCurrentDirectory(f); 
                    return; 
                }
                if (f.canRead()) {
                    Uri u = Uri.fromFile(f);
//                  android.util.Log.d("VIDEOOVERLAY", "Uri = " + u);
                  newIntent.setData(u);
                  startService(newIntent);
                }
            }
        });

        setCurrentDirectory(currentDirectory);

    }

    boolean back_pressed_flag = false;

    @Override
    public void onResume() {
        super.onResume();
        back_pressed_flag = false;
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
//        setCurrentDirectory(currentDirectory);
    }

    // キーイベント発生時、呼び出されます
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    back_pressed_flag = true;
                default:
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode())          {
            case KeyEvent.KEYCODE_BACK:
                if (back_pressed_flag) {
                    back_pressed_flag = false;
                    try {
                        setCurrentDirectory(stack.pop());
                        return true;
                    } catch (EmptyStackException ese) {
                        return super.dispatchKeyEvent(event);
                    }
                }
            default:
            }
        }
        return super.dispatchKeyEvent(event);
    }

}