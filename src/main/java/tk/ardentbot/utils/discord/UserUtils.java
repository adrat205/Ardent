package tk.ardentbot.utils.discord;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import tk.ardentbot.main.Ardent;
import tk.ardentbot.main.Shard;
import tk.ardentbot.main.ShardManager;
import tk.ardentbot.utils.rpg.BadgesList;
import tk.ardentbot.utils.rpg.profiles.Badge;
import tk.ardentbot.utils.rpg.profiles.Profile;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toCollection;
import static tk.ardentbot.main.Ardent.*;

public class UserUtils {
    public static boolean isStaff(User user) {
        String id = user.getId();
        return developers.contains(id) || moderators.contains(id) || translators.contains(id);
    }

    public static boolean hasTierOnePermissions(User user) {
        String id = user.getId();
        boolean normalPermissions = isStaff(user) || tierOnepatrons.contains(id) || tierTwopatrons.contains(id) || tierThreepatrons
                .contains(id);
        if (!normalPermissions) {
            Profile profile = Profile.get(user);
            for (Badge badge : profile.getBadges()) {
                if (BadgesList.from(badge.getId()).getId().equalsIgnoreCase(BadgesList.PREMIUM_TRIAL.getId())) {
                    return true;
                }
            }
            return false;
        }
        else return true;
    }

    public static boolean hasTierTwoPermissions(User user) {
        String id = user.getId();
        boolean normalPermissions = isStaff(user) || tierTwopatrons.contains(id) || tierThreepatrons.contains(id);
        if (!normalPermissions) {
            Profile profile = Profile.get(user);
            for (Badge badge : profile.getBadges()) {
                if (BadgesList.from(badge.getId()).getId().equalsIgnoreCase(BadgesList.PREMIUM_TRIAL.getId())) {
                    return true;
                }
            }
            return false;
        }
        else return true;
    }

    public static boolean hasTierThreePermissions(User user) {
        String id = user.getId();
        boolean normalPermissions = isStaff(user) || tierThreepatrons.contains(id);
        if (!normalPermissions) {
            Profile profile = Profile.get(user);
            for (Badge badge : profile.getBadges()) {
                if (BadgesList.from(badge.getId()).getId().equalsIgnoreCase(BadgesList.PREMIUM_TRIAL.getId())) {
                    return true;
                }
            }
            return false;
        }
        else return true;
    }

    public static boolean isBotCommander(Member member) {
        return member.getRoles().stream().filter(r -> r.getName().equalsIgnoreCase("bot commander")).count() > 0;
    }

    public static boolean hasManageServerOrStaff(Member member) {
        return member.hasPermission(Permission.MANAGE_SERVER) || Ardent.developers.contains(member.getUser().getId())
                || Ardent.moderators.contains(member.getUser().getId());
    }

    public static ArrayList<String> getNamesById(List<String> ids) {
        return ids.stream().map(id -> {
            User user = getUserById(id);
            if (user != null) return user.getName();
            else return null;
        }).collect(toCollection(ArrayList::new));
    }

    public static ArrayList<User> getUsersById(List<String> ids) {
        return ids.stream().map(UserUtils::getUserById).collect(toCollection((ArrayList::new)));
    }

    public static User getUserById(String id) {
        for (Shard shard : ShardManager.getShards()) {
            User user = shard.jda.getUserById(id);
            if (user != null) return user;
        }
        return null;
    }

    public static void addMoney(User user, double amount) {
        double finalAmount;
        if (UserUtils.hasTierThreePermissions(user)) finalAmount = amount * 1.6;
        else if (UserUtils.hasTierTwoPermissions(user)) finalAmount = amount * 1.4;
        else if (UserUtils.hasTierOnePermissions(user)) finalAmount = amount * 1.2;
        else finalAmount = amount;
        Profile.get(user).addMoney(finalAmount);
    }

    public static String getNameWithDiscriminator(String id) {
        User user = getUserById(id);
        if (user == null) return null;
        return user.getName() + "#" + user.getDiscriminator();
    }

}
