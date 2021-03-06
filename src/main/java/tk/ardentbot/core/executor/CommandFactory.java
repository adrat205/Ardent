package tk.ardentbot.core.executor;

import com.google.code.chatterbotapi.ChatterBotSession;
import com.mashape.unirest.http.Unirest;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;
import org.apache.commons.lang3.StringEscapeUtils;
import tk.ardentbot.core.misc.logging.BotException;
import tk.ardentbot.main.Ardent;
import tk.ardentbot.main.Shard;
import tk.ardentbot.rethink.models.GuildModel;
import tk.ardentbot.rethink.models.RolePermission;
import tk.ardentbot.utils.discord.UserUtils;
import tk.ardentbot.utils.models.RestrictedUser;
import tk.ardentbot.utils.rpg.EntityGuild;

import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static tk.ardentbot.rethink.Database.connection;
import static tk.ardentbot.rethink.Database.r;

public class CommandFactory {
    private Shard shard;

    private HashMap<String, Long> commandUsages = new HashMap<>();

    private ConcurrentLinkedQueue<BaseCommand> baseCommands = new ConcurrentLinkedQueue<>();
    private long messagesReceived = 0;
    private long commandsReceived = 0;

    /**
     * Schedules emoji command updates with 150 second intervals,
     * as emoji parsing doesn't otherwise work with the existing system
     */
    public CommandFactory(Shard shard) {
        this.shard = shard;
    }

    public static ChatterBotSession getBotSession(Guild guild) {
        return Ardent.cleverbots.get(guild.getId());
    }

    public ConcurrentLinkedQueue<BaseCommand> getBaseCommands() {
        return baseCommands;
    }

    public int getLoadedCommandsAmount() {
        return baseCommands.size();
    }

    public long getMessagesReceived() {
        return messagesReceived;
    }

    public long getCommandsReceived() {
        return commandsReceived;
    }

    public HashMap<String, Long> getCommandUsages() {
        return commandUsages;
    }

    public Shard getShard() {
        return shard;
    }

    public void addCommandUsage(String identifier) {
        long old = commandUsages.get(identifier);
        commandUsages.replace(identifier, old, old + 1);
    }

    /**
     * Registers a baseCommand to the factory, provides a simple check for duplicates
     *
     * @param baseCommand baseCommand to be added
     * @throws Exception
     */
    public void registerCommand(BaseCommand baseCommand) throws Exception {
        baseCommand.setShard(shard);
        Command botCommand = baseCommand.botCommand;
        botCommand.setupSubcommands();
        baseCommands.add(baseCommand);
        commandUsages.put(baseCommand.getName(), (long) 0);
    }

    /**
     * Handles generic message events, parses message content
     * and creates a new AsyncCommandExecutor that will execute the command
     *
     * @param event the MessageReceivedEvent to be handled
     */
    public void pass(MessageReceivedEvent event, String prefix) {
        try {
            Guild guild = event.getGuild();
            User ardent = guild.getSelfMember().getUser();
            User user = event.getAuthor();
            if (user.isBot()) return;
            Message message = event.getMessage();
            MessageChannel channel = event.getChannel();
            String[] args = message.getContent().split(" ");
            String rawContent = message.getRawContent();
            if (rawContent.startsWith(guild.getSelfMember().getUser().getAsMention())) {
                rawContent = rawContent.replaceFirst(ardent.getAsMention(), "");
                if (rawContent.length() == 0)
                    channel.sendMessage("Type @Ardent [msg] to talk to me or /help to see a list of commands").queue();
                else {
                    if (!Ardent.disabledCommands.contains("cleverbot")) {
                        channel.sendMessage(Unirest.post("https://cleverbot.io/1.0/ask").field("user", Ardent.cleverbotUser)
                                .field("key", Ardent.cleverbotKey).field("nick", "ardent").field("text", rawContent).asJson()
                                .getBody().getObject().getString("response")).queue();
                    }
                    else channel.sendMessage("Cleverbot is currently disabled, sorry.").queue();
                }
                return;
            }
            if (!args[0].startsWith(prefix)) return;
            String cmd = args[0].replaceFirst(prefix, "");
            final boolean[] ranCommand = {false};
            String pre = StringEscapeUtils.escapeJava(prefix);
            if (args[0].startsWith(pre)) {
                args[0] = args[0].replaceFirst(pre, "");
                baseCommands.forEach(command -> {
                    if (command.getBotCommand().containsAlias(args[0])) {
                        command.botCommand.usages++;
                        if (!Ardent.disabledCommands.contains(command.getName())) {
                            EntityGuild entityGuild = EntityGuild.get(guild);
                            for (RestrictedUser u : entityGuild.getRestrictedUsers()) {
                                if (u.getUserId().equalsIgnoreCase(user.getId())) {
                                    command.sendRestricted(user);
                                    return;
                                }
                            }
                            GuildModel guildModel = BaseCommand.asPojo(r.table("guilds").get(guild.getId()).run(connection),
                                    GuildModel.class);
                            if (guildModel == null) {
                                guildModel = new GuildModel(guild.getId(), "english", "/");
                                r.table("guilds").insert(r.json(shard.gson.toJson(guildModel))).runNoReply(connection);
                            }
                            if (guildModel.role_permissions != null) {
                                for (RolePermission rolePermission : guildModel.role_permissions) {
                                    Member member = guild.getMember(user);
                                    Role r = guild.getRoleById(rolePermission.getId());
                                    if (r != null && member.getRoles().contains(r) && !member.hasPermission(Permission
                                            .MANAGE_SERVER))
                                    {
                                        if (!rolePermission.getCanUseArdentCommands()) {
                                            user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("One of " +
                                                    "your roles, **" + r.getName() + "**, cannot send Ardent commands!").queue());
                                            return;
                                        }
                                        if (!message.getRawContent().toLowerCase().contains("discord.gg") && !rolePermission
                                                .getCanSendDiscordInvites())
                                        {
                                            message.delete().queue();
                                            user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("One of " +
                                                    "your roles, **" + r.getName() + "**, cannot send Discord server invite " +
                                                    "links!").queue());
                                            return;
                                        }
                                        if (!rolePermission.getCanSendLinks()) {
                                            if (message.getContent().toLowerCase().contains("http://") ||
                                                    message.getContent().toLowerCase().contains("https://"))
                                            {
                                                message.delete().queue();
                                                user.openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("One" +
                                                        " of " +
                                                        "your roles, **" + r.getName() + "**, cannot send websiet links!").queue());
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                            new AsyncCommandExecutor(command.botCommand, guild, channel, event.getAuthor(), message, args, user)
                                    .run();
                            commandsReceived++;
                            ranCommand[0] = true;
                            UserUtils.addMoney(user, 1);
                        }
                        else {
                            command.sendTranslatedMessage("Sorry, this command is currently disabled and will be re-enabled soon."
                                    , channel, user);
                            ranCommand[0] = true;
                        }
                    }
                });
            }
            if (!ranCommand[0]) {
                if (!prefix.equalsIgnoreCase("/")) {
                    pass(event, "/");
                }
            }
        }
        catch (Throwable ex) {
            if (ex instanceof PermissionException) {
                event.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("I don't have permission to " +
                        "send a message in this channel, please tell a server administrator").queue());
            }
            else {
                new BotException(ex);
            }
        }
    }

    public void incrementMessagesReceived() {
        messagesReceived += 1;
    }
}
