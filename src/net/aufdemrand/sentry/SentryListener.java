package net.aufdemrand.sentry;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

@SuppressWarnings("WeakerAccess")
public class SentryListener implements Listener {

    public Sentry plugin;

    public SentryListener(final Sentry sentry) {
        this.plugin = sentry;
    }

    @EventHandler
    public void kill(final org.bukkit.event.entity.EntityDeathEvent event) {

        if (event.getEntity() == null) { return; }

        //don't mess with player death.
        if (event.getEntity() instanceof Player && !event.getEntity().hasMetadata("NPC")) { return; }

        Entity killer = event.getEntity().getKiller();
        if (killer == null) {
            //might have been a projectile.
            final EntityDamageEvent ev = event.getEntity().getLastDamageCause();
            if (ev != null && ev instanceof EntityDamageByEntityEvent) {
                killer = ((EntityDamageByEntityEvent) ev).getDamager();
                if (killer instanceof Projectile && ((Projectile) killer).getShooter() instanceof Entity) {
                    killer = (Entity) ((Projectile) killer).getShooter();
                }
            }
        }

        final SentryInstance sentry = this.plugin.getSentry(killer);

        if (sentry != null && !sentry.doesKillsDropInventory()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void despawn(final net.citizensnpcs.api.event.NPCDespawnEvent event) {
        final SentryInstance sentry = this.plugin.getSentry(event.getNPC());
        //don't despawn active bodyguards on chunk unload
        if (sentry != null && event.getReason() == net.citizensnpcs.api.event.DespawnReason.CHUNK_UNLOAD &&
            sentry.getGuardEntity() != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void entityTeleportEvent(final org.bukkit.event.entity.EntityTeleportEvent event) {
        final SentryInstance sentry = this.plugin.getSentry(event.getEntity());
        if (sentry != null && sentry.getEpcount() != 0 && sentry.isWarlock1()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void entityTeleportEvent(final org.bukkit.event.player.PlayerTeleportEvent event) {
        final SentryInstance sentry = this.plugin.getSentry(event.getPlayer());
        if (sentry != null) {
            if (sentry.getEpcount() != 0 && sentry.isWarlock1() &&
                event.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void projectilehit(final org.bukkit.event.entity.ProjectileHitEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.EnderPearl &&
            event.getEntity().getShooter() instanceof Entity) {
            final SentryInstance sentry = this.plugin.getSentry((Entity) event.getEntity().getShooter());
            if (sentry != null) {
                sentry.setEpcount(sentry.getEpcount() - 1);
                if (sentry.getEpcount() < 0) { sentry.setEpcount(0); }
                event.getEntity().getLocation().getWorld()
                     .playEffect(event.getEntity().getLocation(), org.bukkit.Effect.ENDER_SIGNAL, 1, 100);
                //ender pearl from a sentry
            }
        }
        else if (event.getEntity() instanceof org.bukkit.entity.SmallFireball &&
                 event.getEntity().getShooter() instanceof Entity) {
            final org.bukkit.block.Block block = event.getEntity().getLocation().getBlock();
            final SentryInstance sentry = this.plugin.getSentry((Entity) event.getEntity().getShooter());

            if (sentry != null && sentry.isPyromancer1()) {

                this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, () -> {

                    for (final BlockFace face : BlockFace.values()) {
                        if (block.getRelative(face).getType() == org.bukkit.Material.FIRE) {
                            block.getRelative(face).setType(org.bukkit.Material.AIR);
                        }
                    }

                    if (block.getType() == org.bukkit.Material.FIRE) { block.setType(org.bukkit.Material.AIR); }

                });
            }
        }
    }

//	@EventHandler(ignoreCancelled = true, priority =org.bukkit.event.EventPriority.HIGH)
//	public void tarsdfget(EntityTargetEvent event) {
//		SentryInstance inst = plugin.getSentry(event.getTarget());
//		if(inst!=null){
//			event.setCancelled(false); //inst.myNPC.data().get(NPC.DEFAULT_PROTECTED_METADATA, false));
//		}
//	}

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void EnvDamage(final EntityDamageEvent event) {

        if (event instanceof EntityDamageByEntityEvent) { return; }

        final SentryInstance inst = this.plugin.getSentry(event.getEntity());
        if (inst == null) { return; }

        event.setCancelled(true);

        final DamageCause cause = event.getCause();
        //	plugin.getLogger().log(Level.INFO, "Damage " + cause.toString() + " " + event.getDamage());

        switch (cause) {
            case CONTACT:
            case DROWNING:
            case LAVA:
            case SUFFOCATION:
            case CUSTOM:
            case BLOCK_EXPLOSION:
            case VOID:
            case SUICIDE:
            case MAGIC:
                inst.onEnvironmentDamage(event);
                break;
            case LIGHTNING:
                if (!inst.isStormcaller()) { inst.onEnvironmentDamage(event); }
                break;
            case FIRE:
            case FIRE_TICK:
                if (!inst.isPyromancer() && !inst.isStormcaller()) { inst.onEnvironmentDamage(event); }
                break;
            case POISON:
                if (!inst.isWitchDoctor()) { inst.onEnvironmentDamage(event); }
                break;
            case FALL:
                break;
            default:
                break;
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST) //highest for worldguard...
    public void onDamage(final org.bukkit.event.entity.EntityDamageByEntityEvent event) {

        Entity entfrom = event.getDamager();
        final Entity entto = event.getEntity();

        if (entfrom instanceof org.bukkit.entity.Projectile && entfrom instanceof Entity) {
            final ProjectileSource source = ((Projectile) entfrom).getShooter();
            if (source instanceof Entity) {
                entfrom = (Entity) ((org.bukkit.entity.Projectile) entfrom).getShooter();
            }
        }

        final SentryInstance from = this.plugin.getSentry(entfrom);
        final SentryInstance to = this.plugin.getSentry(entto);

        this.plugin.debug(
            "start: from: " + entfrom + " to " + entto + " cancelled " + event.isCancelled() + " damage " +
            event.getDamage() + " cause " + event.getCause());

        if (from != null) {

            //projectiles go thru ignore targets.
            if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
                if (entto instanceof LivingEntity && from.isIgnored((LivingEntity) entto)) {
                    event.setCancelled(true);
                    event.getDamager().remove();
                    final Projectile newProjectile = (Projectile) (entfrom.getWorld().spawnEntity(
                        event.getDamager().getLocation().add(event.getDamager().getVelocity()),
                        event.getDamager().getType()));
                    newProjectile.setVelocity(event.getDamager().getVelocity());
                    newProjectile.setShooter((LivingEntity) entfrom);
                    newProjectile.setTicksLived(event.getDamager().getTicksLived());
                    return;
                }
            }

            //from a sentry
            event.setDamage(from.getStrength());

            //uncancel if not bodyguard.
            if (from.getGuardTarget() == null || !this.plugin.BodyguardsObeyProtection) { event.setCancelled(false); }

            //cancel if invulnerable non-sentry npc
            if (to == null) {
                final NPC n = CitizensAPI.getNPCRegistry().getNPC(entto);
                if (n != null) {
                    final boolean isProtected = n.data().get(NPC.DEFAULT_PROTECTED_METADATA, true);
                    event.setCancelled(isProtected);
                }
            }

            //don't hurt guard target.
            if (entto == from.getGuardEntity()) { event.setCancelled(true); }

            //stop hittin' yourself.
            if (entfrom == entto) { event.setCancelled(true); }

            //apply potion effects
            if (from.getPotionEffects() != null && !event.isCancelled()) {
                ((LivingEntity) entto).addPotionEffects(from.getPotionEffects());
            }

            if (from.isWarlock1()) {
                if (!event.isCancelled()) {
                    if (to == null) {
                        event.setCancelled(
                            true); //warlock 1 should not do direct damamge, except to other sentries which take no fall damage.
                    }

                    final double h = from.getStrength() + 3;
                    double v = 7.7 * Math.sqrt(h) + .2;
                    if (h <= 3) { v -= 2; }
                    if (v > 150) { v = 150; }

                    entto.setVelocity(new Vector(0, v / 20, 0));

                }

            }

        }

        boolean ok = false; //When you try your best but you don't succeeeeed :,(

        if (to != null) {
            //to a sentry

            //stop hittin yourself.
            if (entfrom == entto) { return; }

            //innate protections
            if (event.getCause() == DamageCause.LIGHTNING && to.isStormcaller()) { return; }
            if ((event.getCause() == DamageCause.FIRE || event.getCause() == DamageCause.FIRE_TICK) &&
                (to.isPyromancer() || to.isStormcaller())) { return; }

            //only bodyguards obey pvp-protection
            if (to.getGuardTarget() == null) { event.setCancelled(false); }

            //don't take damage from guard entity.
            if (entfrom == to.getGuardEntity()) { event.setCancelled(true); }

            if (entfrom != null) {
                final NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(entfrom);
                if (npc != null && npc.hasTrait(SentryTrait.class) && to.getGuardEntity() != null) {
                    if (npc.getTrait(SentryTrait.class).getInstance().getGuardEntity() ==
                        to.getGuardEntity()) { //don't take damage from co-guards.
                        event.setCancelled(true);
                    }
                }
            }

            //process event
            if (!event.isCancelled()) {
                ok = true;
                to.onDamage(event);
            }

            //Damage to a sentry cannot be handled by the server. Always cancel the event here.
            event.setCancelled(true);
        }

        //process this event on each sentry to check for respondable events.
        if ((!event.isCancelled() || ok) && entfrom != entto && event.getDamage() > 0) {
            for (final NPC npc : CitizensAPI.getNPCRegistry()) {
                final SentryInstance inst = this.plugin.getSentry(npc);

                if (inst == null || !npc.isSpawned() || npc.getEntity().getWorld() != entto.getWorld()) {
                    continue; //not a sentry, or not this world, or dead.
                }

                if (inst.getGuardEntity() == entto) {
                    if (inst.isRetaliate() && entfrom instanceof LivingEntity) {
                        inst.setTarget((LivingEntity) entfrom, true);
                    }
                }

                //are u attacking mai horse?
                if (inst.getMountNPC() != null && inst.getMountNPC().getEntity() == entto) {
                    if (entfrom == inst.getGuardEntity()) { event.setCancelled(true); }
                    else if (inst.isRetaliate() && entfrom instanceof LivingEntity) {
                        inst.setTarget((LivingEntity) entfrom, true);
                    }

                }

                if (inst.hasTargetType(16) &&
                    inst.getSentryStatus() == net.aufdemrand.sentry.SentryInstance.Status.LOOKING &&
                    entfrom instanceof Player && !CitizensAPI.getNPCRegistry().isNPC(entfrom)) {
                    //pv-something event.
                    if (npc.getEntity().getLocation().distance(entto.getLocation()) <= inst.getSentryRange() ||
                        npc.getEntity().getLocation().distance(entfrom.getLocation()) <= inst.getSentryRange()) {
                        // in range
                        if (inst.getNightVision() >= entfrom.getLocation().getBlock().getLightLevel() ||
                            inst.getNightVision() >= entto.getLocation().getBlock().getLightLevel()) {
                            //can see
                            if (inst.hasLOS(entfrom) || inst.hasLOS(entto)) {
                                //have los
                                if ((!(entto instanceof Player) && inst.containsTarget("event:pve")) ||
                                    (entto instanceof Player && !CitizensAPI.getNPCRegistry().isNPC(entto) &&
                                     inst.containsTarget("event:pvp")) ||
                                    (CitizensAPI.getNPCRegistry().isNPC(entto) && inst.containsTarget("event:pvnpc")) ||
                                    (to != null && inst.containsTarget("event:pvsentry"))) {
                                    //Valid event, attack
                                    if (!inst.isIgnored((LivingEntity) entfrom)) {
                                        this.plugin.debug("");
                                        inst.setTarget((LivingEntity) entfrom, true); //attack the aggressor
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(final net.citizensnpcs.api.event.NPCDeathEvent event) {
        final NPC eventNPC = event.getNPC();
        //if the mount dies carry aggression over.
        for (final NPC npc : CitizensAPI.getNPCRegistry()) {
            final SentryInstance inst = this.plugin.getSentry(npc);
            if (inst == null || !npc.isSpawned() || !inst.isMounted()) {
                continue; //not a sentry, dead, or not mounted
            }
            if (eventNPC.getId() == inst.getMountID()) {
                ///nooooo butterstuff!

                Entity killer = ((LivingEntity) eventNPC.getEntity()).getKiller();
                if (killer == null) {
                    //might have been a projectile.
                    final EntityDamageEvent ev = eventNPC.getEntity().getLastDamageCause();
                    if (ev != null && ev instanceof EntityDamageByEntityEvent) {
                        killer = ((EntityDamageByEntityEvent) ev).getDamager();
                        if (killer instanceof Projectile && ((Projectile) killer).getShooter() instanceof Entity) {
                            killer = (Entity) ((Projectile) killer).getShooter();
                        }
                    }
                }

                final LivingEntity perp = killer instanceof LivingEntity ? (LivingEntity) killer : null;

                if (this.plugin.DenizenActive) {
                    DenizenHook.DenizenAction(npc, "mount death", (perp instanceof Player ? (Player) perp : null));
                }

                if (perp == null) { return; }
                if (inst.isIgnored(perp)) { return; }

                //delay so the mount is gone.
                this.plugin.getServer().getScheduler()
                           .scheduleSyncDelayedTask(this.plugin, () -> inst.setTarget(perp, true), 2);

                return;
            }
        }
    }

    @EventHandler
    public void onNPCRightClick(final net.citizensnpcs.api.event.NPCRightClickEvent event) {
        final SentryInstance inst = this.plugin.getSentry(event.getNPC());
        if (inst == null) { return; }

        if (inst.getMyNPC().getEntity() instanceof org.bukkit.entity.Horse) {
            if (inst.getGuardEntity() != event.getClicker()) {
                event.setCancelled(true);
            }
        }
    }

}
