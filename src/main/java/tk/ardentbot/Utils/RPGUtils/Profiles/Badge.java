package tk.ardentbot.Utils.RPGUtils.Profiles;

import net.dv8tion.jda.core.entities.User;
import tk.ardentbot.Utils.Discord.UserUtils;

public class Badge {
    private String userId;
    private String id;
    private String name;
    private boolean guildWide;
    private long expirationEpochSeconds;

    public Badge(String userId, String id, String name, boolean guildWide, long expirationEpochSeconds) {
        this.userId = userId;
        this.id = id;
        this.name = name;
        this.guildWide = guildWide;
        this.expirationEpochSeconds = expirationEpochSeconds;
    }

    public String getId() {
        return id;
    }

    public boolean isGuildWide() {
        return guildWide;
    }

    public long getExpirationEpochSeconds() {
        return expirationEpochSeconds;
    }

    public User getUser() {
        return UserUtils.getUserById(userId);
    }

    public String getName() {
        return name;
    }
}
