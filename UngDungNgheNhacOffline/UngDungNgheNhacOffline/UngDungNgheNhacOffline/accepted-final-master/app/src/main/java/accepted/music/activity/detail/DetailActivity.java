package accepted.music.activity.detail;

import android.os.Bundle;

import androidx.annotation.Nullable;

import accepted.music.R;
import accepted.music.activity.BaseActivity;


public class DetailActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.activity_fade_in, R.anim.activity_no_transition);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.activity_no_transition, R.anim.activity_fade_out);
    }
}
