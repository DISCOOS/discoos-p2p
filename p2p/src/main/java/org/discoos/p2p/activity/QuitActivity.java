package org.discoos.p2p.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import org.discoos.p2p.P2P;

public class QuitActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        P2P.getContext().quit();
        finish();
    }
}
