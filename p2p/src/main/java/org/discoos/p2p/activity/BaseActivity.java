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

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import org.discoos.p2p.P2P;
import org.discoos.p2p.R;
import org.discoos.signal.Dispatcher;
import org.discoos.signal.Observer;

import java.util.HashSet;
import java.util.Set;

/**
 * Base class for implementing P2PApplication android activities
 */
public class BaseActivity extends AppCompatActivity {

    private Set<Observer> mObservers = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /**
         * Close this activity on signal QUIT
         */
        P2P.getDispatcher().add(P2P.QUIT, new Observer() {
            @Override
            public void handle(Object signal, Object observable) {
                finish();

            }
        });
    }

    /**
     * Ensure P2P proximity network exists
     */
    protected void ensureNetwork() {
        P2P.getApplication().ensure();
    }

    protected void addObserver(Observer observer) {
        mObservers.add(observer);
        P2P.getDispatcher().add(observer);
    }

    protected void addObserver(Object signal, Observer observer) {
        mObservers.add(observer);
        P2P.getDispatcher().add(signal, observer);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Dispatcher dispatcher = P2P.getDispatcher();
        for(Observer it : mObservers) {
            dispatcher.removeAll(it);
        }
    }

    /**
     * Create toolbar from view id
     * @param id View id
     * @param displayHomeUp
     */
    protected Toolbar onCreateToolbar(int id, boolean displayHomeUp) {
        return onCreateToolbar(id, getTitle(), displayHomeUp);
    }

    /**
     * Create toolbar from view id
     * @param id View id
     * @param title Toolbar title
     * @param displayHomeUp
     */
    protected Toolbar onCreateToolbar(int id, CharSequence title, boolean displayHomeUp) {

        /**
         * Add action bar
         */
        Toolbar toolbar = (Toolbar) findViewById(id);
        toolbar.setTitle(title);
        setSupportActionBar(toolbar);

        /**
         * Shows back-arrow left of title whichs starts parent activity
         */
        if(displayHomeUp) {
            // Show the Up button in the action bar.
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }

        }
        return toolbar;

    }

    // Menu icons are inflated just as they were with actionbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return super.onCreateOptionsMenu(menu);
    }

    protected SearchView onCreateSearchActionView(Menu menu) {
        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final MenuItem item = menu.findItem(R.id.menu_item_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(item);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(true);
        return searchView;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        // Handle item selection
        switch (item.getItemId()) {
            case android.R.id.home:
                intent = new Intent(this, NetworkListActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            case R.id.menu_item_settings:
                intent = new Intent(this, SettingsActivity.class);
//                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            case R.id.menu_item_quit:
                P2P.getApplication().quit();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
