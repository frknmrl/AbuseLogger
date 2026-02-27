package com.uxplima.abuselogger.listeners;

import com.uxplima.abuselogger.AbuseLogger;
import com.uxplima.claim.app.facade.ClaimFacade;
import com.uxplima.claim.bukkit.api.BukkitConverter;
import com.uxplima.claim.domain.model.Claim;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

public class ClaimListener implements Listener {

    private final AbuseLogger plugin;
    private final ClaimFacade claimFacade;

    public ClaimListener(AbuseLogger plugin) {
        this.plugin = plugin;
        this.claimFacade = plugin.getClaimFacade();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        handleAction(event.getPlayer(), event.getBlock(), "break");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        handleAction(event.getPlayer(), event.getBlock(), "place");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) {
            handleAction(event.getPlayer(), event.getBlock(), "ignite");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        handleAction(event.getPlayer(), event.getBlock(), "bucket_empty");
    }

    private void handleAction(Player player, Block block, String actionType) {
        if (player.hasPermission("abuse.logger.admin"))
            return;

        int radius = plugin.getConfig().getInt("radius", 10);
        org.bukkit.Location bLoc = block.getLocation();

        // Find if there is a claim in radius that the player is not a member of
        Claim nearbyClaim = findNearbyUnauthorizedClaim(player, bLoc, radius);

        if (nearbyClaim != null) {
            String actionName = plugin.getConfig().getString("actions." + actionType, actionType);
            String claimName = nearbyClaim.getName();

            // Log to file (async)
            plugin.getLoggerManager().log(player.getName(), claimName, actionName, bLoc.getX(), bLoc.getY(),
                    bLoc.getZ());

            // Notify Admins
            notifyAdmins(player, claimName, actionName, bLoc);

            // Notify Claim Members/Owner
            notifyMembers(nearbyClaim, player, actionName, bLoc);
        }
    }

    private Claim findNearbyUnauthorizedClaim(Player player, org.bukkit.Location origin, int radius) {
        // Current chunk coordinates
        int centralX = origin.getBlockX() >> 4;
        int centralZ = origin.getBlockZ() >> 4;
        org.bukkit.World world = origin.getWorld();

        if (world == null)
            return null;

        // Search in a grid of chunks [central - radius, central + radius]
        for (int x = centralX - radius; x <= centralX + radius; x++) {
            for (int z = centralZ - radius; z <= centralZ + radius; z++) {
                // We use the center of the chunk to check for a claim
                // This works because claims are chunk-aligned
                org.bukkit.Location chunkCenter = new org.bukkit.Location(world, (x << 4) + 8, 64, (z << 4) + 8);
                Claim found = claimFacade.getByLocationUnsafe(BukkitConverter.toDomainLocation(chunkCenter));

                if (found != null) {
                    if (!isMember(found, player)) {
                        return found;
                    }
                }
            }
        }

        return null;
    }

    private boolean isMember(Claim claim, Player player) {
        return claim.isOwner(player.getUniqueId()) || claim.findMemberByUid(player.getUniqueId()).isPresent();
    }

    private void notifyAdmins(Player violator, String claimName, String action, org.bukkit.Location loc) {
        String msgFormat = plugin.getConfig().getString("messages.admin_notification", "")
                .replace("{", "<")
                .replace("}", ">");
        var msg = plugin.getMiniMessage().deserialize(msgFormat,
                Placeholder.parsed("player", violator.getName()),
                Placeholder.parsed("claim", claimName),
                Placeholder.parsed("action", action),
                Placeholder.parsed("x", String.valueOf(loc.getBlockX())),
                Placeholder.parsed("y", String.valueOf(loc.getBlockY())),
                Placeholder.parsed("z", String.valueOf(loc.getBlockZ())));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("abuse.logger.admin") && plugin.getAlertManager().isAlertsEnabled(p.getUniqueId())) {
                p.sendMessage(msg);
            }
        }
    }

    private void notifyMembers(Claim claim, Player violator, String action, org.bukkit.Location loc) {
        String msgFormat = plugin.getConfig().getString("messages.player_notification", "")
                .replace("{", "<")
                .replace("}", ">");
        var msg = plugin.getMiniMessage().deserialize(msgFormat,
                Placeholder.parsed("player", violator.getName()),
                Placeholder.parsed("claim", claim.getName()),
                Placeholder.parsed("action", action));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("abuse.logger.player") && plugin.getAlertManager().isAlertsEnabled(p.getUniqueId())) {
                if (claim.isOwner(p.getUniqueId()) || claim.findMemberByUid(p.getUniqueId()).isPresent()) {
                    p.sendMessage(msg);
                }
            }
        }
    }
}
