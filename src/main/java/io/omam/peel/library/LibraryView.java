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

import java.util.List;

import io.omam.peel.jfx.Fader;
import io.omam.peel.jfx.Jfx;
import io.omam.peel.player.Playback;
import io.omam.peel.tracks.Album;
import io.omam.peel.tracks.Artist;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

final class LibraryView {

    private final Playback player;

    final BorderPane pane;

    private final HBox search;

    private final Label searchType;

    private final TextField searchField;

    private final Button searchClear;

    private final VBox albums;

    private final ArtistsView artists;

    private final ScrollPane scrollPane;

    private final Fader searching;

    LibraryView(final SearchHandler searchHandler, final Playback aPlayer) {
        player = aPlayer;

        pane = new BorderPane();
        pane.setMaxHeight(Double.MAX_VALUE);
        pane.getStyleClass().add("peel-library");

        search = new HBox();
        search.getStyleClass().add("peel-library-search-bar");

        final Label searchIcon = new Label();
        searchIcon.setGraphic(Jfx.icon(Icons.SEARCH_ICON));
        searchIcon.getStyleClass().addAll("peel-library-search-bar-icon");
        search.getChildren().add(searchIcon);

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

        searchClear = new Button();
        searchClear.setGraphic(Jfx.icon(Icons.CLEAR_ICON));
        searchClear.getStyleClass().add("peel-library-search-bar-clear");

        searchClear.setOnAction(e -> searchField.clear());

        pane.setTop(search);

        albums = new VBox();
        albums.getStyleClass().add("peel-library-albums");

        artists = new ArtistsView();

        scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setVbarPolicy(ScrollBarPolicy.ALWAYS);
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

        searching = new Fader(searchIcon);

    }

    final void addAlbum(final Album album) {
        Platform.runLater(() -> {
            albums.getChildren().add(new AlbumView(album, player));
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
        Platform.runLater(() -> {
            searching.stop();
            // TODO text if results is empty.
        });
    }

    final void searchStarted(final boolean allArtists) {
        Platform.runLater(() -> {
            searching.start();
            albums.getChildren().clear();
            artists.clear();
            final Node value = allArtists ? artists : albums;
            scrollPane.setContent(value);
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
            searchHandler.search((SearchType) searchType.getUserData(), text);
        }
    }

}