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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.omam.peel.jfx.Jfx;
import io.omam.peel.player.Playback;
import io.omam.peel.tracks.Album;
import io.omam.peel.tracks.Track;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class AlbumView extends VBox {

    private static final PseudoClass EXPANDED = PseudoClass.getPseudoClass("expanded");

    private final Playback player;

    private final VBox tracks;

    AlbumView(final Album album, final Playback aPlayer) {
        player = aPlayer;

        getStyleClass().add("peel-library-album");

        // FIXME artistName & albumName don't wrap even with setWrapText...
        final Label artistName = new Label(album.artist());
        artistName.getStyleClass().add("peel-library-album-artist");
        getChildren().add(artistName);

        final Label albumName = new Label(album.name());
        albumName.getStyleClass().add("peel-library-album-name");
        getChildren().add(albumName);

        final HBox controls = new HBox();
        controls.getStyleClass().add("peel-library-album-controls");

        final Button playNow = Jfx.button(Icons.PLAY_NOW_ICON, "peel-library-play-now");
        playNow.setText("now");
        playNow.setOnAction(e -> player.playTracks(trackData()));
        controls.getChildren().add(playNow);

        final Button playNext = Jfx.button(Icons.PLAY_NEXT_ICON, "peel-library-play-next");
        playNext.setText("next");
        playNext.setOnAction(e -> player.queueTracksNext(trackData()));
        controls.getChildren().add(playNext);

        final Button playLast = Jfx.button(Icons.PLAY_LAST_ICON, "peel-library-play-last");
        playLast.setText("last");
        playLast.setOnAction(e -> player.queueTracksLast(trackData()));
        controls.getChildren().add(playLast);

        Jfx.addSpacing(controls);

        final ToggleButton toggle = new ToggleButton();
        toggle.setGraphic(Jfx.icon(Icons.LIST_ICON));
        toggle.getStyleClass().add("peel-library-album-toggle");

        controls.getChildren().add(toggle);

        getChildren().add(controls);

        tracks = new VBox();
        tracks.getStyleClass().add("peel-library-album-tracks");

        album.tracks().stream().map(this::toTrack).forEach(tracks.getChildren()::add);

        toggle.selectedProperty().addListener((obs, ov, nv) -> {
            if (nv) {
                pseudoClassStateChanged(EXPANDED, true);
                toggle.setGraphic(Jfx.icon(Icons.CLEAR_ICON));
                getChildren().addAll(tracks);
            } else {
                pseudoClassStateChanged(EXPANDED, false);
                toggle.setGraphic(Jfx.icon(Icons.LIST_ICON));
                getChildren().removeAll(tracks);
            }
        });

    }

    private HBox toTrack(final Track track) {
        final HBox hbox = new HBox();
        hbox.setUserData(track);
        final Label trackName = new Label(track.name());
        trackName.getStyleClass().add("peel-library-album-track-name");
        hbox.getChildren().add(trackName);

        Jfx.addSpacing(hbox);

        final Button playNow = Jfx.button(Icons.PLAY_NOW_ICON, "peel-library-play-now");
        playNow.setOnAction(e -> player.playTracks(Arrays.asList(track)));
        hbox.getChildren().add(playNow);

        final Button playNext = Jfx.button(Icons.PLAY_NEXT_ICON, "peel-library-play-next");
        playNext.setOnAction(e -> player.queueTracksNext(Arrays.asList(track)));
        hbox.getChildren().add(playNext);

        final Button playLast = Jfx.button(Icons.PLAY_LAST_ICON, "peel-library-play-last");
        playLast.setOnAction(e -> player.queueTracksLast(Arrays.asList(track)));
        hbox.getChildren().add(playLast);

        hbox.getStyleClass().add("peel-library-album-track");
        return hbox;

    }

    private List<Track> trackData() {
        return tracks.getChildren().stream().map(c -> (Track) c.getUserData()).collect(Collectors.toList());
    }

}
