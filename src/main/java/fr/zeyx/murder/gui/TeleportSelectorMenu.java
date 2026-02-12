package fr.zeyx.murder.gui;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import fr.zeyx.murder.game.Role;
import fr.zeyx.murder.manager.GameManager;
import fr.zeyx.murder.util.ChatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntSupplier;

public class TeleportSelectorMenu {

    private final GameManager gameManager;
    private final Function<UUID, String> identityDisplayNameResolver;
    private final Function<Player, String> chatNameResolver;
    private final Function<UUID, Role> roleResolver;
    private final Function<UUID, Integer> weaponCountResolver;
    private final IntSupplier murdererKillCountResolver;

    public TeleportSelectorMenu(GameManager gameManager,
                                Function<UUID, String> identityDisplayNameResolver,
                                Function<Player, String> chatNameResolver,
                                Function<UUID, Role> roleResolver,
                                Function<UUID, Integer> weaponCountResolver,
                                IntSupplier murdererKillCountResolver) {
        this.gameManager = gameManager;
        this.identityDisplayNameResolver = identityDisplayNameResolver;
        this.chatNameResolver = chatNameResolver;
        this.roleResolver = roleResolver;
        this.weaponCountResolver = weaponCountResolver;
        this.murdererKillCountResolver = murdererKillCountResolver;
    }

    public void open(Player spectator, List<UUID> alivePlayerIds) {
        if (spectator == null) {
            return;
        }
        List<Player> targets = resolveOrderedTargets(alivePlayerIds);
        if (targets.isEmpty()) {
            spectator.sendMessage(ChatUtil.prefixed("&cNo alive players to watch."));
            return;
        }

        int rows = Math.max(1, Math.min(6, (targets.size() + 8) / 9));
        Gui gui = Gui.gui()
                .title(ChatUtil.component("&9Select a Player"))
                .rows(rows)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        int slot = 0;
        int maxSlots = rows * 9;
        for (Player target : targets) {
            if (slot >= maxSlots) {
                break;
            }
            gui.setItem(slot++, buildTargetItem(spectator, target));
        }
        gui.open(spectator);
    }

    private List<Player> resolveOrderedTargets(List<UUID> alivePlayerIds) {
        List<Player> targets = new ArrayList<>();
        if (alivePlayerIds == null) {
            return targets;
        }
        for (UUID playerId : alivePlayerIds) {
            Player target = Bukkit.getPlayer(playerId);
            if (target != null) {
                targets.add(target);
            }
        }
        targets.sort(Comparator
                .comparingInt((Player player) -> rolePriority(roleResolver.apply(player.getUniqueId())))
                .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return targets;
    }

    private GuiItem buildTargetItem(Player spectator, Player target) {
        UUID targetId = target.getUniqueId();
        Role role = roleResolver.apply(targetId);
        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);

        if (chestplate.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(gameManager.getSecretIdentityManager().getCurrentIdentityLeatherColor(targetId));

            String identityName = identityDisplayNameResolver.apply(targetId);
            if (identityName == null || identityName.isBlank()) {
                identityName = "&f" + target.getName();
            }
            meta.displayName(ChatUtil.itemComponent(identityName));

            List<Component> lore = new ArrayList<>();
            lore.add(ChatUtil.itemComponent(role == Role.MURDERER ? "&4Murderer" : "&9Bystander"));
            lore.add(ChatUtil.itemComponent("&7Weapons: &a" + Math.max(0, weaponCountResolver.apply(targetId))));
            lore.add(ChatUtil.itemComponent("&7Emeralds: &a0"));
            if (role == Role.MURDERER) {
                lore.add(ChatUtil.itemComponent("&7Kills: &a" + Math.max(0, murdererKillCountResolver.getAsInt())));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            chestplate.setItemMeta(meta);
        }

        return ItemBuilder.from(chestplate).asGuiItem(event -> {
            event.setCancelled(true);
            if (target.isOnline()) {
                spectator.teleport(target.getLocation());
                spectator.sendMessage(ChatUtil.prefixed("&7Now watching " + chatNameResolver.apply(target) + "&7."));
            }
            spectator.closeInventory();
        });
    }

    private int rolePriority(Role role) {
        if (role == Role.MURDERER) {
            return 0;
        }
        if (role == Role.DETECTIVE) {
            return 1;
        }
        return 2;
    }
}
