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
import java.util.Collection;
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

import io.omam.peel.Tracks.Track;
import io.omam.wire.device.CastDeviceController;
import io.omam.wire.device.CastDeviceControllerListener;
import io.omam.wire.device.CastDeviceStatus;
import io.omam.wire.discovery.CastDeviceBrowser;
import io.omam.wire.discovery.CastDeviceBrowserListener;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

@SuppressWarnings("javadoc")
final class Player {

    static final class Controller
            implements CastDeviceBrowserListener, ActionHandler, Playback, ConnectedDeviceListener {

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
        public final void deviceDiscovered(final CastDeviceController controller) {
            executor.execute(() -> {
                final String deviceId = controller.deviceId();
                controllers.put(deviceId, controller);
                view.addDevice(deviceId, controller.deviceName());
            });
        }

        @Override
        public final void deviceRemoved(final CastDeviceController controller) {
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
            executePlayback(() -> connected.next());
        }

        @Override
        public final void play(final Track track) {
            executePlayback(() -> connected.play(track));
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
            executeQueue(() -> connected.play(tracks));
        }

        @Override
        public final void prev() {
            executePlayback(() -> connected.prev());
        }

        @Override
        public final void queueTracksLast(final List<Track> tracks) {
            executeQueue(() -> connected.appendToQueue(tracks));
        }

        @Override
        public final void queueTracksNext(final List<Track> tracks) {
            executeQueue(() -> connected.playNext(tracks));
        }

        @Override
        public final void queueUpdated(final List<Track> tracks) {
            executor.execute(() -> view.setQueue(tracks));
        }

        @Override
        public final void removeFromQueue(final Track track) {
            executeQueue(() -> connected.removeFromQueue(track));
        }

        @Override
        public final void requestConnection(final String deviceId) {
            executor.execute(() -> {
                view.waiting();
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
                    controller.disconnect();
                    connected = null;
                    view.deviceDisconnected(deviceId, e.getMessage());
                }

            });
        }

        @Override
        public final void requestDisconnection(final String deviceId) {
            executor.execute(() -> {
                view.waiting();
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
            browser = CastDeviceBrowser.start(this);
        }

        final Node widget() {
            return view.pane;
        }

        private void executePlayback(final PlaybackTask task) {
            executor.execute(() -> {
                view.waiting();
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

        private void executeQueue(final QueueTask task) {
            executor.execute(() -> {
                view.waiting();
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

    }

    @FunctionalInterface
    static interface UrlResolver {

        String resolveUrl(final Path localPath);
    }

    private static interface ActionHandler {

        void next();

        void play(final Track track);

        void prev();

        void removeFromQueue(final Track track);

        void requestConnection(final String deviceId);

        void requestDisconnection(final String deviceId);

        void stopPlayback();

        void togglePlayback();

    }

    private static final class ConnectedDeviceController
            implements MediaStatusListener, CastDeviceControllerListener {

        private static final Logger LOGGER = Logger.getLogger(ConnectedDeviceController.class.getName());

        private final CastDeviceController deviceController;

        private final MediaController mediaController;

        private final UrlResolver urlResolver;

        private final ConcurrentLinkedQueue<ConnectedDeviceListener> listeners;

        private final MediaSession mediaSession;

        ConnectedDeviceController(final CastDeviceController aCastDeviceController,
                final MediaController aMediaController, final UrlResolver anUrlResolver) {
            deviceController = aCastDeviceController;
            deviceController.addListener(this);
            mediaController = aMediaController;
            mediaController.addListener(this);
            urlResolver = anUrlResolver;
            listeners = new ConcurrentLinkedQueue<>();
            mediaSession = new MediaSession();
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
            mediaSession.update(newStatus);
            final PlayerState playerState = mediaSession.playerState();
            if (playerState == PlayerState.PAUSED) {
                listeners.forEach(ConnectedDeviceListener::playbackPaused);
            } else if (playerState == PlayerState.PLAYING) {
                final Optional<Track> currentTrack = mediaSession.currentTrack();
                if (currentTrack.isPresent()) {
                    final Track t = currentTrack.get();
                    listeners.forEach(l -> l.newTrackPlaying(t));
                } else {
                    listeners.forEach(l -> l.playbackError("No current track"));
                }
            } else if (playerState == PlayerState.IDLE && newStatus.idleReason().isPresent()) {
                final IdleReason idle = newStatus.idleReason().get();
                if (idle == IdleReason.CANCELLED) {
                    listeners.forEach(ConnectedDeviceListener::playbackStopped);
                } else if (idle == IdleReason.FINISHED) {
                    listeners.forEach(ConnectedDeviceListener::playbackFinished);
                }
            }
        }

        @Override
        public final void remoteConnectionClosed() {
            listeners.forEach(l -> l.connectionClosed(deviceController.deviceId(), "connection closed by device"));
        }

        final void addListener(final ConnectedDeviceListener l) {
            listeners.add(l);
        }

        final List<Track> appendToQueue(final List<Track> tracks)
                throws IOException, TimeoutException, MediaRequestException {
            if (mediaSession.isQueueEmpty()) {
                return play(tracks);
            }
            mediaController.appendToQueue(storeTracks(tracks));
            return queuedTracks();
        }

        final String deviceId() {
            return deviceController.deviceId();
        }

        final String deviceName() {
            return deviceController.deviceName().orElse(deviceController.deviceId());
        }

        final void disconnect() {
            mediaSession.reset();
            try {
                deviceController.stopApp(mediaController);
            } catch (final IOException | TimeoutException e) {
                LOGGER.log(Level.WARNING, e, () -> "Could not stop media application");
            }
            deviceController.disconnect();
        }

        final void next() throws IOException, TimeoutException, MediaRequestException {
            mediaController.next();
        }

        final List<Track> play(final List<Track> tracks)
                throws IOException, TimeoutException, MediaRequestException {
            if (mediaSession.playerState() != PlayerState.IDLE) {
                stopPlayback();
            }
            mediaController.load(storeTracks(tracks));
            return queuedTracks();
        }

        final void play(final Track track) throws IOException, TimeoutException, MediaRequestException {
            mediaController.jump(mediaSession.jumpTo(track));
        }

        final List<Track> playNext(final List<Track> tracks)
                throws IOException, TimeoutException, MediaRequestException {
            if (mediaSession.isQueueEmpty()) {
                return play(tracks);
            }
            mediaController.insertInQueue(mediaSession.nextItemId(), storeTracks(tracks));
            return queuedTracks();
        }

        final void prev() throws IOException, TimeoutException, MediaRequestException {
            mediaController.previous();
        }

        final List<Track> removeFromQueue(final Track track)
                throws IOException, TimeoutException, MediaRequestException {
            mediaController.removeFromQueue(Collections.singletonList(mediaSession.itemId(track)));
            return queuedTracks();
        }

        final void removeListener(final ConnectedDeviceListener l) {
            listeners.remove(l);
        }

        final void stopPlayback() throws IOException, TimeoutException, MediaRequestException {
            /* no media status unsolicited message. */
            mediaController.stop();
            mediaSession.reset();
        }

        final PlayerState togglePlayback() throws IOException, TimeoutException, MediaRequestException {
            /* no media status unsolicited message. */
            final MediaStatus status;
            if (mediaSession.playerState() == PlayerState.PLAYING) {
                status = mediaController.pause();
            } else {
                status = mediaController.play();
            }
            final PlayerState playerState = status.playerState();
            mediaSession.setPlayerState(playerState);
            return playerState;
        }

        private List<Track> queuedTracks() throws IOException, TimeoutException, MediaRequestException {
            final List<QueueItem> items = mediaController.getQueueItems();
            return mediaSession.withQueueItems(items);
        }

        private List<MediaInfo> storeTracks(final List<Track> tracks) {
            final List<MediaInfo> l = new ArrayList<>();
            for (final Track track : tracks) {
                try {
                    final String contentId = urlResolver.resolveUrl(track.path);
                    final MediaInfo media = MediaInfo.fromDataStream(contentId);
                    l.add(media);
                    mediaSession.add(contentId, track);
                } catch (final IOException e) {
                    LOGGER.log(Level.WARNING, e, () -> "Ignoring track " + track.name);
                }
            }
            return l;
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

        private final Label albumArtist;

        CurrentTrackView() {
            getStyleClass().add("peel-player-current");
            trackName = new Label("no track playing");
            trackName.getStyleClass().add("peel-player-current-track-name");
            trackName.setAlignment(Pos.CENTER);

            albumArtist = new Label();
            albumArtist.getStyleClass().add("peel-player-current-album-artist");
            albumArtist.setAlignment(Pos.CENTER);

            reset();

            getChildren().addAll(trackName, albumArtist);
        }

        final void reset() {
            trackName.setText("Idle");
            albumArtist.setText("...");
        }

        final void set(final Track track) {
            trackName.setText(track.name);
            albumArtist.setText(track.album + " by " + track.artist);
        }

    }

    private static final class MediaSession {

        private static class QueueTrack {

            final String contentId;

            final Track track;

            QueueTrack(final String aContentId, final Track aTrack) {
                contentId = aContentId;
                track = aTrack;
            }
        }

        private final List<QueueTrack> tracks;

        private PlayerState playerState;

        private MediaInfo currentMedia;

        private Optional<Integer> currentItemId;

        private List<QueueItem> items;

        MediaSession() {
            tracks = new ArrayList<>();
            playerState = PlayerState.IDLE;
            currentMedia = null;
        }

        final void add(final String contentId, final Track track) {
            tracks.add(new QueueTrack(contentId, track));
        }

        final Optional<Track> currentTrack() {
            return Optional.ofNullable(currentMedia).flatMap(this::track);
        }

        final boolean isQueueEmpty() {
            return tracks.isEmpty();
        }

        final int itemId(final Track track) {
            final Optional<String> optContentId =
                    tracks.stream().filter(t -> t.track == track).map(t -> t.contentId).findFirst();
            if (optContentId.isEmpty()) {
                return -1;
            }
            final String contentId = optContentId.get();
            final Optional<Integer> optId = items
                .stream()
                .filter(i -> i.media().contentId().equals(contentId))
                .map(i -> i.itemId())
                .findFirst();
            if (optId.isEmpty()) {
                return -1;
            }
            return optId.get();
        }

        final int jumpTo(final Track track) {
            if (currentItemId.isEmpty()) {
                return 0;
            }
            final Optional<String> optContentId =
                    tracks.stream().filter(t -> t.track == track).map(t -> t.contentId).findFirst();
            if (optContentId.isEmpty()) {
                return 0;
            }
            final String contentId = optContentId.get();
            final Optional<Integer> optNextId = items
                .stream()
                .filter(i -> i.media().contentId().equals(contentId))
                .map(i -> i.itemId())
                .findFirst();
            if (optNextId.isEmpty()) {
                return 0;
            }
            final int nextId = optNextId.get();
            final int current = currentItemId.get();
            return (int) items
                .stream()
                .dropWhile(i -> i.itemId() != current)
                .takeWhile(i -> i.itemId() != nextId)
                .count();
        }

        final int nextItemId() {
            if (currentItemId.isEmpty()) {
                return -1;
            }
            final int current = currentItemId.get();
            final Optional<QueueItem> next =
                    items.stream().dropWhile(i -> i.itemId() != current).skip(1).findFirst();
            return next.map(QueueItem::itemId).orElse(-1);
        }

        final PlayerState playerState() {
            return playerState;
        }

        final void reset() {
            tracks.clear();
            playerState = PlayerState.IDLE;
            currentMedia = null;
        }

        final void setPlayerState(final PlayerState state) {
            playerState = state;
        }

        final Optional<Track> track(final MediaInfo media) {
            final String contentId = media.contentId();
            return tracks.stream().filter(qt -> qt.contentId.equals(contentId)).map(qt -> qt.track).findFirst();
        }

        final void update(final MediaStatus newStatus) {
            if (newStatus.media().isPresent()) {
                currentMedia = newStatus.media().get();
            }
            currentItemId = newStatus.currentItemId();
            playerState = newStatus.playerState();
        }

        final List<Track> withQueueItems(final List<QueueItem> queueItems) {
            final List<Track> result = new ArrayList<>();
            for (final QueueItem qi : queueItems) {
                track(qi.media()).ifPresent(result::add);
            }
            items = queueItems;
            return result;
        }

    }

    @FunctionalInterface
    private static interface PlaybackTask {

        void run() throws IOException, TimeoutException, MediaRequestException;

    }

    @FunctionalInterface
    private static interface QueueTask {

        List<Track> run() throws IOException, TimeoutException, MediaRequestException;

    }

    private static final class QueueTrackView extends HBox {

        private static final String PLAY_ICON = "play_circle_outline-24px";

        private static final String REMOVE_ICON = "remove_circle_outline-24px";

        private static final PseudoClass PAST = PseudoClass.getPseudoClass("past");

        private static final PseudoClass ODD = PseudoClass.getPseudoClass("odd");

        private final Track track;

        QueueTrackView(final Track aTrack, final boolean odd, final ActionHandler ah) {
            track = aTrack;
            getStyleClass().add("peel-player-queue-track");
            setAlignment(Pos.CENTER_LEFT);
            if (odd) {
                pseudoClassStateChanged(ODD, true);
            }
            final VBox vbox = new VBox();
            final Label trackName = new Label(track.name);
            trackName.getStyleClass().add("peel-player-queue-track-name");
            vbox.getChildren().add(trackName);
            final Label albumArtist = new Label(track.album + " by " + track.artist);
            albumArtist.getStyleClass().add("peel-player-queue-track-album-artist");
            vbox.getChildren().add(albumArtist);

            getChildren().add(vbox);

            Jfx.addSpacing(this);

            final Button play = Jfx.button(PLAY_ICON, "peel-player-queue-track-play");
            play.setOnAction(e -> ah.play(track));
            getChildren().add(play);
            final Button remove = Jfx.button(REMOVE_ICON, "peel-player-queue-track-remove");
            remove.setOnAction(e -> ah.removeFromQueue(track));
            getChildren().add(remove);
        }

        final void setPast() {
            pseudoClassStateChanged(PAST, true);
        }

        final Track track() {
            return track;
        }

        final void unsetPast() {
            pseudoClassStateChanged(PAST, false);
        }

    }

    private static final class View {

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

        private final VBox pane;

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

        View(final ActionHandler anActionHandler) {
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
                /* 2 menu items = title + one device -> connect to the device. */
                if (devices.getItems().size() == 2) {
                    mi.setSelected(true);
                    mi.fire();
                }
            });
        }

        final void clearQueue() {
            Platform.runLater(() -> resetPlayback());
        }

        final void deviceConnected() {
            Platform.runLater(() -> {
                clearError();
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
                queue.getChildren().stream().map(c -> (QueueTrackView) c).forEach(QueueTrackView::unsetPast);
            });
        }

        final void setCurrentTrack(final Track track) {
            Platform.runLater(() -> {
                requesting.stop();
                playPause.setGraphic(Jfx.icon(PAUSE_ICON));
                currentTrack.set(track);
                queue
                    .getChildren()
                    .stream()
                    .map(c -> (QueueTrackView) c)
                    .takeWhile(t -> !t.track().equals(track))
                    .forEach(QueueTrackView::setPast);
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

        final void setQueue(final List<Track> tracks) {
            Platform.runLater(() -> {
                requesting.stop();
                enableControls();
                queue.getChildren().clear();
                final Collection<QueueTrackView> views = new ArrayList<>();
                for (int i = 0; i < tracks.size(); i++) {
                    views.add(new QueueTrackView(tracks.get(i), i % 2 == 0, ah));
                }
                queue.getChildren().addAll(views);
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

    }

    private static final String PLAYBACK_ERROR = "Playback error: ";

    private static String errToString(final Error error) {
        return PLAYBACK_ERROR + error.toString();
    }

}
