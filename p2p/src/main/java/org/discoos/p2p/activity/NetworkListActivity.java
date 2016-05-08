package org.discoos.p2p.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PNetwork;
import org.discoos.p2p.R;
import org.discoos.signal.Observer;

import java.util.Arrays;

public class NetworkListActivity extends BaseActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_network_list);

        Toolbar toolbar = onCreateToolbar(R.id.network_list_toolbar, false);

        onCreateFloatingAction();

        onCreateDrawer(toolbar);

        View recyclerView = findViewById(R.id.network_list);
        assert recyclerView != null : "RecyclerView 'R.id.network_list' not found";
        NetworkRecyclerViewAdapter adapter = onSetupRecyclerView((RecyclerView) recyclerView);

        onCreateP2PObserver(adapter);

    }

    private void onCreateDrawer(Toolbar toolbar) {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        if (drawer != null) {
            drawer.addDrawerListener(toggle);
        }
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(this);
        }
    }

    private void onCreateFloatingAction() {
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (fab != null) {
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String name = "test";
                    P2PNetwork network = P2P.join(name, 100, name);
                    String msg = null;
                    if (network != null) {
                        msg = String.format("Added network %s", name);
                    } else {
                        msg = String.format("Failed to add network %s", name);

                    }
                    Snackbar.make(view, msg, Snackbar.LENGTH_LONG).show();
                }
            });
        }
    }

    private void onCreateP2PObserver(final NetworkRecyclerViewAdapter adapter) {

        /**
         * Ensure P2P proximity network exists
         */
        ensureNetwork();

        /**
         * Close this activity on signal QUIT
         */
        addObserver(P2P.CHANGED, new Observer() {
            @Override
            public void handle(Object signal, Object observable) {

                /** Inform list view that data has changed*/
                adapter.notifyDataSetChanged();

            }
        });
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate main menus
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        Intent intent;
        boolean display = true;
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.nav_peer_list:
                intent = new Intent(this, PeerListActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                break;
            case R.id.nav_log_list:
                intent = new Intent(this, LogListActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                break;
            default:
                display = false;
                break;
        }

        if(display) {
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            if (drawer != null) {
                drawer.closeDrawer(GravityCompat.START);
            }
        }

        return display;
    }

    private NetworkRecyclerViewAdapter onSetupRecyclerView(@NonNull RecyclerView recyclerView) {
        NetworkRecyclerViewAdapter adapter = new NetworkRecyclerViewAdapter();
        recyclerView.setAdapter(adapter);
        // Improves performance. Only use if you know that changes
        // in content do not change the layout size of the RecyclerView
        recyclerView.setHasFixedSize(true);
        return adapter;
    }

    public class NetworkRecyclerViewAdapter
            extends RecyclerView.Adapter<NetworkRecyclerViewAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.network_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            String name = P2P.getNetworkNames().get(position);
            holder.mItem = P2P.getNetwork(name);
            holder.mLabelView.setText(holder.mItem.getLabel());
            holder.mPeersView.setText(Arrays.toString(holder.mItem.getPeerIds().toArray()));

            holder.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Context context = v.getContext();
                    Intent intent = new Intent(context, PeerListActivity.class);
                    intent.putExtra(PeerListActivity.ARG_NETWORK_NAME, holder.mItem.getName());
                    context.startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return P2P.getNetworkNames().size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final View mView;
            public final TextView mLabelView;
            public final TextView mPeersView;
            public P2PNetwork mItem;

            public ViewHolder(View view) {
                super(view);
                mView = view;
                mLabelView = (TextView) view.findViewById(R.id.network_label);
                mPeersView = (TextView) view.findViewById(R.id.network_peers);
            }
        }
    }
}
