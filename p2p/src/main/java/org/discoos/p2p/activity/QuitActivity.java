package org.discoos.p2p.activity;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PApplication;

public class QuitActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        P2P.getApplication().quit();
        finish();
    }
}
