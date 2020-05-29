/*
Copyright 2020-2020 Cedric Liegeois

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

    * Neither the name of the copyright holder nor the names of other
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package io.omam.peel.library;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import io.omam.peel.core.PeelThreadFactory;
import io.omam.peel.player.Playback;
import io.omam.peel.tracks.Album;
import io.omam.peel.tracks.Artist;
import io.omam.peel.tracks.SearchListener;
import io.omam.peel.tracks.Tracks;
import javafx.scene.Node;

public final class LibraryController implements SearchHandler {

    private static final class SearchListenerImpl<T> implements SearchListener<T> {

        private final LibraryView view;

        private final boolean allArtists;

        private final Consumer<T> consumer;

        SearchListenerImpl(final LibraryView aView, final boolean isAllArtists, final Consumer<T> anItemConsumer) {
            view = aView;
            allArtists = isAllArtists;
            consumer = anItemConsumer;
        }

        @Override
        public final void found(final T item) {
            consumer.accept(item);
        }

        @Override
        public final void searchOver() {
            view.searchOver();
        }

        @Override
        public final void searchStarted() {
            view.searchStarted(allArtists);
        }
    }

    private final Path libraryRoot;

    private final Set<String> supportedFormats;

    private final ExecutorService executor;

    private final LibraryView view;

    private Future<?> pendingSearch;

    public LibraryController(final Path aLibraryRoot, final Set<String> someSupportedFormats,
            final Playback player) {
        libraryRoot = aLibraryRoot;
        supportedFormats = someSupportedFormats;
        executor = Executors.newSingleThreadExecutor(new PeelThreadFactory("library"));
        view = new LibraryView(this, player);
        pendingSearch = null;
        searchArtists();
    }

    @Override
    public final void search(final SearchType searchType, final String text) {
        cancelPendingSearch();
        final String lc = text.toLowerCase();
        final SearchListener<Album> l = new SearchListenerImpl<>(view, false, view::addAlbum);
        final Runnable task;
        if (searchType == SearchType.ARTIST) {
            task = Tracks.searchByArtist(libraryRoot, supportedFormats, a -> a.toLowerCase().contains(lc), l);
        } else {
            task = Tracks.searchByAlbum(libraryRoot, supportedFormats, a -> a.toLowerCase().contains(lc), l);
        }
        pendingSearch = executor.submit(task);
    }

    @Override
    public final void searchArtists() {
        cancelPendingSearch();
        final SearchListener<List<Artist>> l = new SearchListenerImpl<>(view, true, view::addArtists);
        final Runnable task = Tracks.searchArtists(libraryRoot, l);
        pendingSearch = executor.submit(task);
    }

    public final void shutdown() {
        executor.shutdownNow();
    }

    public final Node widget() {
        return view.pane;
    }

    private void cancelPendingSearch() {
        if (pendingSearch != null) {
            pendingSearch.cancel(true);
            pendingSearch = null;
        }
    }

}
