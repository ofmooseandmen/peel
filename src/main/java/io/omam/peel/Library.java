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
package io.omam.peel;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.omam.peel.Tracks.Album;
import io.omam.peel.Tracks.Artist;
import io.omam.peel.Tracks.SearchListener;
import io.omam.peel.Tracks.Track;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.Duration;

@SuppressWarnings("javadoc")
final class Library {

    static final class Controller implements SearchHandler {

        private final Path libraryRoot;

        private final Set<String> supportedFormats;

        private final ExecutorService executor;

        private final View view;

        private Future<?> pendingSearch;

        Controller(final Path aLibraryRoot, final Set<String> someSupportedFormats, final Playback player) {
            libraryRoot = aLibraryRoot;
            supportedFormats = someSupportedFormats;
            executor = Executors.newSingleThreadExecutor(new PeelThreadFactory("library"));
            view = new View(this, player);
            pendingSearch = null;
            searchArtists();
        }

        @Override
        public final void searchArtists() {
            cancelPendingSearch();
            final SearchListener<List<Artist>> l = new SearchListenerImpl<>(view, true, view::addArtists);
            final Runnable task = Tracks.searchArtists(libraryRoot, l);
            pendingSearch = executor.submit(task);
        }

        @Override
        public final void searchBy(final SearchType searchType, final String text) {
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

        final void shutdown() {
            executor.shutdownNow();
        }

        final Node widget() {
            return view.pane;
        }

        private void cancelPendingSearch() {
            if (pendingSearch != null) {
                pendingSearch.cancel(true);
                pendingSearch = null;
            }
        }

    }

    private static final class AlbumView extends VBox {

        private final Playback player;

        private final FlowPane tracks;

        AlbumView(final Album album, final Playback aPlayer, final ToggleGroup toggleGroup) {
            player = aPlayer;

            getStyleClass().add("peel-library-album");
            setMinWidth(250);

            final Label artistName = new Label(album.artist);
            artistName.setMaxWidth(250);
            artistName.setWrapText(true);
            artistName.getStyleClass().add("peel-library-album-artist");
            getChildren().add(artistName);

            final Label albumName = new Label(album.name);
            albumName.setMaxWidth(250);
            albumName.setWrapText(true);
            albumName.getStyleClass().add("peel-library-album-name");
            getChildren().add(albumName);

            final HBox controls = new HBox();
            controls.getStyleClass().add("peel-library-album-controls");

            final Button play = new Button("play");
            play.getStyleClass().add("peel-library-play");
            play.setOnAction(e -> player.playTracks(trackData()));
            controls.getChildren().add(play);

            final Button add = new Button("add");
            add.getStyleClass().add("peel-library-add");
            add.setOnAction(e -> player.queueTracks(trackData()));
            controls.getChildren().add(add);

            Jfx.addSpacing(controls);

            final ToggleButton toggle = new ToggleButton(HAMBURGER);
            toggle.setToggleGroup(toggleGroup);
            toggle.getStyleClass().add("peel-library-album-toggle");

            controls.getChildren().add(toggle);

            Jfx.addSpacing(this);

            getChildren().add(controls);

            tracks = new FlowPane();
            tracks.setPrefWrapLength(0);
            tracks.setOrientation(Orientation.VERTICAL);
            tracks.setHgap(20);
            tracks.setVgap(0);
            tracks.getStyleClass().add("peel-library-album-tracks");

            final Popup popup = new Popup();
            popup.getScene().setRoot(tracks);

            toggle.selectedProperty().addListener((obs, ov, nv) -> {
                if (nv) {
                    pseudoClassStateChanged(EXPANDED, true);
                    toggle.setText(CROSS);
                    final Point2D point = localToScreen(0, 0);
                    popup.show(this, point.getX() + getWidth() + 5, point.getY());
                } else {
                    pseudoClassStateChanged(EXPANDED, false);
                    toggle.setText(HAMBURGER);
                    popup.hide();
                }
            });

            tracks.addEventHandler(MouseEvent.MOUSE_EXITED, e -> toggle.setSelected(false));

            album.tracks.forEach(this::addTrack);

        }

        private void addTrack(final Track track) {
            final HBox pane = new HBox();
            pane.setUserData(track);
            final Node trackName = new Label(track.name);
            trackName.getStyleClass().add("peel-library-album-track-name");
            pane.getChildren().add(trackName);

            Jfx.addSpacing(pane);

            final Button play = new Button("play");
            play.getStyleClass().add("peel-library-play");
            play.setOnAction(e -> player.playTracks(Arrays.asList(track)));
            pane.getChildren().add(play);

            final Button add = new Button("add");
            add.getStyleClass().add("peel-library-add");
            add.setOnAction(e -> player.queueTracks(Arrays.asList(track)));
            pane.getChildren().add(add);

            pane.getStyleClass().add("peel-library-album-track");

            final double wrapLength = Math.min(400.0, tracks.getPrefWrapLength() + 21);
            tracks.setPrefWrapLength(wrapLength);
            tracks.getChildren().add(pane);
        }

        private List<Track> trackData() {
            return tracks.getChildren().stream().map(c -> (Track) c.getUserData()).collect(Collectors.toList());
        }

    }

    private static final class ArtistsView extends VBox {

        private VBox pending;

        private final Map<String, VBox> artists;

        ArtistsView() {
            getStyleClass().add("peel-library-artists");
            artists = new HashMap<>();
            pending = null;
        }

        final void clear() {
            getChildren().clear();
            artists.clear();
            pending = null;
        }

        final void done() {
            if (pending != null) {
                getChildren().add(pending);
            }
            pending = null;
        }

        final void withArtist(final Artist artist, final TextField searchField) {
            final VBox vbox;
            if (!artists.containsKey(artist.firstChar)) {
                vbox = new VBox();
                final Label label = new Label(artist.firstChar);
                label.getStyleClass().add("peel-library-artist-index");
                label.setMaxWidth(Double.MAX_VALUE);
                vbox.getChildren().add(label);
                artists.put(artist.firstChar, vbox);
                if (pending != null) {
                    getChildren().add(pending);
                }
                pending = vbox;
            } else {
                vbox = artists.get(artist.firstChar);
            }
            vbox.getChildren().add(new ArtistView(artist, searchField));
        }
    }

    private static final class ArtistView extends HBox {

        ArtistView(final Artist artist, final TextField searchField) {
            getStyleClass().add("peel-library-artist");

            final Label label = new Label(artist.name);
            label.getStyleClass().add("peel-library-artist-name");

            getChildren().add(label);

            Jfx.addSpacing(this);

            final Label search = new Label(SEARCH);
            search.getStyleClass().addAll("peel-library-artist-search", "peel-library-search-icon");

            getChildren().add(search);

            addEventHandler(MouseEvent.MOUSE_RELEASED, e -> searchField.setText(artist.name));

        }
    }

    private static interface SearchHandler {

        void searchArtists();

        void searchBy(final SearchType searchType, final String text);
    }

    private static final class SearchListenerImpl<T> implements SearchListener<T> {

        private final View view;

        private final boolean allArtists;

        private final Consumer<T> consumer;

        SearchListenerImpl(final View aView, final boolean isAllArtists, final Consumer<T> anItemConsumer) {
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

    private enum SearchType {

        ARTIST,
        ALBUM;

        final String display() {
            return name().toLowerCase() + ":";
        }

        final SearchType toggle() {
            if (this == ARTIST) {
                return ALBUM;
            }
            return ARTIST;
        }
    }

    private static final class View {

        private final Playback player;

        private final BorderPane pane;

        private final HBox search;

        private final Label searchType;

        private final TextField searchField;

        private final Button searchClear;

        private final TilePane albums;

        private final ArtistsView artists;

        private final ScrollPane scrollPane;

        private final ToggleGroup toggleGroup;

        View(final SearchHandler searchHandler, final Playback aPlayer) {
            player = aPlayer;

            pane = new BorderPane();
            pane.getStyleClass().add("peel-library");

            search = new HBox();
            search.getStyleClass().add("peel-library-search-bar");

            final Label icon = new Label(SEARCH);
            icon.getStyleClass().addAll("peel-library-search-bar-icon", "peel-library-search-icon");
            search.getChildren().add(icon);

            searchType = new Label(SearchType.ARTIST.display());
            searchType.setUserData(SearchType.ARTIST);
            searchType.getStyleClass().add("peel-library-search-bar-type");
            searchType.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
                final SearchType newType = ((SearchType) searchType.getUserData()).toggle();
                searchType.setUserData(newType);
                searchType.setText(newType.display());
            });
            search.getChildren().add(searchType);

            searchField = new TextField();
            searchField.getStyleClass().add("peel-library-search-bar-field");
            search.getChildren().add(searchField);

            Jfx.addSpacing(search);

            searchClear = new Button(CROSS);
            searchClear.getStyleClass().add("peel-library-search-bar-clear");

            searchClear.setOnAction(e -> searchField.clear());

            pane.setTop(search);

            albums = new TilePane();
            albums.setHgap(10);
            albums.setVgap(10);
            albums.getStyleClass().add("peel-library-albums");

            artists = new ArtistsView();

            scrollPane = new ScrollPane();
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            scrollPane.getStyleClass().add("peel-library-scroll");
            pane.setCenter(scrollPane);

            final PauseTransition pause = new PauseTransition(Duration.millis(250));
            searchType.textProperty().addListener((obs, ov, nv) -> {
                pause.stop();
                search(searchHandler);
            });

            searchField.textProperty().addListener((obs, ov, nv) -> {
                pause.setOnFinished(e -> search(searchHandler));
                pause.playFromStart();
            });

            toggleGroup = new ToggleGroup();

        }

        final void addAlbum(final Album album) {
            Platform.runLater(() -> {
                albums.getChildren().add(new AlbumView(album, player, toggleGroup));
            });
        }

        final void addArtists(final List<Artist> artistList) {
            Platform.runLater(() -> {
                for (final Artist artist : artistList) {
                    artists.withArtist(artist, searchField);
                }
                artists.done();
            });
        }

        final void searchOver() {
            // TODO icon = search + text if results is empty.
        }

        final void searchStarted(final boolean allArtists) {
            Platform.runLater(() -> {
                albums.getChildren().clear();
                artists.clear();
                final Node value = allArtists ? artists : albums;
                scrollPane.setContent(value);
                // TODO icon = spinner
            });
        }

        private void search(final SearchHandler searchHandler) {
            final String text = searchField.getText();
            if (text.isBlank()) {
                search.getChildren().remove(searchClear);
                searchHandler.searchArtists();
            } else {
                if (!search.getChildren().contains(searchClear)) {
                    search.getChildren().add(searchClear);
                }
                searchHandler.searchBy((SearchType) searchType.getUserData(), text);
            }
        }

    }

    private static final String CROSS = "\u00D7";

    private static final String HAMBURGER = "\u2630";

    private static final String SEARCH = "\u26B2";

    private static final PseudoClass EXPANDED = PseudoClass.getPseudoClass("expanded");

}
