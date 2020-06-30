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
package io.omam.peel.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.omam.peel.jfx.Fader;
import io.omam.peel.jfx.Jfx;
import io.omam.peel.tracks.Track;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class PlayerView {

    private static final String CAST_ICON = "cast-24px";

    private static final String VOLUME_ICON = "volume_up-24px";

    private static final String STOP_ICON = "stop-24px";

    private static final String SKIP_NEXT_ICON = "skip_next-24px";

    private static final String PLAY_ICON = "play_circle_outline-24px";

    private static final String SKIP_PREVIOUS_ICON = "skip_previous-24px";

    private static final String PAUSE_ICON = "pause_circle_outline-24px";

    private static final String CAST_CONNECTED_ICON = "cast_connected-24px";

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    private final ActionHandler ah;

    final VBox pane;

    private final VBox queue;

    private final ContextMenu devices;

    private final Button connection;

    private final CurrentTrackView currentTrack;

    private final Button prev;

    private final Button playPause;

    private final Button next;

    private final Button stop;

    private final Button volume;

    private final Fader requesting;

    PlayerView(final ActionHandler anActionHandler) {
        ah = anActionHandler;
        pane = new VBox();
        pane.getStyleClass().add("peel-player");

        currentTrack = new CurrentTrackView();
        currentTrack.setAlignment(Pos.CENTER);
        pane.getChildren().add(currentTrack);

        final HBox controls = new HBox();
        controls.setAlignment(Pos.CENTER);
        controls.getStyleClass().add("peel-player-controls");

        connection = Jfx.button(CAST_ICON, "peel-player-connection");

        devices = new ContextMenu();
        devices.getStyleClass().add("peel-player-devices");
        final MenuItem title = new MenuItem("Cast Devices");
        title.setDisable(true);
        title.getStyleClass().add("peel-player-devices-title");
        devices.getItems().add(title);

        connection.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                devices.show(connection, Side.BOTTOM, 0, 0);
            } else {
                clearError();
            }
        });

        controls.getChildren().add(connection);

        prev = Jfx.button(SKIP_PREVIOUS_ICON, "peel-player-previous");
        prev.setOnAction(e -> ah.prev());
        controls.getChildren().add(prev);

        playPause = Jfx.button(PLAY_ICON, "peel-player-playpause");
        playPause.setOnAction(e -> ah.togglePlayback());
        controls.getChildren().add(playPause);

        next = Jfx.button(SKIP_NEXT_ICON, "peel-player-next");
        next.setOnAction(e -> ah.next());
        controls.getChildren().add(next);

        stop = Jfx.button(STOP_ICON, "peel-player-stop");
        stop.setOnAction(e -> ah.stopPlayback());
        controls.getChildren().add(stop);

        volume = Jfx.button(VOLUME_ICON, "peel-player-volume");
        controls.getChildren().add(volume);

        disableControls();

        pane.getChildren().add(controls);

        queue = new VBox();
        queue.getStyleClass().add("peel-player-queue");

        final ScrollPane scrollPane = new ScrollPane();
        scrollPane.getStyleClass().add("peel-player-scroll");
        scrollPane.setContent(queue);
        scrollPane.setFitToWidth(true);

        pane.getChildren().add(scrollPane);

        requesting = new Fader(connection);
    }

    final void addDevice(final String deviceId, final Optional<String> deviceName) {
        Platform.runLater(() -> {
            final CheckMenuItem mi = new CheckMenuItem(deviceName.orElse(deviceId));
            mi.getStyleClass().add("peel-player-devices-device");
            mi.setUserData(deviceId);
            mi.setOnAction(e -> {
                if (mi.isSelected()) {
                    ah.requestConnection(deviceId);
                } else {
                    ah.requestDisconnection(deviceId);
                }
            });
            devices.getItems().add(mi);
        });
    }

    final void clearQueue() {
        Platform.runLater(() -> resetPlayback());
    }

    final void deviceConnected(final String deviceId) {
        Platform.runLater(() -> {
            clearError();
            devices
                .getItems()
                .stream()
                .filter(mi -> deviceId.equals(mi.getUserData()))
                .findFirst()
                .ifPresent(mi -> ((CheckMenuItem) mi).setSelected(true));
            connection.setGraphic(Jfx.icon(CAST_CONNECTED_ICON));
        });
    }

    final void deviceDisconnected() {
        Platform.runLater(() -> {
            clearError();
            resetPlayback();
            connection.setGraphic(Jfx.icon(CAST_ICON));
        });
    }

    final void deviceDisconnected(final String deviceId, final String error) {
        Platform.runLater(() -> {
            resetPlayback();
            devices
                .getItems()
                .stream()
                .filter(mi -> deviceId.equals(mi.getUserData()))
                .forEach(mi -> ((CheckMenuItem) mi).setSelected(false));
            connection.pseudoClassStateChanged(ERROR, true);
            connection.setTooltip(new Tooltip(error));
        });
    }

    final void resetCurrentTrack() {
        Platform.runLater(() -> {
            requesting.stop();
            playPause.setGraphic(Jfx.icon(PLAY_ICON));
            currentTrack.reset();
            queue.getChildren().clear();
        });
    }

    final void setCurrentTrack(final Track track) {
        Platform.runLater(() -> {
            requesting.stop();
            playPause.setGraphic(Jfx.icon(PAUSE_ICON));
            currentTrack.set(track);
            updatePast(track);
        });
    }

    final void setError(final String error) {
        Platform.runLater(() -> {
            requesting.stop();
            connection.pseudoClassStateChanged(ERROR, true);
            connection.setTooltip(new Tooltip(error));
        });
    }

    final void setPause() {
        Platform.runLater(() -> {
            requesting.stop();
            playPause.setGraphic(Jfx.icon(PLAY_ICON));
        });
    }

    final void setPlay() {
        Platform.runLater(() -> {
            requesting.stop();
            playPause.setGraphic(Jfx.icon(PAUSE_ICON));
        });
    }

    final void setQueue(final List<Track> tracks, final Optional<Track> current, final boolean unsynch) {
        Platform.runLater(() -> {
            requesting.stop();
            enableControls();
            queue.getChildren().clear();
            final Collection<QueueTrackView> views = new ArrayList<>();
            for (int i = 0; i < tracks.size(); i++) {
                views.add(new QueueTrackView(tracks.get(i), i, ah));
            }
            queue.getChildren().addAll(views);
            current.ifPresent(this::updatePast);
            if (unsynch) {
                connection.pseudoClassStateChanged(ERROR, true);
                connection.setTooltip(new Tooltip("queue not synchronised with device"));
            }
        });
    }

    final void waiting() {
        Platform.runLater(() -> requesting.start());
    }

    private void clearError() {
        requesting.stop();
        connection.setTooltip(null);
        connection.pseudoClassStateChanged(ERROR, false);
    }

    private void disableControls() {
        prev.setDisable(true);
        playPause.setDisable(true);
        next.setDisable(true);
        stop.setDisable(true);
        volume.setDisable(true);
    }

    private void enableControls() {
        prev.setDisable(false);
        playPause.setDisable(false);
        next.setDisable(false);
        stop.setDisable(false);
        volume.setDisable(false);
    }

    private void resetPlayback() {
        requesting.stop();
        playPause.setGraphic(Jfx.icon(PLAY_ICON));
        disableControls();
        currentTrack.reset();
        queue.getChildren().clear();
    }

    private void updatePast(final Track current) {
        boolean afterCurrent = false;
        for (final Node child : queue.getChildren()) {
            final QueueTrackView qt = (QueueTrackView) child;
            afterCurrent = afterCurrent || qt.track() == current;
            if (afterCurrent) {
                qt.unsetPast();
            } else {
                qt.setPast();
            }
        }
    }

}
