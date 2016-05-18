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
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.discoos.p2p.loader.LogItemLoader;
import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PUtils;
import org.discoos.p2p.P2PUtils.LogItem;
import org.discoos.p2p.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;

/**
 * An activity representing a list of system log entries. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link PeerDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class LogListActivity extends BaseActivity {

    private String mQuery = "";
    private FloatingActionButton mFab;
    private List<LogItem> mLog;
    private LoaderCallbacks<List<LogItem>> mLoaderCallbacks;

    private LogItemRecyclerViewAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_log_list);

        onCreateToolbar(R.id.log_toolbar, true);

        mFab = onCreateFloatingAction();

        View recyclerView = findViewById(R.id.log_list);
        assert recyclerView != null : "RecyclerView 'R.id.log_list' not found";
        mAdapter = onSetupRecyclerView((RecyclerView) recyclerView);

        mLoaderCallbacks = createLoaderCallbacks();
        getSupportLoaderManager().initLoader(P2P.LOADER_SYSTEM_LOG_ID, null, mLoaderCallbacks);

    }

    /**
     * Create loader callbacks
     * @return LoaderCallbacks implementation for LogListActivity
     */
    private LoaderCallbacks<List<LogItem>> createLoaderCallbacks() {
        return new LoaderCallbacks<List<LogItem>>() {
            @Override
            public LogItemLoader onCreateLoader(int id, Bundle args) {
                return new LogItemLoader(mQuery);
            }

            @Override
            public void onLoadFinished(Loader<List<LogItem>> loader, List<LogItem> data) {
                setLog(data);
            }

            @Override
            public void onLoaderReset(Loader<List<LogItem>> loader) {
                List<LogItem> data = Collections.emptyList();
                setLog(data);
            }

            private void setLog(List<LogItem> data) {
                mLog = data;
                Collections.reverse(mLog);
                mAdapter.notifyDataSetChanged();
                String msg = String.format("Loaded %s log items", mLog.size());
                Snackbar.make(mFab, msg, Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }

        };
    }

    private FloatingActionButton onCreateFloatingAction() {
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queryLogs(mQuery);
            }
        });
        return fab;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate log list menus
        getMenuInflater().inflate(R.menu.menu_log_list, menu);
        getMenuInflater().inflate(R.menu.menu_search, menu);

        final SearchView searchView = onCreateSearchActionView(menu);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                queryLogs(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {

                if (s.isEmpty() && !mQuery.isEmpty()) {
                    queryLogs(s);
                }

                return false;
            }

        });

        // Add items from main menu
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_view_peers:
                intent = new Intent(this, PeerListActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            case R.id.menu_send_log:
                File file= P2PUtils.writeLog(P2P.getFilesDir().getAbsolutePath(), "p2papp.txt");
                intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_SUBJECT, "P2PApp system log");
                intent.putExtra(Intent.EXTRA_TEXT, "Please review attached system log");
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                intent.setType("text/plain");
                startActivity(Intent.createChooser(intent, "Select method"));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void queryLogs(String query) {
        mQuery = query;
        getSupportLoaderManager().restartLoader(P2P.LOADER_SYSTEM_LOG_ID, null, mLoaderCallbacks);
    }

    private LogItemRecyclerViewAdapter onSetupRecyclerView(@NonNull RecyclerView recyclerView) {
        mLog = P2PUtils.readLog();
        LogItemRecyclerViewAdapter adapter = new LogItemRecyclerViewAdapter();
        recyclerView.setAdapter(adapter);
        // Improves performance. Only use if you know that changes
        // in content do not change the layout size of the RecyclerView
        recyclerView.setHasFixedSize(true);
        return adapter;
    }

    public class LogItemRecyclerViewAdapter
            extends RecyclerView.Adapter<LogItemRecyclerViewAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.log_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mItem = mLog.get(position);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            holder.mTimestamp.setText(sdf.format(holder.mItem.timestamp));
            holder.mThread.setText(holder.mItem.thread);
            holder.mModule.setText(holder.mItem.module);
            holder.mLevel.setText(holder.mItem.level);
            holder.mMessage.setText(holder.mItem.message);
        }

        @Override
        public int getItemCount() {
            return mLog.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final View mView;
            public final TextView mTimestamp;
            public final TextView mThread;
            public final TextView mModule;
            public final TextView mLevel;
            public final TextView mMessage;
            public LogItem mItem;

            public ViewHolder(View view) {
                super(view);
                mView = view;
                mTimestamp = (TextView) view.findViewById(R.id.timestamp);
                mThread = (TextView) view.findViewById(R.id.thread);
                mModule = (TextView) view.findViewById(R.id.module);
                mLevel = (TextView) view.findViewById(R.id.level);
                mMessage = (TextView) view.findViewById(R.id.message);
            }
        }
    }

}
