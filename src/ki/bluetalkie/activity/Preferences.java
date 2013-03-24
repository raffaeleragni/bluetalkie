package ki.bluetalkie.activity;

import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import ki.bluetalkie.R;

/**
 * 
 * @author Raffaele Ragni <raffaele.ragni@gmail.com>
 */
public class Preferences extends PreferenceActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.layout.preferences);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }
}
