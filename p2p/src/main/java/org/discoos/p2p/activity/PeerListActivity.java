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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.discoos.p2p.P2P;
import org.discoos.p2p.PeerInfo;
import org.discoos.p2p.R;
import org.discoos.p2p.internal.P2PContext;
import org.discoos.signal.Event;
import org.discoos.signal.Observer;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * An activity representing a list of Peers. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link PeerDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class PeerListActivity extends BaseActivity {

    public static final String ARG_NETWORK_NAME = "network.name";

    private String mNetwork;

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    /**
     * List of known peers
     */
    private List<? extends PeerInfo> mPeerList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_peer_list);

        mNetwork = getIntent().getStringExtra(PeerListActivity.ARG_NETWORK_NAME);

        fetchPeerList();

        String title = getTitle().toString();
        if(mNetwork != null && P2P.getNetworkNames().contains(mNetwork)) {
            title = P2P.getNetwork(mNetwork).getLabel() + " " + title;
        }
        onCreateToolbar(R.id.peer_list_toolbar, title, true);

        onCreateFloatingAction();

        View recyclerView = findViewById(R.id.peer_list);
        assert recyclerView != null : "RecyclerView 'R.id.peer_list' not found";
        PeerInfoRecyclerViewAdapter adapter = onSetupRecyclerView((RecyclerView) recyclerView);

        if (findViewById(R.id.peer_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }

        onCreateP2PObserver(adapter);

    }

    // Menu icons are inflated just as they were with actionbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate peer list menus
        getMenuInflater().inflate(R.menu.menu_peer_list, menu);
        getMenuInflater().inflate(R.menu.menu_search, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_view_logs:
                intent = new Intent(this, LogListActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onCreateFloatingAction() {
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int peers = 0;
                P2PContext cxt = P2P.getContext();
                for (String id : cxt.getPeerIds()) {
                    if (cxt.ping(id)) {
                        peers++;
                    }
                }
                String msg = String.format("Pinged %s peers", peers);
                Snackbar.make(view, msg, Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
        });
    }

    private void onCreateP2PObserver(final PeerInfoRecyclerViewAdapter adapter) {
        /**
         * Ensure P2P proximity network exists
         */
        ensureNetwork();

        /**
         * Register handles
         */
        addObserver(P2P.CHANGED, new Observer() {
            @Override
            public void handle(Object signal, Object observable) {
                if(P2P.isEvent(observable) && P2P.isPeerChange((Event)observable)) {
                    fetchPeerList();
                    /** Inform list view that data has changed*/
                    adapter.notifyDataSetChanged();
                }
            }
        });
    }

    private void fetchPeerList() {
        // Fetch all peers from all networks
        if(mNetwork == null || !P2P.getNetworkNames().contains(mNetwork) ) {
            mPeerList = P2P.getPeerList();
        } else {
            mPeerList = P2P.getNetwork(mNetwork).getPeerList();
        }
    }


    private PeerInfoRecyclerViewAdapter onSetupRecyclerView(@NonNull RecyclerView recyclerView) {
        PeerInfoRecyclerViewAdapter adapter = new PeerInfoRecyclerViewAdapter();
        recyclerView.setAdapter(adapter);
        // Improves performance. Only use if you know that changes
        // in content do not change the layout size of the RecyclerView
        recyclerView.setHasFixedSize(true);
        return adapter;
    }

    public class PeerInfoRecyclerViewAdapter
            extends RecyclerView.Adapter<PeerInfoRecyclerViewAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.peer_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mItem = mPeerList.get(position);
            SimpleDateFormat sdf = new SimpleDateFormat("dd-HH:mm:ss");
            holder.mActiveView.setText(sdf.format(holder.mItem.getTimestamp()));
            holder.mTimeoutView.setText((holder.mItem.isTimeout() ?  getResources().getString(R.string.timeout) : ""));
            holder.mSummaryView.setText(holder.mItem.getSummary());

            holder.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mTwoPane) {
                        Bundle arguments = new Bundle();
                        arguments.putString(PeerDetailFragment.ARG_PEER_ID, holder.mItem.getId());
                        PeerDetailFragment fragment = new PeerDetailFragment();
                        fragment.setArguments(arguments);
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.peer_detail_container, fragment)
                                .commit();
                    } else {
                        Context context = v.getContext();
                        Intent intent = new Intent(context, PeerDetailActivity.class);
                        intent.putExtra(PeerDetailFragment.ARG_PEER_ID, holder.mItem.getId());
                        context.startActivity(intent);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mPeerList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final View mView;
            public final TextView mActiveView;
            public final TextView mTimeoutView;
            public final TextView mSummaryView;
            public PeerInfo mItem;

            public ViewHolder(View view) {
                super(view);
                mView = view;
                mActiveView = (TextView) view.findViewById(R.id.active);
                mTimeoutView = (TextView) view.findViewById(R.id.timeout);
                mSummaryView = (TextView) view.findViewById(R.id.summary);
            }
        }
    }

}
