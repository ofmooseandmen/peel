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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import io.omam.peel.core.PeelThreadFactory;
import io.omam.peel.server.UrlResolver;
import io.omam.peel.tracks.Track;
import io.omam.wire.device.CastDeviceController;
import io.omam.wire.discovery.CastDeviceBrowser;
import io.omam.wire.discovery.CastDeviceBrowserListener;
import io.omam.wire.media.Error;
import io.omam.wire.media.MediaController;
import io.omam.wire.media.MediaRequestException;
import io.omam.wire.media.MediaStatus.PlayerState;
import javafx.scene.Node;

public final class PlayerController
        implements CastDeviceBrowserListener, ActionHandler, Playback, ConnectedDeviceListener {

    @FunctionalInterface
    private static interface PlaybackTask {

        void run() throws IOException, TimeoutException, MediaRequestException;

    }

    @FunctionalInterface
    private static interface QueueTask {

        QueueState run() throws IOException, TimeoutException, MediaRequestException;

    }

    static final String PLAYBACK_ERROR = "Playback error: ";

    private final UrlResolver urlResolver;

    private CastDeviceBrowser browser;

    private final PlayerView view;

    private final Map<String, CastDeviceController> controllers;

    private final ExecutorService executor;

    private ConnectedDeviceController connected;

    public PlayerController(final UrlResolver anUrlResolver) {
        urlResolver = anUrlResolver;
        view = new PlayerView(this);
        controllers = new HashMap<>();
        executor = Executors.newSingleThreadExecutor(new PeelThreadFactory("player"));
        connected = null;
    }

    private static String errToString(final Error error) {
        return PLAYBACK_ERROR + error.toString();
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
    public final void removeFromQueue(final int trackIndex) {
        executeQueue(() -> connected.removeFromQueue(trackIndex));
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

    public final void shutdown() {
        executor.shutdownNow();
        if (connected != null) {
            connected.disconnect();
        }
        controllers.clear();
        if (browser != null) {
            browser.close();
        }
    }

    public final void start() throws IOException {
        browser = CastDeviceBrowser.start(this);
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

    public final Node widget() {
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
                final QueueState q = task.run();
                view.setQueue(q.tracks, q.currentTrack, q.unsynch);
            } catch (final MediaRequestException e) {
                view.setError(errToString(e.error()));
            } catch (final Exception e) {
                view.setError(PLAYBACK_ERROR + e.getMessage());
            }
        });
    }

}
