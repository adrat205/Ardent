package tk.ardentbot.core.events;

import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import tk.ardentbot.commands.games.Trivia;
import tk.ardentbot.main.Shard;
import tk.ardentbot.utils.discord.GuildUtils;
import tk.ardentbot.utils.rpg.TriviaGame;

import java.util.Iterator;

class TriviaChecker {
    static void check(MessageReceivedEvent event) throws Exception {
        if (event.isFromType(ChannelType.TEXT)) {
            User user = event.getAuthor();
            Guild guild = event.getGuild();
            Shard shard = GuildUtils.getShard(guild);
            TextChannel channel = event.getTextChannel();
            for (Iterator<TriviaGame> iterator = Trivia.gamesInSession.iterator(); iterator.hasNext(); ) {
                TriviaGame triviaGame = iterator.next();
                if (triviaGame.getGuildId().equalsIgnoreCase(guild.getId()) && triviaGame.getTextChannelId().equalsIgnoreCase(channel
                        .getId()))
                {
                    if (!triviaGame.isAnsweredCurrentQuestion()) {
                        if (triviaGame.isSolo() && !triviaGame.getCreator().equalsIgnoreCase(user.getId())) return;
                        String content = event.getMessage().getContent();
                        if (triviaGame.getCurrentTriviaQuestion() != null) {
                            final boolean[] correct = {false};
                            triviaGame.getCurrentTriviaQuestion().getAnswers().forEach(a -> {
                                if (a.equalsIgnoreCase(content)) correct[0] = true;
                            });
                            if (correct[0]) {
                                triviaGame.addPoint(user);
                                shard.help.sendEditedTranslation("{0} got it right!", user, channel,
                                        user.getAsMention());
                                if (triviaGame.getRound() != triviaGame.getTotalRounds()) {
                                    Trivia.dispatchRound(guild, channel, guild.getMemberById(triviaGame.getCreator()).getUser(), triviaGame,
                                            triviaGame.getEx());
                                }
                                else triviaGame.finish(shard, shard.help);
                            }
                        }
                    }
                }
            }
        }
    }
}
