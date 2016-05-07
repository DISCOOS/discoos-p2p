package org.discoos.p2p.activity;

import android.app.Activity;
import android.support.design.widget.CollapsingToolbarLayout;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.discoos.p2p.P2P;
import org.discoos.p2p.internal.P2PAboutData;
import org.discoos.p2p.PeerInfo;
import org.discoos.p2p.R;

import java.util.Arrays;

/**
 * A fragment representing a single Peer detail screen.
 * This fragment is either contained in a {@link PeerListActivity}
 * in two-pane mode (on tablets) or a {@link PeerDetailActivity}
 * on handsets.
 */
public final class PeerDetailFragment extends Fragment {

    /**
     * The fragment argument representing the peer ID that this fragment
     * represents.
     */
    public static final String ARG_PEER_ID = "peer_id";

    /**
     * AppBarLayout instance
     */
    private CollapsingToolbarLayout mAppBarLayout;

    /**
     * Peer id
     */
    private String mId;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public PeerDetailFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mId = getArguments().getString(PeerDetailFragment.ARG_PEER_ID);

        if (mId != null) {

            Activity activity = this.getActivity();
            mAppBarLayout = (CollapsingToolbarLayout) activity.findViewById(R.id.toolbar_layout);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.peer_detail, container, false);

        if (mId != null) {

            /* The PeerInfo this fragment is presenting. */
            PeerInfo item = P2P.getPeer(mId);

            if (item != null) {
                if (mAppBarLayout != null) {
                    String title = item.get(P2PAboutData.USER_NAME).toString();
                    if(title == null) {
                        title = item.get(P2PAboutData.MODEL_NUMBER).toString();
                    }
                    mAppBarLayout.setTitle(title);
                }
                String networks = Arrays.toString(item.getNetworks().toArray());
                networks = String.format("Networks: %s", networks);
                ((TextView) rootView.findViewById(R.id.peer_networks)).setText(networks);
                ((TextView) rootView.findViewById(R.id.peer_detail)).setText(item.getDetails());
            }
        }

        return rootView;
    }

}
