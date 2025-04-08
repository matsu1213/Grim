package ac.grim.grimac.utils.anticheat;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.util.reflection.Reflection;
import lombok.experimental.UtilityClass;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class MessageUtil {
    private final Pattern HEX_PATTERN = Pattern.compile("([&§]#[A-Fa-f0-9]{6})|([&§]x([&§][A-Fa-f0-9]){6})");
    private final BukkitAudiences adventure = BukkitAudiences.create(GrimAPI.INSTANCE.getPlugin());
    public final boolean hasPlaceholderAPI = Reflection.getClassByNameWithoutException("me.clip.placeholderapi.PlaceholderAPI") != null;

    public @NotNull String toUnlabledString(@Nullable Vector3i vec) {
        return vec == null ? "null" : vec.x + ", " + vec.y + ", " + vec.z;
    }

    public @NotNull String toUnlabledString(@Nullable Vector3f vec) {
        return vec == null ? "null" : vec.x + ", " + vec.y + ", " + vec.z;
    }

    public @NotNull String replacePlaceholders(@NotNull GrimPlayer player, @NotNull String string) {
        return replacePlaceholders(player.bukkitPlayer, GrimAPI.INSTANCE.getExternalAPI().replaceVariables(player, string));
    }

    public @NotNull String replacePlaceholders(@Nullable Object object, @NotNull String string) {
        if (!hasPlaceholderAPI) return string;
        return PlaceholderAPI.setPlaceholders(object instanceof OfflinePlayer player ? player : null, string);
    }

    public @NotNull Component replacePlaceholders(@NotNull GrimPlayer player, @NotNull Component component) {
        // Replacement config that forces any placeholder replacement to be pure text
        final TextReplacementConfig safeReplacement = TextReplacementConfig.builder()
                .match("%[a-zA-Z0-9_]+%") // Match placeholders
                .replacement(placeholder -> Component.text(replacePlaceholders(player, placeholder.content())))
                .build();
        return component.replaceText(safeReplacement);
    }

    public @NotNull Component miniMessage(@NotNull String string) {
        string = string.replace("%prefix%", GrimAPI.INSTANCE.getConfigManager().getConfig().getStringElse("prefix", "&bGrim &8»"));

        // hex codes
        Matcher matcher = HEX_PATTERN.matcher(string);
        StringBuilder sb = new StringBuilder(string.length());

        while (matcher.find()) {
            matcher.appendReplacement(sb, "<#" + matcher.group(0).replaceAll("[&§#x]", "") + ">");
        }

        string = matcher.appendTail(sb).toString();

        // MiniMessage doesn't like legacy formatting codes
        string = ChatColor.translateAlternateColorCodes('&', string)
                .replace("§0", "<reset><black>")
                .replace("§1", "<reset><dark_blue>")
                .replace("§2", "<reset><dark_green>")
                .replace("§3", "<reset><dark_aqua>")
                .replace("§4", "<reset><dark_red>")
                .replace("§5", "<reset><dark_purple>")
                .replace("§6", "<reset><gold>")
                .replace("§7", "<reset><gray>")
                .replace("§8", "<reset><dark_gray>")
                .replace("§9", "<reset><blue>")
                .replace("§a", "<reset><green>")
                .replace("§b", "<reset><aqua>")
                .replace("§c", "<reset><red>")
                .replace("§d", "<reset><light_purple>")
                .replace("§e", "<reset><yellow>")
                .replace("§f", "<reset><white>")
                .replace("§r", "<reset>")
                .replace("§k", "<obfuscated>")
                .replace("§l", "<bold>")
                .replace("§m", "<strikethrough>")
                .replace("§n", "<underlined>")
                .replace("§o", "<italic>");

        return MiniMessage.miniMessage().deserialize(string).compact();
    }

    public void sendMessage(@NotNull CommandSender commandSender, @NotNull Component component) {
        adventure.sender(commandSender).sendMessage(component);
    }
}
