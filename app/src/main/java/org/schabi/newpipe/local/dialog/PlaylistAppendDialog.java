package org.schabi.newpipe.local.dialog;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.LocalItemListAdapter;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;

import java.util.HashMap;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public final class PlaylistAppendDialog extends PlaylistDialog {
    private static final String TAG = PlaylistAppendDialog.class.getCanonicalName();

    private RecyclerView playlistRecyclerView;
    private LocalItemListAdapter playlistAdapter;

    private final CompositeDisposable playlistDisposables = new CompositeDisposable();

    /**
     * Create a new instance of {@link PlaylistAppendDialog}.
     *
     * @param streamEntities    a list of {@link StreamEntity} to be added to playlists
     * @return a new instance of {@link PlaylistAppendDialog}
     */
    public static PlaylistAppendDialog newInstance(final List<StreamEntity> streamEntities) {
        final PlaylistAppendDialog dialog = new PlaylistAppendDialog();
        dialog.setStreamEntities(streamEntities);
        return dialog;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle - Creation
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_playlists, container);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final LocalPlaylistManager playlistManager =
                new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()));

        playlistAdapter = new LocalItemListAdapter(getActivity());
        playlistAdapter.setHasStableIds(true);
        playlistAdapter.setSelectedListener(selectedItem -> {
            final List<StreamEntity> entities = getStreamEntities();
            if (selectedItem instanceof PlaylistMetadataEntry && entities != null) {
                onPlaylistSelected(playlistManager, (PlaylistMetadataEntry) selectedItem, entities);
            }
        });

        playlistRecyclerView = view.findViewById(R.id.playlist_list);
        playlistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        playlistRecyclerView.setAdapter(playlistAdapter);

        final View newPlaylistButton = view.findViewById(R.id.newPlaylist);
        newPlaylistButton.setOnClickListener(ignored -> openCreatePlaylistDialog());

        playlistDisposables.add(playlistManager.getPlaylists()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onPlaylistsReceived));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle - Destruction
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        playlistDisposables.dispose();
        if (playlistAdapter != null) {
            playlistAdapter.unsetSelectedListener();
        }

        playlistDisposables.clear();
        playlistRecyclerView = null;
        playlistAdapter = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    //////////////////////////////////////////////////////////////////////////*/

    /** Display create playlist dialog. */
    public void openCreatePlaylistDialog() {
        if (getStreamEntities() == null || !isAdded()) {
            return;
        }

        final PlaylistCreationDialog playlistCreationDialog =
                PlaylistCreationDialog.newInstance(getStreamEntities());
        // Move the dismissListener to the new dialog.
        playlistCreationDialog.setOnDismissListener(this.getOnDismissListener());
        this.setOnDismissListener(null);

        playlistCreationDialog.show(getParentFragmentManager(), TAG);
        requireDialog().dismiss();
    }

    private void onPlaylistsReceived(@NonNull final List<PlaylistMetadataEntry> playlists) {
        if (playlistAdapter != null && playlistRecyclerView != null) {
            playlistAdapter.clearStreamItemList();
            playlistAdapter.addItems(playlists);
            playlistRecyclerView.setVisibility(View.VISIBLE);

            final LocalPlaylistManager playlistManager =
                    new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()));
            final List<Long> duplicateIds = playlistManager.getDuplicatePlaylist(getStreamEntities()
                    .get(0).getUrl()).blockingFirst();

            final HashMap<Integer, Long> map = new HashMap<>();
            for (int i = 0; i < playlists.size(); i++) {
                map.put(i, playlists.get(i).uid);
            }

            playlistRecyclerView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (playlistRecyclerView.getAdapter() == null) {
                        return;
                    }
                    final int count = playlistRecyclerView.getAdapter().getItemCount();
                    System.out.println(" kasjdflkalk" + playlistRecyclerView.getAdapter()
                            .getItemId(0));
                    for (int i = 0; i < count; i++) {
                        if (playlistRecyclerView.findViewHolderForAdapterPosition(i) != null
                                && duplicateIds.contains(playlistAdapter.getItemId(i))) {
                            playlistRecyclerView.findViewHolderForAdapterPosition(i).itemView
                                    .findViewById(R.id.checkmark2).setVisibility(View.VISIBLE);
                        }
                    }
                }
            }, 1000);
        }
    }

    private void onPlaylistSelected(@NonNull final LocalPlaylistManager manager,
                                    @NonNull final PlaylistMetadataEntry playlist,
                                    @NonNull final List<StreamEntity> streams) {

        final int numOfDuplicates = manager.getPlaylistDuplicates(playlist.uid,
                        streams.get(0).getUrl()).blockingFirst();
        if (numOfDuplicates > 0) {
            createDuplicateDialog(numOfDuplicates, manager, playlist, streams);
        } else {
            addStreamToPlaylist(manager, playlist, streams);
        }
    }

    private void addStreamToPlaylist(@NonNull final LocalPlaylistManager manager,
                                     @NonNull final PlaylistMetadataEntry playlist,
                                     @NonNull final List<StreamEntity> streams) {
        final Toast successToast = Toast.makeText(getContext(),
                R.string.playlist_add_stream_success, Toast.LENGTH_SHORT);

        if (playlist.thumbnailUrl
                .equals("drawable://" + R.drawable.placeholder_thumbnail_playlist)) {
            playlistDisposables.add(manager
                    .changePlaylistThumbnail(playlist.uid, streams.get(0).getThumbnailUrl(), false)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(ignored -> successToast.show()));
        }

        playlistDisposables.add(manager.appendToPlaylist(playlist.uid, streams)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> successToast.show()));
        requireDialog().dismiss();
    }

    private void createDuplicateDialog(final int duplicates,
                                       @NonNull final LocalPlaylistManager manager,
                                       @NonNull final PlaylistMetadataEntry playlist,
                                       @NonNull final List<StreamEntity> streams) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());
        builder.setTitle(R.string.duplicate_stream_in_playlist_title);
        builder.setMessage(getString(R.string.duplicate_stream_in_playlist_description,
                duplicates));

        builder.setPositiveButton(android.R.string.yes, (dialog, i) -> {
            addStreamToPlaylist(manager, playlist, streams);
        });
        builder.setNeutralButton(R.string.cancel, null);
        builder.create().show();
    }
}
