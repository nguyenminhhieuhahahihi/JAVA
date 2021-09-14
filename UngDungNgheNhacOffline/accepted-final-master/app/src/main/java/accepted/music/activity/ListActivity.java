package accepted.music.activity;

import android.os.Bundle;

import androidx.annotation.Nullable;

import accepted.music.R;


public class ListActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.activity_bottom_slide_in, R.anim.activity_no_transition);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.activity_no_transition, R.anim.activity_fade_out);
    }
}
