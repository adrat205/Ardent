package tk.ardentbot.Commands.Music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.core.entities.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ArdentMusicManager {
    private AudioPlayer player;
    private Instant lastPlayedAt;
    private BlockingQueue<ArdentTrack> queue = new LinkedBlockingQueue<>();

    private ArdentTrack currentlyPlaying;

    public ArdentMusicManager(AudioPlayer player) {
        this.player = player;
    }

    public boolean isTrackCurrentlyPlaying() {
        return currentlyPlaying != null;
    }

    public ArdentTrack getCurrentlyPlaying() {
        return currentlyPlaying;
    }

    public void queue(User user, ArdentTrack track) {
        if (!player.startTrack(track.getTrack(), true)) {
            queue.offer(track);
        }
        else currentlyPlaying = track;
    }

    void nextTrack() {
        ArdentTrack track = queue.poll();
        if (track != null) {
            player.startTrack(track.getTrack(), false);
            currentlyPlaying = track;
        }
        else {
            player.startTrack(null, false);
            currentlyPlaying = null;
        }
    }

    public void addToQueue(ArdentTrack track) {
        this.queue.offer(track);
    }

    public void updateLastPlayed(Instant instant) {
        this.lastPlayedAt = instant;
    }

    public Instant getLastPlayedAt() {
        return lastPlayedAt;
    }

    void resetQueue() {
        this.queue = new LinkedBlockingQueue<>();
    }

    void shuffle() {
        ArrayList<ArdentTrack> tracks = new ArrayList<>();
        queue.forEach((tracks::add));
        Collections.shuffle(tracks);
        queue = new LinkedBlockingQueue<>(tracks);
    }

    void removeFrom(User user) {
        Iterator<ArdentTrack> iterator = queue.iterator();
        while (iterator.hasNext()) {
            ArdentTrack currentTrack = iterator.next();
            if (currentTrack.getAuthor().equalsIgnoreCase(user.getId())) iterator.remove();
        }
    }

    public BlockingQueue<ArdentTrack> getQueue() {
        return queue;
    }
}