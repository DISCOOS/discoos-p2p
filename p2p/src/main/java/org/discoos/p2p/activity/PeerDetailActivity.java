/*
 * Copyright DISCO Open Source. All rights reserved
 *
 *    Redistribution and use in source and binary forms, with or without
 *    modification, are permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this
 *       list of conditions and the following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice,
 *       this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *    ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *    WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *    DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *    ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *    (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *    LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *    ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *    (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *    The views and conclusions contained in the software and documentation are those
 *    of the authors and should not be interpreted as representing official policies,
 *    either expressed or implied, of DISCO Open Source.
 */
package org.discoos.p2p.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.view.Menu;
import android.view.View;
import android.view.MenuItem;

import org.discoos.p2p.P2P;
import org.discoos.p2p.PeerInfo;
import org.discoos.p2p.R;
import org.discoos.signal.Observer;

/**
 * An activity representing a single Peer detail screen. This
 * activity is only used narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link PeerListActivity}.
 */
public final class PeerDetailActivity extends BaseActivity {

    private String mId;

    private PeerDetailFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peer_detail);

        onCreateToolbar(R.id.peer_detail_toolbar, true);

        mId = getIntent().getStringExtra(PeerDetailFragment.ARG_PEER_ID);

        onCreateFloatingAction();

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
            mFragment = createFragment(false);
        }

        setupP2PNetwork();

    }

    private void onCreateFloatingAction() {
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                P2P.getContext().ping(mId);
                String msg = String.format("Pinged %s", mId);
                Snackbar.make(view, msg, Snackbar.LENGTH_LONG).show();

            }
        });
    }

    private PeerDetailFragment createFragment(boolean replace) {
        // Create the detail fragment and add it to the activity
        // using a fragment transaction.
        Bundle arguments = new Bundle();
        arguments.putString(PeerDetailFragment.ARG_PEER_ID,
                getIntent().getStringExtra(PeerDetailFragment.ARG_PEER_ID));
        PeerDetailFragment fragment = new PeerDetailFragment();
        fragment.setArguments(arguments);
        if(replace) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.peer_detail_container, fragment)
                    .commitAllowingStateLoss();
        } else {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.peer_detail_container, fragment)
                    .commitAllowingStateLoss();
        }
        return fragment;
    }

    private void setupP2PNetwork() {
        /**
         * Ensure P2P proximity network exists
         */
        ensureNetwork();

        /**
         * Close this activity on signal QUIT
         */
        addObserver(new Observer() {
            @Override
            public void handle(Object signal, Object observable) {
                PeerInfo info;
                switch ((int) signal) {
                    case P2P.ALIVE:
                    case P2P.TIMEOUT:
                        info = ((PeerInfo) observable);
                        if (info.getId().equals(mId)) {
                            createFragment(true);
                        }
                        break;
                    case P2P.QUIT:
                        // Close this activity
                        finish();
                        break;
                }
            }
        });
    }

    // Menu icons are inflated just as they were with actionbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate peer details menus
        getMenuInflater().inflate(R.menu.menu_peer_list, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        // Handle item selection
        switch (item.getItemId()) {
            case android.R.id.home:
                intent = new Intent(this, PeerListActivity.class);
                navigateUpTo(intent);
                return true;
            case R.id.menu_view_logs:
                intent = new Intent(this, LogListActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


}
