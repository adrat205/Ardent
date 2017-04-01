package tk.ardentbot.Utils.RPGUtils.Profiles;

import lombok.Getter;
import net.dv8tion.jda.core.entities.User;
import tk.ardentbot.Core.Misc.LoggingUtils.BotException;
import tk.ardentbot.Core.Translation.LangFactory;
import tk.ardentbot.Main.Ardent;
import tk.ardentbot.Main.Shard;
import tk.ardentbot.Utils.Discord.UserUtils;
import tk.ardentbot.Utils.SQL.DatabaseAction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Profile {
    private String userId;
    private double moneyAmount;
    @Getter
    private double stocksOwned;
    private List<Badge> badges = new ArrayList<>();

    public Profile(User user) throws SQLException {
        this.userId = user.getId();
        DatabaseAction getProfile = new DatabaseAction("SELECT * FROM Profiles WHERE UserID=?").set(userId);
        ResultSet set = getProfile.request();
        if (set.next()) {
            moneyAmount = set.getDouble("Money");
            DatabaseAction retrieveBadges = new DatabaseAction("SELECT * FROM Badges WHERE UserID=?").set(userId);
            ResultSet badgesWithId = retrieveBadges.request();
            while (badgesWithId.next()) {
                badges.add(new Badge(userId, badgesWithId.getString("BadgeID"), badgesWithId.getString("FeatureName"), badgesWithId
                        .getBoolean("GuildWide"), badgesWithId
                        .getLong("ExpirationTime")));
            }
            retrieveBadges.close();
        }
        else {
            moneyAmount = 25;
            new DatabaseAction("INSERT INTO Profiles VALUES (?,?)").set(userId).set(25).update();
        }
        getProfile.close();
        Ardent.profileUpdateExecutorService.scheduleAtFixedRate(() -> {
            try {
                for (Iterator<Badge> iterator = badges.iterator(); iterator.hasNext(); ) {
                    Badge badge = iterator.next();
                    if (badge.getExpirationEpochSeconds() < Instant.now().getEpochSecond()) {
                        user.openPrivateChannel().queue(privateChannel -> {
                            privateChannel.sendMessage("Your badge with the ID of **" + badge.getName() + "** has expired.").queue();
                            iterator.remove();
                        });
                    }
                }
                new DatabaseAction("UPDATE Profiles SET Money=? WHERE UserID=?").set(moneyAmount).set(userId).update();
                new DatabaseAction("DELETE FROM Badges WHERE UserID=?").set(userId).update();
                for (Badge badge : getBadges()) {
                    new DatabaseAction("INSERT INTO Badges VALUES (?,?,?,?)").set(userId).set(badge.getId()).set(badge
                            .isGuildWide())
                            .set(Timestamp.from(Instant.ofEpochSecond(badge.getExpirationEpochSeconds()))).update();
                }
            }
            catch (SQLException e) {
                new BotException(e);
            }
        }, 5, 15, TimeUnit.SECONDS);
    }

    public Profile(String userId, double moneyAmount, List<Badge> badges) {
        this.userId = userId;
        this.moneyAmount = moneyAmount;
        this.badges = badges;
    }

    public static Profile get(User user) {
        Profile toReturn;
        String id = user.getId();
        if (Ardent.userProfiles.containsKey(id)) {
            toReturn = Ardent.userProfiles.get(id);
        }
        else {
            Profile profile = null;
            try {
                profile = new Profile(user);
                Ardent.userProfiles.put(id, profile);
            }
            catch (SQLException e) {
                e.printStackTrace();
            }
            toReturn = profile;
        }
        int lucky = new Random().nextInt(2000);
        if (lucky == 69) {
            Shard shard0 = Ardent.shard0;
            user.openPrivateChannel().queue(privateChannel -> {
                try {
                    if (toReturn != null) {
                        toReturn.addMoney(2500);
                        shard0.help.sendRetrievedTranslation(privateChannel, "rpg", LangFactory.english, "gotlucky", user);
                    }
                }
                catch (Exception e) {
                    new BotException(e);
                }
            });
        }
        return toReturn;
    }

    public User getUser() {
        return UserUtils.getUserById(userId);
    }

    public double getMoneyAmount() {
        return moneyAmount;
    }

    public List<Badge> getBadges() {
        return badges;
    }

    public void addMoney(double amount) {
        moneyAmount += amount;
    }

    public void removeMoney(double amount) {
        moneyAmount -= amount;
    }

    public void addStock(double amountToAdd) {
        stocksOwned += amountToAdd;
    }

    public void removeStock(double amountToRemove) {
        stocksOwned -= amountToRemove;
    }
}
