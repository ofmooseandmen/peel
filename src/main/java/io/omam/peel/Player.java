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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.omam.peel.Tracks.Track;
import io.omam.wire.CastDeviceBrowser;
import io.omam.wire.CastDeviceBrowserListener;
import io.omam.wire.CastDeviceController;
import io.omam.wire.CastDeviceControllerListener;
import io.omam.wire.CastDeviceStatus;
import io.omam.wire.media.Error;
import io.omam.wire.media.MediaController;
import io.omam.wire.media.MediaInfo;
import io.omam.wire.media.MediaRequestException;
import io.omam.wire.media.MediaStatus;
import io.omam.wire.media.MediaStatus.IdleReason;
import io.omam.wire.media.MediaStatus.PlayerState;
import io.omam.wire.media.MediaStatusListener;
import io.omam.wire.media.QueueItem;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

@SuppressWarnings("javadoc")
final class Player {

    static final class Controller
            implements CastDeviceBrowserListener, ActionHandler, Playback, ConnectedDeviceListener {

        private static final String PLAYBACK_ERROR = "Playback error: ";

        private final UrlResolver urlResolver;

        private CastDeviceBrowser browser;

        private final View view;

        private final Map<String, CastDeviceController> controllers;

        private final ExecutorService executor;

        private ConnectedDeviceController connected;

        Controller(final UrlResolver anUrlResolver) {
            urlResolver = anUrlResolver;
            view = new View(this);
            controllers = new HashMap<>();
            executor = Executors.newSingleThreadExecutor(new PeelThreadFactory("player"));
            connected = null;
        }

        @Override
        public final void connectionClosed(final String deviceId, final String reason) {
            executor.execute(() -> {
                connected = null;
                view.deviceDisconnected(deviceId, reason);
            });
        }

        @Override
        public final void down(final CastDeviceController controller) {
            executor.execute(() -> {
                final String deviceId = controller.deviceId();
                controllers.remove(deviceId);
                if (connected != null && connected.deviceId().equals(deviceId)) {
                    connected.removeListener(this);
                    connected.disconnect();
                    connected = null;
                    view.deviceDisconnected(deviceId, "device not reachable");
                }
            });
        }

        @Override
        public final void newTrackPlaying(final Track track) {
            executor.execute(() -> view.setCurrentTrack(track));
        }

        @Override
        public final void next() {
            // TODO Auto-generated method stub

        }

        @Override
        public final void playbackError(final Error error) {
            final String msg = errToString(error);
            executor.execute(() -> view.setError(msg));
        }

        @Override
        public final void playbackError(final String error) {
            final String msg = PLAYBACK_ERROR + error;
            executor.execute(() -> view.setError(msg));
        }

        @Override
        public final void playbackFinished() {
            executor.execute(() -> view.resetCurrentTrack());
        }

        @Override
        public final void playbackPaused() {
            executor.execute(() -> view.setPause());
        }

        @Override
        public final void playbackStopped() {
            executor.execute(() -> view.clearQueue());
        }

        @Override
        public final void playTracks(final List<Track> tracks) {
            executeLoad(() -> connected.play(tracks));
        }

        @Override
        public final void prev() {
            // TODO Auto-generated method stub

        }

        @Override
        public final void queueTracks(final List<Track> tracks) {
            executeLoad(() -> connected.addToQueue(tracks));
        }

        @Override
        public final void queueUpdated(final List<Track> tracks) {
            executor.execute(() -> view.setQueue(tracks));
        }

        @Override
        public final void requestConnection(final String deviceId) {
            executor.execute(() -> {
                if (connected != null) {
                    view.setError(connected.deviceName() + " already connected");
                    return;
                }
                final CastDeviceController controller = controllers.get(deviceId);
                if (controller == null) {
                    view.deviceDisconnected(deviceId, "Unknown device: " + deviceId);
                    return;
                }

                try {
                    controller.connect();
                    final MediaController mediaController =
                            controller.launchApp(MediaController.APP_ID, MediaController::newInstance);
                    connected = new ConnectedDeviceController(controller, mediaController, urlResolver);
                    connected.addListener(this);
                    view.deviceConnected();
                } catch (final IOException | TimeoutException e) {
                    controller.close();
                    connected = null;
                    view.deviceDisconnected(deviceId, e.getMessage());
                }

            });
        }

        @Override
        public final void requestDisconnection(final String deviceId) {
            executor.execute(() -> {
                if (connected != null) {
                    connected.disconnect();
                    connected = null;
                    view.deviceDisconnected();
                }
            });
        }

        @Override
        public final void stopPlayback() {
            executePlayback(() -> {
                connected.stopPlayback();
                view.clearQueue();
            });
        }

        @Override
        public final void togglePlayback() {
            executePlayback(() -> {
                final PlayerState state = connected.togglePlayback();
                if (state == PlayerState.PAUSED) {
                    view.setPause();
                } else {
                    view.setPlay();
                }
            });

        }

        @Override
        public final void up(final CastDeviceController controller) {
            executor.execute(() -> {
                controllers.put(controller.deviceId(), controller);
                view.addDevice(controller.deviceId(), controller.deviceName());
            });
        }

        final void shutdown() {
            executor.shutdownNow();
            if (connected != null) {
                connected.disconnect();
            }
            controllers.clear();
            if (browser != null) {
                browser.close();
            }
        }

        final void start() throws IOException {
            browser = CastDeviceController.browse(this);
        }

        final Node widget() {
            return view.pane;
        }

        private String errToString(final Error error) {
            final StringBuilder sb = new StringBuilder(PLAYBACK_ERROR);
            sb.append(error.errorType());
            error.errorReason().ifPresent(r -> sb.append(" [" + r + "]"));
            error.detailedErrorCode().ifPresent(c -> sb.append(" (" + c + ")"));
            return sb.toString();
        }

        private void executeLoad(final LoadTask task) {
            executor.execute(() -> {
                if (connected == null) {
                    view.setError("No connected device");
                    return;
                }
                try {
                    final List<Track> tracks = task.run();
                    view.setQueue(tracks);
                } catch (final MediaRequestException e) {
                    view.setError(errToString(e.error()));
                } catch (final Exception e) {
                    view.setError(PLAYBACK_ERROR + e.getMessage());
                }
            });
        }

        private void executePlayback(final PlaybackTask task) {
            executor.execute(() -> {
                if (connected == null) {
                    view.setError("No connected device");
                    return;
                }
                try {
                    task.run();
                } catch (final MediaRequestException e) {
                    view.setError(errToString(e.error()));
                } catch (final Exception e) {
                    view.setError(PLAYBACK_ERROR + e.getMessage());
                }
            });
        }

    }

    @FunctionalInterface
    static interface UrlResolver {

        String resolveUrl(final Path localPath);
    }

    private static interface ActionHandler {

        void next();

        void prev();

        void requestConnection(final String deviceId);

        void requestDisconnection(final String deviceId);

        void stopPlayback();

        void togglePlayback();

    }

    private static class ConnectedDeviceController implements MediaStatusListener, CastDeviceControllerListener {

        private static final Logger LOGGER = Logger.getLogger(ConnectedDeviceController.class.getName());

        private final CastDeviceController deviceController;

        private final MediaController mediaController;

        private final UrlResolver urlResolver;

        private final List<QueueTrack> queue;

        private final ConcurrentLinkedQueue<ConnectedDeviceListener> listeners;

        private PlayerState playerState;

        private MediaInfo currentMedia;

        ConnectedDeviceController(final CastDeviceController aCastDeviceController,
                final MediaController aMediaController, final UrlResolver anUrlResolver) {
            deviceController = aCastDeviceController;
            deviceController.addListener(this);
            mediaController = aMediaController;
            mediaController.addListener(this);
            urlResolver = anUrlResolver;
            listeners = new ConcurrentLinkedQueue<>();
            queue = new ArrayList<>();
            playerState = PlayerState.IDLE;
            currentMedia = null;
        }

        @Override
        public final void connectionDead() {
            listeners.forEach(l -> l.connectionClosed(deviceController.deviceId(), "connection dropped"));
        }

        @Override
        public final void deviceStatusUpdated(final CastDeviceStatus status) {
            // ignore.
        }

        @Override
        public void mediaErrorReceived(final Error error) {
            listeners.forEach(l -> l.playbackError(error));
        }

        @Override
        public final void mediaStatusUpdated(final MediaStatus newStatus) {
            if (newStatus.media().isPresent()) {
                currentMedia = newStatus.media().get();
            }
            final PlayerState newPlayerState = newStatus.playerState();
            if (newPlayerState == PlayerState.PAUSED) {
                listeners.forEach(ConnectedDeviceListener::playbackPaused);
            } else if (newPlayerState == PlayerState.PLAYING) {
                final Optional<Track> currentTrack = Optional.ofNullable(currentMedia).flatMap(this::track);
                currentTrack
                    .ifPresentOrElse(t -> listeners.forEach(l -> l.newTrackPlaying(t)),
                            () -> listeners.forEach(l -> l.playbackError("No current track")));
            } else if (newPlayerState == PlayerState.IDLE && newStatus.idleReason().isPresent()) {
                final IdleReason idle = newStatus.idleReason().get();
                if (idle == IdleReason.CANCELLED) {
                    listeners.forEach(ConnectedDeviceListener::playbackStopped);
                } else if (idle == IdleReason.FINISHED) {
                    listeners.forEach(ConnectedDeviceListener::playbackFinished);
                }
            }
            playerState = newPlayerState;
        }

        @Override
        public final void remoteConnectionClosed() {
            listeners.forEach(l -> l.connectionClosed(deviceController.deviceId(), "connection closed by device"));
        }

        final void addListener(final ConnectedDeviceListener l) {
            listeners.add(l);
        }

        final List<Track> addToQueue(final List<Track> tracks)
                throws IOException, TimeoutException, MediaRequestException {
            if (queue.isEmpty()) {
                return play(tracks);
            }
            mediaController.addToQueue(storeTracks(tracks));
            return queuedTracks();
        }

        final String deviceId() {
            return deviceController.deviceId();
        }

        final String deviceName() {
            return deviceController.deviceName().orElse(deviceController.deviceId());
        }

        final void disconnect() {
            try {
                deviceController.stopApp(mediaController);
            } catch (final IOException | TimeoutException e) {
                LOGGER.log(Level.WARNING, e, () -> "Could not stop media application");
            }
            deviceController.close();
        }

        final List<Track> play(final List<Track> tracks)
                throws IOException, TimeoutException, MediaRequestException {
            queue.clear();
            mediaController.load(storeTracks(tracks));
            return queuedTracks();
        }

        final void removeListener(final ConnectedDeviceListener l) {
            listeners.remove(l);
        }

        final void stopPlayback() throws IOException, TimeoutException, MediaRequestException {
            /* no media status unsolicited message. */
            queue.clear();
            final MediaStatus status = mediaController.stop();
            currentMedia = null;
            playerState = status.playerState();
        }

        final PlayerState togglePlayback() throws IOException, TimeoutException, MediaRequestException {
            /* no media status unsolicited message. */
            final MediaStatus status;
            if (playerState == PlayerState.PLAYING) {
                status = mediaController.pause();
            } else {
                status = mediaController.play();
            }
            playerState = status.playerState();
            return playerState;
        }

        private List<Track> queuedTracks() {
            try {
                final List<QueueItem> items = mediaController.getQueueItems();
                return tracks(items);
            } catch (final Exception e) {
                LOGGER.log(Level.WARNING, e, () -> "Could not get queue items");
                return Collections.emptyList();
            }
        }

        private List<MediaInfo> storeTracks(final List<Track> tracks) {
            final List<MediaInfo> l = new ArrayList<>();
            for (final Track track : tracks) {
                try {
                    final String contentId = urlResolver.resolveUrl(track.path);
                    final MediaInfo media = MediaInfo.fromDataStream(contentId);
                    l.add(media);
                    queue.add(new QueueTrack(contentId, track));
                } catch (final IOException e) {
                    LOGGER.log(Level.WARNING, e, () -> "Ignoring track " + track.name);
                }
            }
            return l;
        }

        private Optional<Track> track(final MediaInfo media) {
            final String contentId = media.contentId();
            return queue.stream().filter(qt -> qt.contentId.equals(contentId)).map(qt -> qt.track).findFirst();
        }

        private List<Track> tracks(final List<QueueItem> queueItems) {
            final List<Track> tracks = new ArrayList<>();
            for (final QueueItem qi : queueItems) {
                track(qi.media()).ifPresent(tracks::add);
            }
            return tracks;
        }

    }

    private static interface ConnectedDeviceListener {

        void connectionClosed(final String deviceId, final String reason);

        void newTrackPlaying(final Track track);

        void playbackError(final Error error);

        void playbackError(final String error);

        void playbackFinished();

        void playbackPaused();

        void playbackStopped();

        void queueUpdated(final List<Track> tracks);

    }

    private static final class CurrentTrackView extends VBox {

        private final Label trackName;

        private final Label artistAlbum;

        CurrentTrackView() {
            getStyleClass().add("peel-player-controls-current");
            trackName = new Label("no track playing");
            trackName.setAlignment(Pos.CENTER);
            artistAlbum = new Label();
            artistAlbum.setAlignment(Pos.CENTER);
            getChildren().add(trackName);
            getChildren().add(artistAlbum);
        }

        final void reset() {
            trackName.setText("no track playing");
            artistAlbum.setText(null);
        }

        final void set(final Track track) {
            trackName.setText(track.name);
            artistAlbum.setText(track.album + " by " + track.artist);
        }

    }

    @FunctionalInterface
    private static interface LoadTask {

        List<Track> run() throws IOException, TimeoutException, MediaRequestException;

    }

    @FunctionalInterface
    private static interface PlaybackTask {

        void run() throws IOException, TimeoutException, MediaRequestException;

    }

    private static class QueueTrack {

        final String contentId;

        final Track track;

        QueueTrack(final String aContentId, final Track aTrack) {
            contentId = aContentId;
            track = aTrack;
        }
    }

    private static final class View {

        private static final String CAST = "cast-black-48dp-30";

        private static final String PREV = "skip_previous-black-48dp-30";

        private static final String NEXT = "skip_next-black-48dp-30";

        private static final String STOP = "stop-black-48dp-30";

        private static final String VOLUME = "volume_up-black-48dp-30";

        private static final String PAUSE = "pause_circle_outline-black-48dp-30";

        private static final String PLAY = "play_circle_outline-black-48dp-30";

        private final ActionHandler ah;

        private final BorderPane pane;

        private final VBox queue;

        private final ContextMenu devices;

        private final Button connection;

        private final CurrentTrackView currentTrack;

        private final Button playPause;

        View(final ActionHandler anActionHandler) {
            ah = anActionHandler;
            pane = new BorderPane();
            pane.getStyleClass().add("peel-player");

            queue = new VBox();
            queue.getStyleClass().add("peel-player-queue");

            pane.setCenter(queue);

            final VBox controls = new VBox();
            controls.setAlignment(Pos.CENTER);
            BorderPane.setAlignment(controls, Pos.CENTER);
            controls.getStyleClass().add("peel-player-controls");

            currentTrack = new CurrentTrackView();
            currentTrack.setAlignment(Pos.CENTER);
            BorderPane.setAlignment(currentTrack, Pos.CENTER);
            controls.getChildren().add(currentTrack);

            final HBox buttons = new HBox();
            buttons.setAlignment(Pos.CENTER);
            buttons.getStyleClass().add("peel-player-controls-buttons");

            connection = new Button(null, Jfx.image(CAST));
            connection.getStyleClass().add("peel-player-connection");

            devices = new ContextMenu();
            devices.getStyleClass().add("peel-player-devices");
            final MenuItem title = new MenuItem("Cast Devices");
            title.setDisable(true);
            title.getStyleClass().add("peel-player-devices-title");
            devices.getItems().add(title);

            connection.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    devices.show(connection, Side.TOP, 0, 0);
                } else {
                    clearError();
                }
            });

            buttons.getChildren().add(connection);

            final Button prev = new Button(null, Jfx.image(PREV));
            buttons.getChildren().add(prev);
            prev.setDisable(true);
            prev.setOnAction(e -> ah.prev());

            playPause = new Button(null, Jfx.image(PLAY));
            buttons.getChildren().add(playPause);
            playPause.setDisable(true);
            playPause.setOnAction(e -> ah.togglePlayback());

            final Button next = new Button(null, Jfx.image(NEXT));
            buttons.getChildren().add(next);
            next.setDisable(true);
            next.setOnAction(e -> ah.next());

            final Button stop = new Button(null, Jfx.image(STOP));
            buttons.getChildren().add(stop);
            stop.setOnAction(e -> ah.stopPlayback());

            final Button volume = new Button(null, Jfx.image(VOLUME));
            buttons.getChildren().add(volume);

            controls.getChildren().add(buttons);

            pane.setBottom(controls);

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
            Platform.runLater(() -> {
                playPause.setGraphic(Jfx.image(PLAY));
                playPause.setDisable(true);
                currentTrack.reset();
                queue.getChildren().clear();
            });
        }

        final void deviceConnected() {
            Platform.runLater(() -> {
                clearError();
                connection.pseudoClassStateChanged(CONNECTED, true);
            });
        }

        final void deviceDisconnected() {
            Platform.runLater(() -> {
                clearError();
                connection.pseudoClassStateChanged(CONNECTED, false);
            });
        }

        final void deviceDisconnected(final String deviceId, final String error) {
            Platform.runLater(() -> {
                devices
                    .getItems()
                    .stream()
                    .filter(mi -> deviceId.equals(mi.getUserData()))
                    .forEach(mi -> ((CheckMenuItem) mi).setSelected(false));
                connection.pseudoClassStateChanged(CONNECTED, false);
                connection.setTooltip(new Tooltip(error));
            });
        }

        final void resetCurrentTrack() {
            Platform.runLater(() -> internalResetCurrentTrack());
        }

        final void setCurrentTrack(final Track track) {
            Platform.runLater(() -> internalSetCurrentTrack(track));
        }

        final void setError(final String error) {
            Platform.runLater(() -> {
                connection.pseudoClassStateChanged(ERROR, true);
                connection.setTooltip(new Tooltip(error));
            });
        }

        final void setPause() {
            Platform.runLater(() -> playPause.setGraphic(Jfx.image(PAUSE)));
        }

        final void setPlay() {
            Platform.runLater(() -> playPause.setGraphic(Jfx.image(PLAY)));
        }

        final void setQueue(final List<Track> tracks) {
            Platform.runLater(() -> {
                playPause.setDisable(false);
                queue.getChildren().clear();
                queue
                    .getChildren()
                    .addAll(tracks.stream().map(t -> new Label(t.name)).collect(Collectors.toList()));
            });
        }

        private void clearError() {
            connection.setTooltip(null);
            connection.pseudoClassStateChanged(ERROR, false);
        }

        private void internalResetCurrentTrack() {
            playPause.setGraphic(Jfx.image(PLAY));
            currentTrack.reset();
        }

        private void internalSetCurrentTrack(final Track track) {
            playPause.setGraphic(Jfx.image(PAUSE));
            currentTrack.set(track);
        }

    }

    private static final PseudoClass CONNECTED = PseudoClass.getPseudoClass("connected");

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

}
