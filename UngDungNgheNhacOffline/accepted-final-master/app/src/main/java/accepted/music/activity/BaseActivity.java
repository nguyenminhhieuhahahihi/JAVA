package accepted.music.activity;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import accepted.player.PlayerClient;

public class BaseActivity extends AppCompatActivity {
    @Nullable
    private PlayerClient mPlayerClient;

    public void setPlayerClient(@Nullable PlayerClient playerClient) {
        mPlayerClient = playerClient;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mPlayerClient != null && !mPlayerClient.isConnected()) {
            mPlayerClient.connect();
        }
    }
}
