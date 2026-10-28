import Foundation
import AVFoundation
import MediaPlayer
import Combine
import UIKit

@MainActor
final class MusicPlayer: ObservableObject {
    @Published private(set) var current: MusicTrack?
    @Published private(set) var isPlaying = false
    @Published private(set) var elapsed: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var queue: [MusicTrack] = []
    @Published private(set) var queueIndex = 0

    private var player: AVPlayer?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?

    init() {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        setupRemoteCommands()
    }

    deinit {
        if let observer = timeObserver { player?.removeTimeObserver(observer) }
        if let observer = endObserver { NotificationCenter.default.removeObserver(observer) }
    }

    func play(_ tracks: [MusicTrack], index: Int = 0) {
        guard tracks.indices.contains(index) else { return }
        queue = tracks.filter { $0.audioURL != nil }
        guard let selected = queue.firstIndex(where: { $0.id == tracks[index].id }) else { return }
        begin(at: selected)
    }

    private func begin(at index: Int) {
        guard queue.indices.contains(index), let url = queue[index].audioURL else { return }
        if let observer = timeObserver { player?.removeTimeObserver(observer); timeObserver = nil }
        if let observer = endObserver { NotificationCenter.default.removeObserver(observer); endObserver = nil }
        player?.pause()
        queueIndex = index
        current = queue[index]
        elapsed = 0
        duration = 0

        let item = AVPlayerItem(url: url)
        let newPlayer = AVPlayer(playerItem: item)
        player = newPlayer
        timeObserver = newPlayer.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main
        ) { [weak self] time in
            Task { @MainActor [weak self] in
                guard let self else { return }
                let currentTime = time.seconds
                self.elapsed = currentTime.isFinite ? max(0, currentTime) : 0
                let length = self.player?.currentItem?.duration.seconds ?? 0
                self.duration = length.isFinite ? max(0, length) : 0
                self.updateNowPlaying()
            }
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in self?.next() }
        }
        try? AVAudioSession.sharedInstance().setActive(true)
        newPlayer.play()
        isPlaying = true
        updateNowPlaying()
        loadArtwork(for: queue[index])
    }

    func toggle() {
        guard let player else { return }
        if isPlaying { player.pause(); isPlaying = false }
        else { try? AVAudioSession.sharedInstance().setActive(true); player.play(); isPlaying = true }
        updateNowPlaying()
    }

    func resume() {
        guard let player else { return }
        try? AVAudioSession.sharedInstance().setActive(true)
        player.play()
        isPlaying = true
        updateNowPlaying()
    }

    func pause() {
        player?.pause()
        isPlaying = false
        updateNowPlaying()
    }

    func next() {
        if queue.indices.contains(queueIndex + 1) { begin(at: queueIndex + 1) }
        else { pause(); seek(to: 0) }
    }

    func previous() {
        if elapsed > 4 { seek(to: 0) }
        else if queue.indices.contains(queueIndex - 1) { begin(at: queueIndex - 1) }
        else { seek(to: 0) }
    }

    func seek(to seconds: Double) {
        guard seconds.isFinite else { return }
        player?.seek(to: CMTime(seconds: max(0, seconds), preferredTimescale: 600))
        elapsed = max(0, seconds)
        updateNowPlaying()
    }

    private func updateNowPlaying() {
        guard let current else { return }
        var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
        info[MPMediaItemPropertyTitle] = current.title
        info[MPMediaItemPropertyArtist] = current.artist
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsed
        info[MPMediaItemPropertyPlaybackDuration] = duration
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? 1.0 : 0.0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func loadArtwork(for track: MusicTrack) {
        guard let url = track.artworkURL else { return }
        Task { [weak self] in
            guard let (data, _) = try? await URLSession.shared.data(from: url),
                  let image = UIImage(data: data),
                  self?.current?.id == track.id else { return }
            var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        }
    }

    private func setupRemoteCommands() {
        let commands = MPRemoteCommandCenter.shared()
        commands.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.resume() }
            return .success
        }
        commands.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pause() }
            return .success
        }
        commands.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.toggle() }
            return .success
        }
        commands.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.next() }
            return .success
        }
        commands.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.previous() }
            return .success
        }
        commands.changePlaybackPositionCommand.isEnabled = true
        commands.changePlaybackPositionCommand.addTarget { [weak self] event in
            if let event = event as? MPChangePlaybackPositionCommandEvent {
                let position = event.positionTime
                Task { @MainActor in self?.seek(to: position) }
            }
            return .success
        }
    }
}
