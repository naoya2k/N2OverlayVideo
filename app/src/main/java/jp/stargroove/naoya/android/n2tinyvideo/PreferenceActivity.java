package jp.stargroove.naoya.android.n2tinyvideo;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class PreferenceActivity extends android.preference.PreferenceActivity implements OnPreferenceChangeListener {
    Resources res;
    SharedPreferences sp;
    private Preference ff; //=     this.findPreference("ffSeconds");
    private Preference rew; //=     this.findPreference("rewSeconds");
    private Preference searchInfo;
    private int changeCount;  
    private void setSummary() {
        ff.setSummary(String.format(res.getString(R.string.ffSeconds_summary), sp.getString(("ffSeconds"), "0")));
        rew.setSummary(String.format(res.getString(R.string.rewSeconds_summary), sp.getString(("rewSeconds"), "0")));
    }

    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        changeCount = 0;
        res = getResources();
        sp = PreferenceManager.getDefaultSharedPreferences(this); 
        addPreferencesFromResource(R.xml.pref);
        ff =  findPreference("ffSeconds");
        rew = findPreference("rewSeconds");
        searchInfo =  findPreference("searchInfo");
        setSummary();

        ff.setOnPreferenceChangeListener(this);
        rew.setOnPreferenceChangeListener(this);
        searchInfo.setOnPreferenceChangeListener(this);
    }


    @Override public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == ff) {
            ff.setSummary(String.format(res.getString(R.string.ffSeconds_summary), (String)newValue));
            return true;
        } else if (preference == rew) {
            rew.setSummary(String.format(res.getString(R.string.rewSeconds_summary), (String)newValue));
            return true;
        } else if (preference == searchInfo) {
            changeCount++;
            if (changeCount > 16 && !sp.getBoolean("TESTMODE", false)) {
                Toast.makeText(this, "NO ADVERTISE MODE!!", Toast.LENGTH_SHORT).show();
                sp.edit().putBoolean("TESTMODE", true).commit();
            }
            return true;
        }
        return false;
    }
}
