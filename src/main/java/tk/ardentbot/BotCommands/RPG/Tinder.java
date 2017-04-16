package tk.ardentbot.BotCommands.RPG;

import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import tk.ardentbot.Core.CommandExecution.Command;
import tk.ardentbot.Core.CommandExecution.Subcommand;
import tk.ardentbot.Core.Translation.Language;
import tk.ardentbot.Core.Translation.Translation;
import tk.ardentbot.Core.Translation.TranslationResponse;
import tk.ardentbot.Rethink.Models.TinderMatch;
import tk.ardentbot.Utils.Discord.MessageUtils;
import tk.ardentbot.Utils.Discord.UserUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;

import static tk.ardentbot.Main.Ardent.globalGson;
import static tk.ardentbot.Rethink.Database.connection;
import static tk.ardentbot.Rethink.Database.r;

public class Tinder extends Command {
    public Tinder(CommandSettings commandSettings) {
        super(commandSettings);
    }

    private static ArrayList<TinderMatch> getMutuallySwipedWith(String userId) {
        ArrayList<TinderMatch> mutuallySwipedWith = new ArrayList<>();
        ArrayList<TinderMatch> matches = queryToArraylist(TinderMatch.class, r.table("tinder_matches").filter(r.hashMap
                ("user_id", userId).with("swipedRight", true)).run(connection));
        matches.forEach(potentialMatch -> {
            ArrayList<TinderMatch> personMatches = queryToArraylist(TinderMatch.class, r.table("tinder_matches").filter(r.hashMap
                    ("user_id", potentialMatch.getPerson_id()).with("swipedRight", true)).run(connection));
            personMatches.forEach(personMatch -> {
                if (personMatch.getPerson_id().equals(userId)) mutuallySwipedWith.add(personMatch);
            });
        });
        return mutuallySwipedWith;
    }

    private static boolean swipedRightWith(String userId, String toCheckWithId) {
        final boolean[] mutualSwipedWith = {false};
        ArrayList<TinderMatch> matches = queryToArraylist(TinderMatch.class, r.table("tinder_matches").filter(r.hashMap("user_id",
                userId).with("swipedRight", true)).run(connection));
        matches.forEach(tinderMatch -> {
            if (tinderMatch.getPerson_id().equals(toCheckWithId)) {
                ArrayList<TinderMatch> personMatches = queryToArraylist(TinderMatch.class, r.table("tinder_matches").filter(r.hashMap
                        ("user_id",
                                toCheckWithId).with("swipedRight", true)).run(connection));
                personMatches.forEach(personMatch -> {
                    if (personMatch.getPerson_id().equals(userId)) mutualSwipedWith[0] = true;
                });
            }
        });
        return mutualSwipedWith[0];
    }

    private static User getPotentialMatch(User user, Guild guild, ArrayList<TinderMatch> userMatches) {
        User u = guild.getMembers().get(new SecureRandom().nextInt(guild.getMembers().size())).getUser();
        if (user.getId().equals(u.getId()) || u.isBot()) return getPotentialMatch(user, guild, userMatches);
        for (TinderMatch t : userMatches) {
            if (t.getPerson_id().equals(u.getId())) return getPotentialMatch(user, guild, userMatches);
        }
        return u;
    }

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws Exception {
        sendHelp(language, channel, guild, user, this);
    }

    @Override
    public void setupSubcommands() throws Exception {
        subcommands.add(new Subcommand(this, "message") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws
                    Exception {
                if (args.length == 2) {
                    sendRetrievedTranslation(channel, "tinder", language, "messagesyntax", user);
                    return;
                }
                try {
                    int number = Integer.parseInt(args[2]) - 1;
                    if (number < 0) sendRetrievedTranslation(channel, "tinder", language, "messagesyntax", user);
                    else {
                        ArrayList<TinderMatch> matches = getMutuallySwipedWith(user.getId());
                        if (number >= matches.size()) {
                            sendRetrievedTranslation(channel, "tinder", language, "youdonthavethismanymatches", user);
                            return;
                        }
                        TinderMatch selected = matches.get(number);

                    }
                }
                catch (NumberFormatException e) {
                    sendRetrievedTranslation(channel, "tinder", language, "messagesyntax", user);
                }
            }
        });

        subcommands.add(new Subcommand(this, "connect") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws
                    Exception {
                HashMap<Integer, TranslationResponse> translations = getTranslations(language,
                        new Translation("tinder", "yourmatches"), new Translation("tinder", "swipedrightonyou"),
                        new Translation("tinder", "usetindermessagenumber"));
                String yourMatches = translations.get(0).getTranslation();
                String swipedRightOnYou = translations.get(1).getTranslation();
                EmbedBuilder builder = MessageUtils.getDefaultEmbed(guild, user, Tinder.this);
                builder.setAuthor(yourMatches, getShard().url, getShard().bot.getAvatarUrl());
                builder.setThumbnail(user.getAvatarUrl());
                StringBuilder description = new StringBuilder();
                description.append("**" + yourMatches + "**");
                ArrayList<TinderMatch> matches = queryToArraylist(TinderMatch.class, r.table("tinder_matches").filter(r.hashMap("user_id",
                        user.getId()).with("swipedRight", true)).run(connection));
                for (int i = 0; i < matches.size(); i++) {
                    String swipedRightWithId = matches.get(i).getPerson_id();
                    boolean mutual = swipedRightWith(user.getId(), swipedRightWithId);
                    String yesNo;
                    if (mutual) yesNo = EmojiParser.parseToUnicode(":white_check_mark:");
                    else yesNo = EmojiParser.parseToAliases(":x:");
                    description.append("\n#" + (i + 1) + ": " + UserUtils.getNameWithDiscriminator(swipedRightWithId) + " | " +
                            swipedRightOnYou + ": " + yesNo);
                }
                description.append("\n\n" + translations.get(2).getTranslation());
                sendEmbed(builder.setDescription(description), channel, user);
            }
        });

        subcommands.add(new Subcommand(this, "matchme") {
            @Override
            public void onCall(Guild guild, MessageChannel channel, User user, Message message, String[] args, Language language) throws
                    Exception {
                ArrayList<TinderMatch> matches = queryToArraylist(TinderMatch.class, r.table("tinder_matches").filter(row -> row.g
                        ("user_id").eq(user.getId())).run(connection));
                User potentialMatch = getPotentialMatch(user, guild, matches);
                HashMap<Integer, TranslationResponse> translations = getTranslations(language,
                        new Translation("tinder", "tindermatchme"), new Translation("tinder", "swipeleftorright"),
                        new Translation("tinder", "usetindermessagetostartmessage"), new Translation("tinder", "theirpicture"),
                        new Translation("tinder", "theirname"));
                EmbedBuilder builder = MessageUtils.getDefaultEmbed(guild, user, Tinder.this);
                String matchMe = translations.get(0).getTranslation();
                builder.setAuthor(matchMe, getShard().url, getShard().bot.getAvatarUrl());
                builder.setThumbnail(potentialMatch.getAvatarUrl());
                StringBuilder description = new StringBuilder();
                description.append("**" + matchMe + "**");
                description.append("\n" + translations.get(4).getTranslation() + ": " + UserUtils.getNameWithDiscriminator(potentialMatch
                        .getId()));
                description.append("\n\n" + translations.get(1).getTranslation() + "\n" + translations.get(2).getTranslation());
                Message sent = sendEmbed(builder.setDescription(description.toString()), channel, user, ":arrow_left:", ":arrow_right:");
                interactiveReaction(language, channel, sent, user, 15, messageReaction -> {
                    String name = messageReaction.getEmote().getName();
                    if (name != null) {
                        if (name.equals("➡")) {
                            r.table("tinder_matches").insert(r.json(globalGson.toJson(new TinderMatch(user.getId(), potentialMatch.getId
                                    (), true)))
                            ).run(connection);
                            sendEditedTranslation("tomder", language, "swipedright", user, channel, potentialMatch.getName());
                        }
                        else if (name.equals("⬅")) {
                            r.table("tinder_matches").insert(r.json(globalGson.toJson(new TinderMatch(user.getId(), potentialMatch.getId
                                    (), false)))
                            ).run(connection);
                            sendEditedTranslation("tinder", language, "swipedleft", user, channel, potentialMatch.getName());
                        }
                        else sendRetrievedTranslation(channel, "tinder", language, "invalidreaction", user);
                    }
                });
            }
        });
    }

}
