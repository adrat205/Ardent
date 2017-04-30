package tk.ardentbot.commands.guildinfo;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import tk.ardentbot.core.executor.Command;

import static tk.ardentbot.main.Ardent.botLogsShard;

public class GuildInvite extends Command {
    public GuildInvite(CommandSettings commandSettings) {
        super(commandSettings);
    }

    @Override
    public void noArgs(Guild guild, MessageChannel channel, User user, Message message, String[] args) throws Exception {
        if (guild.getMember(user).hasPermission(Permission.CREATE_INSTANT_INVITE)) {
            String link = botLogsShard.jda.asBot().getInviteUrl();
            sendTranslatedMessage("The invite link for this server is " + link, channel, user);




        }
        else {
            sendTranslatedMessage("You don't have permissions to get the invite link to this server!\n Permissions needed: ```Create Instant Invite```", channel, user);
        }
    }

    @Override
    public void setupSubcommands() {

    }
}
