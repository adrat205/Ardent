package tk.ardentbot.commands.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.wrapper.spotify.methods.TrackRequest;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.Region;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.exceptions.PermissionException;
import net.dv8tion.jda.core.managers.AudioManager;
import org.apache.commons.lang.WordUtils;
import tk.ardentbot.core.executor.BaseCommand;
import tk.ardentbot.core.executor.Command;
import tk.ardentbot.core.executor.Subcommand;
import tk.ardentbot.core.misc.logging.BotException;
import tk.ardentbot.main.Shard;
import tk.ardentbot.main.ShardManager;
import tk.ardentbot.rethink.models.MusicSettingsModel;
import tk.ardentbot.utils.discord.GuildUtils;
import tk.ardentbot.utils.discord.MessageUtils;
import tk.ardentbot.utils.discord.UserUtils;
import tk.ardentbot.utils.javaAdditions.Pair;
import tk.ardentbot.utils.rpg.EntityGuild;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static tk.ardentbot.main.Ardent.spotifyApi;
import static tk.ardentbot.rethink.Database.connection;
import static tk.ardentbot.rethink.Database.r;

@SuppressWarnings("Duplicates")
public class Music extends Command {
    public Music(CommandSettings commandSettings, Shard shard) {
        super(commandSettings);
    }

    public static Pair<Integer, Integer> getMusicStats() {
        try {
            int playingGuilds = 0;
            int queueLength = 0;
            for (Shard shard : ShardManager.getShards()) {
                for (Guild guild : shard.jda.getGuilds()) {
                    GuildMusicManager guildMusicManager = getGuildAudioPlayer(guild, null, shard);
                    ArdentMusicManager manager = guildMusicManager.scheduler.manager;
                    if (guildMusicManager.player.getPlayingTrack() != null) {
                        playingGuilds++;
                        queueLength++;
                        queueLength += manager.getQueue().size();
                    }
                }
            }
            return new Pair<>(playingGuilds, queueLength);
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    static synchronized GuildMusicManager getGuildAudioPlayer(Guild guild, MessageChannel channel, Shard shard) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = shard.musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(shard.playerManager, channel);
            guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
            shard.musicManagers.put(guildId, musicManager);
        }
        else {
            ArdentMusicManager ardentMusicManager = musicManager.scheduler.manager;
            if (ardentMusicManager.getChannel() == null && channel != null) {
                ardentMusicManager.setChannel(channel);
            }
        }
        return musicManager;
    }

    public static synchronized GuildMusicManager getGuildAudioPlayer(Guild guild, MessageChannel channel) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = GuildUtils.getShard(guild).musicManagers.get(guildId);
        if (musicManager == null) {
            musicManager = new GuildMusicManager(GuildUtils.getShard(guild).playerManager, channel);
            guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
            GuildUtils.getShard(guild).musicManagers.put(guildId, musicManager);
        }
        else {
            ArdentMusicManager ardentMusicManager = musicManager.scheduler.manager;
            if (ardentMusicManager.getChannel() == null) {
                ardentMusicManager.setChannel(channel);
            }
        }
        return musicManager;
    }

    private static void play(User user, Guild guild, VoiceChannel channel, GuildMusicManager musicManager, AudioTrack
            track, TextChannel textChannel) {
        if (guild.getAudioManager().getConnectedChannel() == null) {
            try {
                guild.getAudioManager().openAudioConnection(channel);
            }
            catch (PermissionException e) {
                textChannel.sendMessage("I don't have permission to connect to this channel!").queue();
            }
        }
        musicManager.scheduler.manager.addToQueue(new ArdentTrack(user.getId(), textChannel, track));
    }

    private static boolean shouldContinue(User user, Guild guild, TextChannel channel, int
            numberOfTracks) throws Exception {
        if (guild.getMembers().size() < 150) {
            int trackAmount = getGuildAudioPlayer(guild, channel).scheduler.manager.getQueue().size();
            if (trackAmount + numberOfTracks >= 100) {
                GuildUtils.getShard(guild).help.sendTranslatedMessage("You can't queue more than **100** songs at a time if you're " +
                        "not a patron! Please help us out at pledge at https://patreon.com/ardent for this perk!", channel, user);
                return false;
            }
            else return true;
        }
        else return true;
    }

    private static boolean shouldContinue(User user, Guild guild, TextChannel channel, AudioTrack
            track) throws Exception {
        if (guild.getMembers().size() < 150) {
            long minutesDuration = track.getDuration() / 1000 / 60;
            if (minutesDuration > 15 && getHours(track) > 0) {
                GuildUtils.getShard(guild).help.sendTranslatedMessage("You can't queue songs longer than **15** minutes if you're " +
                        "not a patron! Please help us out at pledge at https://patreon.com/ardent for this perk!", channel, user);
                return false;
            }
            else {
                return shouldContinue(user, guild, channel, 1);
            }
        }
        else return true;
    }

    static void loadAndPlay(Message message, User user, Command command, final TextChannel channel,
                            String trackUrl, final VoiceChannel voiceChannel, boolean search, boolean useEmbedSelect) {
        if (trackUrl.contains("spotify.com")) {
            String[] parsed = trackUrl.split("/track/");
            if (parsed.length == 2) {
                final TrackRequest request = spotifyApi.getTrack(parsed[1]).build();
                try {
                    trackUrl = request.get().getName();
                }
                catch (Exception e) {
                    new BotException(e);
                }
            }
        }
        Guild guild = channel.getGuild();
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild(), channel);
        String finalTrackUrl = trackUrl;
        GuildUtils.getShard(guild).playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                if (!UserUtils.hasTierTwoPermissions(user) && !EntityGuild.get(guild).isPremium()) {
                    try {
                        if (!shouldContinue(user, guild, channel, track)) {
                            return;
                        }
                    }
                    catch (Exception e) {
                        new BotException(e);
                    }
                }
                try {
                    command.sendTranslatedMessage("Adding {0} to the queue".replace("{0}", track.getInfo().title) + " " + getDuration
                            (track), channel, user);
                }
                catch (Exception e) {
                    new BotException(e);
                }
                play(user, guild, voiceChannel, musicManager, track, channel);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                List<AudioTrack> tracks = playlist.getTracks();
                if (playlist.isSearchResult()) {
                    try {
                        if (!useEmbedSelect) {
                            AudioTrack[] possible;
                            if (playlist.getTracks().size() >= 5) possible = playlist.getTracks().subList(0, 5).toArray(new AudioTrack[5]);
                            else possible = playlist.getTracks().toArray(new AudioTrack[playlist.getTracks().size()]);
                            ArrayList<String> names = new ArrayList<>();
                            for (AudioTrack audioTrack : possible) {
                                names.add(audioTrack.getInfo().title);
                            }
                            Message embed = command.sendEmbed(command.chooseFromList("Choose Song", guild, user,
                                    command, names.toArray(new String[5])), channel, user);
                            interactiveOperation(channel, message, selectionMessage -> {
                                try {
                                    AudioTrack selected = possible[Integer.parseInt(selectionMessage.getContent()) - 1];
                                    if (!UserUtils.hasTierTwoPermissions(user) && !EntityGuild.get(guild).isPremium()) {
                                        try {
                                            if (!shouldContinue(user, guild, channel, selected)) {
                                                return;
                                            }
                                        }
                                        catch (Exception e) {
                                            new BotException(e);
                                        }
                                    }
                                    try {
                                        embed.delete().queue();
                                        selectionMessage.delete().queue();
                                    }
                                    catch (Exception ignored) {
                                    }
                                    play(user, guild, voiceChannel, musicManager, selected, channel);
                                    command.sendTranslatedMessage("Adding {0} to the queue".replace("{0}", selected.getInfo().title) + " " +
                                            getDuration(selected), channel, user);
                                }
                                catch (Exception e) {
                                    command.sendTranslatedMessage("Invalid response", channel, user);
                                }
                            });
                        }
                        else {
                            AudioTrack track = playlist.getTracks().get(0);
                            play(user, guild, voiceChannel, musicManager, track, channel);
                            command.sendTranslatedMessage("Adding {0} to the queue".replace("{0}", track.getInfo().title) + " " +
                                    getDuration(track), channel, user);

                        }
                    }
                    catch (Exception e) {
                        new BotException(e);
                    }
                }
                else {
                    if (!UserUtils.hasTierTwoPermissions(user) && !EntityGuild.get(guild).isPremium()) {
                        try {
                            if (!shouldContinue(user, guild, channel, 1)) {
                                return;
                            }
                        }
                        catch (Exception e) {
                            new BotException(e);
                        }
                    }
                    try {
                        command.sendTranslatedMessage("Adding {0} songs to the queue".replace("{0}", String.valueOf(tracks.size())),
                                channel, user);
                    }
                    catch (Exception e) {
                        new BotException(e);
                    }
                    for (AudioTrack track : tracks) {
                        play(user, guild, voiceChannel, musicManager, track, channel);
                    }
                }
            }

            @Override
            public void noMatches() {
                if (!search) {
                    loadAndPlay(message, user, command, channel, "ytsearch: " + finalTrackUrl, voiceChannel, true,
                            useEmbedSelect);
                }
                else {
                    try {
                        command.sendTranslatedMessage("I couldn't find a song with that name", channel, user);
                    }
                    catch (Exception e) {
                        new BotException(e);
                    }
                }
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                try {
                    sendTo(channel, guild).sendMessage("I wasn't able to play that track... **Reason: **" + exception.getLocalizedMessage
                            ()).queue();
                }
                catch (Exception e) {
                    new BotException(e);
                }
            }
        });
    }

    static VoiceChannel joinChannel(Guild guild, Member user, Command command, AudioManager
            audioManager, MessageChannel channel) throws Exception {
        GuildVoiceState voiceState = user.getVoiceState();
        if (voiceState.inVoiceChannel()) {
            VoiceChannel voiceChannel = voiceState.getChannel();
            Member bot = guild.getMember(GuildUtils.getShard(guild).bot);
            if (bot.hasPermission(voiceChannel, Permission.VOICE_CONNECT)) {
                if (guild.getRegion() == Region.SINGAPORE) {
                    channel.sendMessage("Singapore is currently disabled. Blame OVH.").queue();
                    return null;
                }
                try {
                    audioManager.openAudioConnection(voiceChannel);
                    command.sendTranslatedMessage("Connected to channel **{0}**".replace("{0}", voiceChannel.getName()), channel, user
                            .getUser());
                }
                catch (PermissionException e) {
                    command.sendTranslatedMessage("I don't have permission to join " + voiceChannel.getName() + "!", channel, user
                            .getUser());

                }
            }
            else {
                command.sendTranslatedMessage("I don't have permission to join " + voiceChannel.getName() + "!", channel, user.getUser());
            }
            return voiceChannel;
        }
        else {
            command.sendTranslatedMessage("You're not in a voice channel!", channel, user.getUser());
            return null;
        }
    }

    public static void checkMusicConnections() {
        for (Shard shard : ShardManager.getShards()) {
            shard.executorService.scheduleAtFixedRate(() -> {
                try {
                    for (Guild guild : shard.jda.getGuilds()) {
                        GuildMusicManager manager = getGuildAudioPlayer(guild, null, shard);
                        if (manager != null) {
                            GuildVoiceState voiceState = guild.getSelfMember().getVoiceState();
                            if (voiceState.inVoiceChannel()) {
                                TextChannel channel = manager.scheduler.manager.getChannel();
                                if (channel == null) channel = guild.getPublicChannel();
                                if (channel != null) {
                                    if (channel.canTalk()) {
                                        VoiceChannel voiceChannel = voiceState.getChannel();
                                        AudioPlayer player = manager.player;
                                        if (voiceState.isGuildMuted()) {
                                            shard.help.sendTranslatedMessage("Pausing player now because I'm muted", channel, null);
                                            player.setPaused(true);
                                        }
                                        if (voiceChannel.getMembers().size() == 1 && !EntityGuild.get(guild).isPremium()) {
                                            shard.help.sendTranslatedMessage("Left {0} because no one was in the channel!".replace("{0}",
                                                    voiceChannel.getName()),
                                                    channel, null);
                                            guild.getAudioManager().closeAudioConnection();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                catch (Exception ex) {
                    new BotException(ex);
                }
            }, 5, 5, TimeUnit.MINUTES);
        }
    }

    static int getHours(AudioTrack track) {
        long length = track.getInfo().length;
        int seconds = (int) (length / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        return hours % 60;
    }

    static String getDuration(AudioTrack track) {
        long length = track.getInfo().length;
        int seconds = (int) (length / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        if (getHours(track) < 0) {
            return "[Live Stream]";
        }
        else {
            return "[" + String.format("%02d", (hours % 60)) + ":" + String.format("%02d", (minutes % 60)) + ":" +
                    String.format("%02d", (seconds % 60)) + "]";
        }
    }

    private static String getDuration(ArrayList<AudioTrack> tracks) {
        long length = 0;
        for (AudioTrack t : tracks) length += t.getDuration();
        int seconds = (int) (length / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        return "[" + String.format("%02d", (hours % 60)) + ":" + String.format("%02d", (minutes % 60)) + ":" + String
                .format("%02d", (seconds % 60)) + "]";
    }

    static String getCurrentTime(AudioTrack track) {
        long current = track.getPosition();
        int seconds = (int) (current / 1000);
        int minutes = seconds / 60;
        int hours = minutes / 60;

        long length = track.getInfo().length;
        int lengthSeconds = (int) (length / 1000);
        int lengthMinutes = lengthSeconds / 60;
        int lengthHours = lengthMinutes / 60;

        return "[" + String.format("%02d", (hours % 60)) + ":" + String.format("%02d", (minutes % 60)) + ":" + String
                .format("%02d", (seconds % 60)) + " / " + String.format("%02d", (lengthHours % 60)) + ":" + String
                .format("%02d", (lengthMinutes % 60)) + ":" + String.format("%02d", (lengthSeconds % 60)) + "]";
    }

    static boolean shouldDeleteMessages(Guild guild) throws SQLException {
        boolean returnValue = false;
        MusicSettingsModel musicSettingsModel = asPojo(r.db("data").table("music_settings").get(guild.getId()).run(connection),
                MusicSettingsModel.class);
        if (musicSettingsModel != null) {
            if (musicSettingsModel.isRemove_addition_messages()) returnValue = true;
        }
        return returnValue;
    }

    static TextChannel getOutputChannel(Guild guild) throws SQLException {
        String id;
        MusicSettingsModel guildMusicSettings = BaseCommand.asPojo(r.db("data").table("music_settings").get(guild.getId()).run
                (connection), MusicSettingsModel.class);
        if (guildMusicSettings != null) {
            String setId = guildMusicSettings.getChannel_id();
            if (setId.equalsIgnoreCase("none")) id = null;
            else id = setId;
        }
        else {
            id = null;
            r.db("data").table("music_settings").insert(r.json(getStaticGson().toJson(new MusicSettingsModel(guild.getId(), false,
                    "none")))).run(connection);
        }
        if (id == null || id.length() < 5) return null;
        else return guild.getTextChannelById(id);
    }

    static MessageChannel sendTo(MessageChannel channel, Guild guild) throws SQLException {
        TextChannel outputChannel = getOutputChannel(guild);
        if (outputChannel != null) return outputChannel;
        else return channel;
    }

    private EmbedBuilder getMusicEmbed(Guild guild, User user) throws Exception {
        EmbedBuilder builder = MessageUtils.getDefaultEmbed(user);
        builder.setAuthor(WordUtils.capitalize(getName()), getShard().url, guild.getSelfMember().getUser().getAvatarUrl());
        return builder;
    }

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
        sendHelp(channel, guild, user, this);
    }

    @Override
    public void setupSubcommands() throws Exception {
        subcommands.add(new Subcommand("Play a song by its name or url", "play") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /play <name/url> or /fancyplay <name/url> instead or type /help for to see our music " +
                        "category commands!", channel, user);
            }
        });

        subcommands.add(new Subcommand("Ardent will add recommended tracks based on currently playing songs", "recommend [amt of songs]",
                "recommend") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /recommend <number> instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("View set music settings for the current guild", "config") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /mconfig instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("View the currently queued songs", "queue") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /queue instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("Skip the currently playing song (must have queued it or have permissions)", "skip") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /skip instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("Remove a song from the queue by its number", "remove [number in queue]", "remove") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /remove <queue number> instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("Makes me leave the voice channel I'm currently in", "leave") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /leave instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("Resumes music playback if it has been paused", "resume") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /resume instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("Stops playback and clears the queue", "stop") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /stop instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("Vote to skip the current song", "votetoskip") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws
                    Exception {
                sendTranslatedMessage("Please use /votetoskip instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });
        
        subcommands.add(new Subcommand("View information about the currently playing song", "playing") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /playing instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("Shuffle the songs in the queue", "shuffle") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /shuffle instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("Pause music playback", "pause") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /pause instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("Set a channel to send all music output to", "setoutput #mentionchannel", "setoutput") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws
                    Exception {
                sendTranslatedMessage("Please use /musicoutput instead or type /help for to see our music category commands!",
                        channel, user);

            }
        });

        subcommands.add(new Subcommand("This will loop the given amount of songs, starting at the first song in the queue, for the amount" +
                " of times given", "loop [amount of songs starting at first in the queue] [amount of times to loop]", "loop") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /loop <songs in queue> <amount of times> instead or type /help for to see our music " +
                                "category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("Removes all the tracks of the mentioned user from the queue", "removeallfrom @User",
                "removeallfrom") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /removefrom @User instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("Restarts the currently playing song", "restart") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /restart instead or type /help for to see our music category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("Want to know the link of that cool song you're playing now? This subcommand will display it!",
                "geturl [number in queue (optional)]", "geturl") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws
                    Exception {
                sendTranslatedMessage("Please use /geturl <number in queue [OPTIONAL]> instead or type /help for to see our music " +
                                "category commands!",
                        channel, user);
            }
        });

        subcommands.add(new Subcommand("volume [number between 1 and 100]", "Sets the volume of the music player to the specified value -" +
                " Staff and Patrons only!", "volume") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
                sendTranslatedMessage("Please use /volume <number [optional]> instead or type /help for to see our music category " +
                                "commands!",
                        channel, user);
            }
        });
    }
}
