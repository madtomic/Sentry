package net.aufdemrand.sentry;

import com.google.common.base.Preconditions;
import net.aufdemrand.sentry.events.SentryTargetEntityEvent;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.api.trait.trait.Owner;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPotion;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutAnimation;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
import java.util.*;

/////////////////////////
//Version Specifics

@SuppressWarnings({"WeakerAccess", "unused"})
public class SentryInstance {

    private final Set<Player> _myDamagers = new HashSet<>();
    private final GiveUpStuckAction giveUp = new GiveUpStuckAction(this);
    private final Set<String> _ignoreTargets = new HashSet<>();
    private final Set<String> _validTargets = new HashSet<>();
    /* plugin Constructor */

    private final Sentry plugin;
    private final Map<UUID, Long> warnings = new HashMap<>();
    private final Random random = new Random();
    private final List<String> _nationsEnemies = new ArrayList<>();
    private final List<String> _factionEnemies = new ArrayList<>();
    boolean loaded = false;
    NPC myNPC = null;
    /* Settable */

    SentryTrait myTrait;
    Long isRespawnable = System.currentTimeMillis();
    private Location _projTargetLostLoc;
    private int armor = 0;
    private int sentryRange = 10;
    private int nightVision = 16;
    private int strength = 1;
    private int followDistance = 16;
    private int warningRange = 0;
    private int respawnDelaySeconds = 10;
    private double healRate = 0.0;
    private double attackRateSeconds = 2.0;
    private double sentryHealth = 20;
    private double sentryWeight = 1.0;
    private float sentrySpeed = (float) 1.0;
    private boolean killsDropInventory = true;
    private boolean dropInventory = false;
    private boolean targetable = true;
    private boolean luckyHits = true;
    private boolean ignoreLOS = false;
    private boolean invincible = false;
    private boolean retaliate = true;
    private int lightningLevel = 0;
    private boolean incendiary = false;
    private int mountID = -1;
    private int epcount = 0;
    private String greetingMessage = "&a<NPC> says: Welcome, <PLAYER>!";
    private String warningMessage = "&a<NPC> says: Halt! Come no further!";
    private LivingEntity guardEntity = null;
    private String guardTarget = null;
    private Packet healAnimation = null;
    private List<String> ignoreTargets = new ArrayList<>();
    private List<String> validTargets = new ArrayList<>();
    private boolean lightning = false;
    private LivingEntity meleeTarget;
    private Class<? extends Projectile> myProjectile;
    private long okToFire = System.currentTimeMillis();
    private long okToHeal = System.currentTimeMillis();
    private long okToReasses = System.currentTimeMillis();
    private long okToTakeDamage = 0;
    private List<PotionEffect> potionEffects = null;
    private ItemStack potionType = null;
    private LivingEntity projectileTarget;
    /* Internals */

    private Status sentryStatus = Status.DYING;
    private Location Spawn = null;
    /* Technicals */

    private int taskID = -1;
    private boolean mountCreated = false;
    private int targets = 0;
    private int ignores = 0;
    public SentryInstance(final Sentry plugin) {
        this.plugin = plugin;
        this.isRespawnable = System.currentTimeMillis();
    }
    /**
     * @param exponent The number 2 will be timed by it self
     *
     * @return {@code 2^a}
     */
    private static int powTwo(final int exponent) {
        return 1 << exponent;
    }
    boolean isMounted() {
        return this.mountID >= 0;
    }
    public void cancelRunnable() {
        if (this.taskID != -1) {
            this.plugin.getServer().getScheduler().cancelTask(this.taskID);
        }
    }
    public boolean hasTargetType(final int type) {
        return (this.targets & type) == type;
    }
    public boolean hasIgnoreType(final int type) {
        return (this.ignores & type) == type;
    }
    public boolean isIgnored(final LivingEntity aTarget) {
        //check ignores

        if (aTarget == this.guardEntity) { return true; }

        if (this.ignores == 0) { return false; }

        if (hasIgnoreType(Target.ALL.level)) { return true; }

        if (aTarget instanceof Player && !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget)) {

            if (hasIgnoreType(Target.PLAYERS.level)) { return true; }

            else {

                final OfflinePlayer player = (OfflinePlayer) aTarget;

                if (this.hasIgnoreType(Target.NAMED_PLAYERS.level) && containsIgnore("PLAYER:" + player)) {
                    return true;
                }

                if (this.hasIgnoreType(Target.OWNER.level) &&
                    player.getUniqueId().equals(this.myNPC.getTrait(Owner.class).getOwnerId())) { return true; }

                else if (this.hasIgnoreType(Target.NAMED_NPCS.level)) {

                    final String[] groups1 =
                        this.plugin.perms.getPlayerGroups(aTarget.getWorld().getName(), player); // world perms
                    final String[] groups2 = this.plugin.perms.getPlayerGroups(null, player); //global perms
                    //		String[] groups3 = plugin.perms.getPlayerGroups(aTarget.getWorld().getName(),name); // world perms
                    //	String[] groups4 = plugin.perms.getPlayerGroups((Player)aTarget); // world perms

                    if (groups1 != null) {
                        for (final String aGroups1 : groups1) {
                            //	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found world1 group " + groups1[i] + " on " + name);
                            if (this.containsIgnore("GROUP:" + aGroups1)) { return true; }
                        }
                    }

                    if (groups2 != null) {
                        for (final String aGroups2 : groups2) {
                            //	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found global group " + groups2[i] + " on " + name);
                            if (this.containsIgnore("GROUP:" + aGroups2)) { return true; }
                        }
                    }
                }

                if (this.hasIgnoreType(Target.TOWNY.level)) {
                    final String[] info = this.plugin.getResidentTownyInfo((Player) aTarget);

                    if (info[1] != null) {
                        if (this.containsIgnore("TOWN:" + info[1])) { return true; }
                    }

                    if (info[0] != null) {
                        if (this.containsIgnore("NATION:" + info[0])) { return true; }
                    }
                }

                if (this.hasIgnoreType(Target.FACTION.level)) {
                    final String faction = FactionsUtil.getFactionsTag((Player) aTarget);
                    //	plugin.getLogger().info(faction);
                    if (faction != null) {
                        if (this.containsIgnore("FACTION:" + faction)) { return true; }
                    }
                }
                if (this.hasIgnoreType(Target.WAR.level)) {
                    final String team = this.plugin.getWarTeam((Player) aTarget);
                    //	plugin.getLogger().info(faction);
                    if (team != null) {
                        if (this.containsIgnore("WARTEAM:" + team)) { return true; }
                    }
                }
                if (this.hasIgnoreType(Target.MC_TEAMS.level)) {
                    final String team = this.plugin.getMCTeamName((Player) aTarget);
                    //	plugin.getLogger().info(faction);
                    if (team != null) {
                        if (this.containsIgnore("TEAM:" + team)) { return true; }
                    }
                }
                if (this.hasIgnoreType(Target.CLANS.level)) {
                    final String clan = this.plugin.getClan((Player) aTarget);
                    //	plugin.getLogger().info(faction);
                    if (clan != null) {
                        if (this.containsIgnore("CLAN:" + clan)) { return true; }
                    }
                }
            }
        }

        else if (net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget)) {

            if (this.hasIgnoreType(Target.NPCS.level)) {
                return true;
            }

            final NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(aTarget);

            if (npc != null) {

                final String name = npc.getName();

                if (this.hasIgnoreType(Target.NAMED_NPCS.level) && this.containsIgnore("NPC:" + name)) { return true; }

                else if (hasIgnoreType(Target.NAMED_NPCS.level)) {
                    @SuppressWarnings("deprecation")
                    final String[] groups1 = this.plugin.perms.getPlayerGroups(aTarget.getWorld(), name); // world perms
                    @SuppressWarnings("deprecation")
                    final String[] groups2 = this.plugin.perms.getPlayerGroups((World) null, name); //global perms

                    if (groups1 != null) {
                        for (final String aGroups1 : groups1) {
                            //	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found world1 group " + groups1[i] + " on " + name);
                            if (this.containsIgnore("GROUP:" + aGroups1)) { return true; }
                        }
                    }

                    if (groups2 != null) {
                        for (final String aGroups2 : groups2) {
                            //	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found global group " + groups2[i] + " on " + name);
                            if (this.containsIgnore("GROUP:" + aGroups2)) { return true; }
                        }
                    }
                }
            }
        }

        else if (aTarget instanceof Monster && hasIgnoreType(Target.MONSTERS.level)) { return true; }

        else if (aTarget != null && hasIgnoreType(Target.NAMED_ENTITIES.level)) {
            if (this.containsIgnore("ENTITY:" + aTarget.getType())) { return true; }
        }

        //not ignored, ok!
        return false;
    }
    public boolean isTarget(final LivingEntity aTarget) {

        if (this.targets == 0 || this.targets == Target.EVENTS.level) { return false; }

        if (this.hasTargetType(Target.ALL.level)) { return true; }

        //Check if target
        if (aTarget instanceof Player && !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget)) {

            if (this.hasTargetType(Target.PLAYERS.level)) {
                return true;
            }

            else {
                final OfflinePlayer player = (OfflinePlayer) aTarget;

                if (hasTargetType(Target.NAMED_PLAYERS.level) && this.containsTarget("PLAYER:" + player)) {
                    return true;
                }

                if (this.containsTarget("ENTITY:OWNER") &&
                    player.getUniqueId().equals(this.myNPC.getTrait(Owner.class).getOwnerId())) { return true; }

                if (hasTargetType(Target.NAMED_NPCS.level)) {

                    final String[] groups1 =
                        this.plugin.perms.getPlayerGroups(aTarget.getWorld().getName(), player); // world perms
                    final String[] groups2 = this.plugin.perms.getPlayerGroups(null, player); //global perms

                    if (groups1 != null) {
                        for (final String aGroups1 : groups1) {
                            //			plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found world1 group " + groups1[i] + " on " + name);
                            if (this.containsTarget("GROUP:" + aGroups1)) { return true; }
                        }
                    }

                    if (groups2 != null) {
                        for (final String aGroups2 : groups2) {
                            //	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found global group " + groups2[i] + " on " + name);
                            if (this.containsTarget("GROUP:" + aGroups2)) { return true; }
                        }
                    }
                }

                if (this.hasTargetType(Target.TOWNY.level) || (this.hasTargetType(Target.TOWNY_ENEMIES.level))) {
                    final String[] info = this.plugin.getResidentTownyInfo((Player) aTarget);

                    if (this.hasTargetType(Target.TOWNY.level) && info[1] != null) {
                        if (this.containsTarget("TOWN:" + info[1])) { return true; }
                    }

                    if (info[0] != null) {
                        if (this.hasTargetType(Target.TOWNY.level) && this.containsTarget("NATION:" + info[0])) {
                            return true;
                        }

                        if (this.hasTargetType(Target.TOWNY_ENEMIES.level)) {
                            for (final String s : this._nationsEnemies) {
                                if (this.plugin.isNationEnemy(s, info[0])) { return true; }
                            }
                        }

                    }
                }

                if (this.hasTargetType(Target.FACTION.level) || this.hasTargetType(Target.FACTION_ENEMIES.level)) {
                    if (Sentry.FactionsActive) {
                        final String faction = FactionsUtil.getFactionsTag((Player) aTarget);

                        if (faction != null) {
                            if (this.containsTarget("FACTION:" + faction)) { return true; }

                            if (this.hasTargetType(Target.FACTION_ENEMIES.level)) {
                                for (final String s : this._factionEnemies) {
                                    if (FactionsUtil.isFactionEnemy(getMyEntity().getWorld().getName(), s, faction)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }

                if (this.hasTargetType(Target.WAR.level)) {
                    final String team = this.plugin.getWarTeam((Player) aTarget);
                    //	plugin.getLogger().info(faction);
                    if (team != null) {
                        if (this.containsTarget("WARTEAM:" + team)) { return true; }
                    }
                }
                if (this.hasTargetType(Target.MC_TEAMS.level)) {
                    final String team = this.plugin.getMCTeamName((Player) aTarget);
                    //	plugin.getLogger().info(faction);
                    if (team != null) {
                        if (this.containsTarget("TEAM:" + team)) { return true; }
                    }
                }
                if (this.hasTargetType(Target.CLANS.level)) {
                    final String clan = this.plugin.getClan((Player) aTarget);
                    //	plugin.getLogger().info(faction);
                    if (clan != null) {
                        if (this.containsTarget("CLAN:" + clan)) { return true; }
                    }
                }
            }
        }

        else if (net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget)) {

            if (this.hasTargetType(Target.NPCS.level)) {
                return true;
            }

            final NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(aTarget);

            final String name = npc.getName();

            if (this.hasTargetType(Target.NAMED_NPCS.level) && containsTarget("NPC:" + name)) { return true; }

            if (this.hasTargetType(Target.GROUPS.level)) {
                @SuppressWarnings("deprecation")
                final String[] groups1 = this.plugin.perms.getPlayerGroups(aTarget.getWorld(), name); // world perms
                @SuppressWarnings("deprecation")
                final String[] groups2 = this.plugin.perms.getPlayerGroups((World) null, name); //global perms
                //		String[] groups3 = plugin.perms.getPlayerGroups(aTarget.getWorld().getName(),name); // world perms
                //	String[] groups4 = plugin.perms.getPlayerGroups((Player)aTarget); // world perms

                if (groups1 != null) {
                    for (final String aGroups1 : groups1) {
                        //	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found world1 group " + groups1[i] + " on " + name);
                        if (this.containsTarget("GROUP:" + aGroups1)) { return true; }
                    }
                }

                if (groups2 != null) {
                    for (final String aGroups2 : groups2) {
                        //	plugin.getLogger().log(java.util.logging.Level.INFO , myNPC.getName() + "  found global group " + groups2[i] + " on " + name);
                        if (this.containsTarget("GROUP:" + aGroups2)) { return true; }
                    }
                }
            }
        }
        else if (aTarget instanceof Monster && this.hasTargetType(Target.MONSTERS.level)) { return true; }

        else if (aTarget != null && hasTargetType(Target.NAMED_ENTITIES.level)) {
            if (this.containsTarget("ENTITY:" + aTarget.getType())) { return true; }
        }
        return false;

    }
    public boolean containsIgnore(final String theTarget) {
        return this._ignoreTargets.contains(theTarget.toUpperCase());
    }
    public boolean containsTarget(final String theTarget) {
        return this._validTargets.contains(theTarget.toUpperCase());

    }
    public void deactivate() {
        this.plugin.getServer().getScheduler().cancelTask(this.taskID);
    }
    public void die(final boolean runscripts, final org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        if (this.sentryStatus == Status.DYING || this.sentryStatus == Status.DEAD || getMyEntity() == null) { return; }

        this.sentryStatus = Status.DYING;

        clearTarget();
        //		myNPC.getTrait(Waypoints.class).getCurrentProvider().setPaused(true);

        boolean handled = false;

        if (runscripts && this.plugin.DenizenActive) {
            handled = DenizenHook.SentryDeath(this._myDamagers, this.myNPC);
        }
        if (handled) { return; }

        if (this.plugin.DenizenActive) {
            try {
                Entity killer = getMyEntity().getKiller();
                if (killer == null) {
                    //might have been a projectile.
                    final EntityDamageEvent ev = getMyEntity().getLastDamageCause();
                    if (ev != null && ev instanceof EntityDamageByEntityEvent) {
                        killer = ((EntityDamageByEntityEvent) ev).getDamager();
                    }
                }

                DenizenHook.DenizenAction(this.myNPC, "death", null);
                DenizenHook.DenizenAction(this.myNPC, "death by" + cause.toString().replace(" ", "_"), null);

                if (killer != null) {

                    if (killer instanceof Projectile && ((Projectile) killer).getShooter() != null &&
                        ((Projectile) killer).getShooter() instanceof Entity) {
                        killer = (Entity) ((Projectile) killer).getShooter();
                    }

                    this.plugin.debug(this.myNPC, "Running Denizen actions with killer: " + killer.toString());

                    if (killer instanceof org.bukkit.OfflinePlayer) {
                        DenizenHook.DenizenAction(this.myNPC, "death by player", (org.bukkit.OfflinePlayer) killer);
                    }
                    else {
                        DenizenHook.DenizenAction(this.myNPC, "death by entity", null);
                        DenizenHook.DenizenAction(this.myNPC, "death by " + killer.getType().toString(), null);
                    }

                }

            } catch (final Exception e) {
                e.printStackTrace();
            }

        }

        this.sentryStatus = Status.DEAD;

        if (this.dropInventory) {
            getMyEntity().getLocation().getWorld().spawn(getMyEntity().getLocation(), ExperienceOrb.class)
                         .setExperience(this.plugin.SentryEXP);
        }

        final List<ItemStack> items = new java.util.LinkedList<>();

        if (getMyEntity() instanceof HumanEntity) {
            //get drop inventory.
            for (final ItemStack invItems : ((HumanEntity) getMyEntity()).getInventory().getArmorContents()) {
                if (invItems == null) {
                    continue;
                }
                if (invItems.getType() != Material.AIR) { items.add(invItems); }
            }

            final ItemStack itemInHand = ((HumanEntity) getMyEntity()).getInventory().getItemInHand();
            if (itemInHand.getType() != Material.AIR) { items.add(itemInHand); }

            ((HumanEntity) getMyEntity()).getInventory().clear();
            ((HumanEntity) getMyEntity()).getInventory().setArmorContents(null);
            ((HumanEntity) getMyEntity()).getInventory().setItemInHand(null);
        }

        if (items.isEmpty()) { getMyEntity().playEffect(EntityEffect.DEATH); }
        else { getMyEntity().playEffect(EntityEffect.HURT); }

        if (!this.dropInventory) { items.clear(); }

        for (final ItemStack is : items) {
            getMyEntity().getWorld().dropItemNaturally(getMyEntity().getLocation(), is);
        }

        if (this.plugin.DieLikePlayers) {
            //die!

            getMyEntity().setHealth(0);

        }
        else {
            final org.bukkit.event.entity.EntityDeathEvent ed =
                new org.bukkit.event.entity.EntityDeathEvent(getMyEntity(), items);

            this.plugin.getServer().getPluginManager().callEvent(ed);
            //citizens will despawn it.

        }

        if (this.respawnDelaySeconds == -1) {
            cancelRunnable();
            if (this.isMounted()) { Util.removeMount(this.mountID); }
            this.myNPC.destroy();
        }
        else {
            this.isRespawnable = System.currentTimeMillis() + this.respawnDelaySeconds * 1000;
        }
    }
    private void faceEntity(final Entity from, final Entity at) {

        if (from.getWorld() != at.getWorld()) { return; }
        final Location loc = from.getLocation();

        final double xDiff = at.getLocation().getX() - loc.getX();
        final double yDiff = at.getLocation().getY() - loc.getY();
        final double zDiff = at.getLocation().getZ() - loc.getZ();

        final double distanceXZ = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
        final double distanceY = Math.sqrt(distanceXZ * distanceXZ + yDiff * yDiff);

        double yaw = (Math.acos(xDiff / distanceXZ) * 180 / Math.PI);
        final double pitch = (Math.acos(yDiff / distanceY) * 180 / Math.PI) - 90;
        if (zDiff < 0.0) {
            yaw = yaw + (Math.abs(180 - yaw) * 2);
        }

        net.citizensnpcs.util.NMS.look(from, (float) yaw - 90, (float) pitch);

    }
    private void faceForward() {
        net.citizensnpcs.util.NMS.look(getMyEntity(), getMyEntity().getLocation().getYaw(), 0);
    }
    private void faceAlignWithVehicle() {
        final org.bukkit.entity.Entity v = getMyEntity().getVehicle();
        net.citizensnpcs.util.NMS.look(getMyEntity(), v.getLocation().getYaw(), 0);
    }
    public LivingEntity findTarget(int Range) {
        Range += this.warningRange;
        final List<Entity> entitiesWithinRange = getMyEntity().getNearbyEntities(Range, Range, Range);
        LivingEntity theTarget = null;
        double distanceToBeat = Integer.MAX_VALUE;

        final StringBuilder sb = new StringBuilder();
        entitiesWithinRange.forEach(nb -> sb.append(nb).append(" "));
        this.plugin.debug(this.myNPC, "Potential targets found nearby: " + sb.toString().trim());

        for (final Entity aTarget : entitiesWithinRange) {
            if (!(aTarget instanceof LivingEntity)) { continue; }

            final LivingEntity livingTarget = (LivingEntity) aTarget;

            final boolean isItIgnored = isIgnored(livingTarget);
            final boolean isItATarget = isTarget(livingTarget);

            this.plugin.debug(this.myNPC,
                              "Checking target " + livingTarget + " | target? " + isItATarget + " | ignored? " +
                              isItIgnored);
            // find closest target
            if (!isItIgnored && isItATarget) {
                // can i see it?
                // too dark?
                double ll = aTarget.getLocation().getBlock().getLightLevel();
                // sneaking cut light in half
                if (aTarget instanceof Player) { if (((Player) aTarget).isSneaking()) { ll /= 2; } }

                // too dark?
                final boolean canSeeTarget = ll >= (16 - this.nightVision);
                this.plugin.debug(this.myNPC, "can npc see target " + livingTarget + " in the dark? " + canSeeTarget);
                if (canSeeTarget) {

                    final boolean hasLOS = hasLOS(aTarget);
                    this.plugin.debug(this.myNPC, "can npc see target " + livingTarget + "? " + hasLOS);
                    if (hasLOS) {
                        final double dist = aTarget.getLocation().distance(getMyEntity().getLocation());
                        if (this.warningRange > 0 && this.sentryStatus == Status.LOOKING && aTarget instanceof Player &&
                            dist > (Range - this.warningRange) &&
                            !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget) &
                            !(this.warningMessage.isEmpty())) {

                            if (!this.warnings.containsKey(aTarget.getUniqueId()) ||
                                System.currentTimeMillis() >= this.warnings.get(aTarget.getUniqueId()) + 60 * 1000) {
                                aTarget.sendMessage(getWarningMessage((Player) aTarget));
                                if (!getNavigator().isNavigating()) { faceEntity(getMyEntity(), aTarget); }
                                this.warnings.put(aTarget.getUniqueId(), System.currentTimeMillis());
                            }

                        }
                        else if (dist < distanceToBeat) {
                            // now find closes mob
                            distanceToBeat = dist;
                            theTarget = livingTarget;
                        }
                    }

                }

            }
            else {
                //not a target
                if (this.warningRange > 0 && this.sentryStatus == Status.LOOKING && aTarget instanceof Player &&
                    !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget) &&
                    !(this.greetingMessage.isEmpty())) {
                    final boolean LOS = getMyEntity().hasLineOfSight(aTarget);
                    if (LOS) {
                        if (!this.warnings.containsKey(aTarget.getUniqueId()) ||
                            System.currentTimeMillis() >= this.warnings.get(aTarget.getUniqueId()) + 60 * 1000) {
                            aTarget.sendMessage(getGreetingMessage((Player) aTarget));
                            faceEntity(getMyEntity(), aTarget);
                            this.warnings.put(aTarget.getUniqueId(), System.currentTimeMillis());
                        }
                    }
                }

            }

        }

        if (theTarget != null) {
            // plugin.getServer().broadcastMessage("Targeting: " +
            // theTarget.toString());
            return theTarget;
        }

        return null;
    }
    public void Draw(final boolean on) {
        ((CraftLivingEntity) (getMyEntity())).getHandle().b(on);
    }
    public void Fire(final LivingEntity theEntity) {
        //TODO Wtf are these numbers?
        double v = 34;
        double g = 20;

        Effect effect = null;

        boolean ballistics = true;

        if (this.myProjectile == Arrow.class) {
            effect = Effect.BOW_FIRE;
        }
        else if (this.myProjectile == SmallFireball.class || this.myProjectile == Fireball.class ||
                 this.myProjectile == org.bukkit.entity.WitherSkull.class) {
            effect = Effect.BLAZE_SHOOT;
            ballistics = false;
        }
        else if (this.myProjectile == org.bukkit.entity.ThrownPotion.class) {
            v = 21;
            g = 20;
        }
        else {
            v = 17.75;
            g = 13.5;
        }

        if (this.lightning) {
            ballistics = false;
            effect = null;
        }

        // calc shooting spot.
        final Location loc = Util.getFireSource(getMyEntity(), theEntity);

        Location targetsHeart = theEntity.getLocation();
        targetsHeart = targetsHeart.add(0, .33, 0);

        final Vector test = targetsHeart.clone().subtract(loc).toVector();

        double elev = test.getY();

        final Double testAngle = Util.launchAngle(loc, targetsHeart, v, elev, g);

        if (testAngle == null) {
            // testAngle = Math.atan( ( 2*g*elev + Math.pow(v, 2)) / (2*g*elev +
            // 2*Math.pow(v,2))); //cant hit it where it is, try aiming as far
            // as you can.
            clearTarget();
            // plugin.getServer().broadcastMessage("Can't hit test angle");
            return;
        }

        // plugin.getServer().broadcastMessage("ta " + testAngle.toString());

        final double hangtime = Util.hangtime(testAngle, v, elev, g);
        // plugin.getServer().broadcastMessage("ht " + hangtime.toString());

        final Vector targetVelocity = theEntity.getLocation().subtract(this._projTargetLostLoc).toVector();
        // plugin.getServer().broadcastMessage("tv" + targetVelocity);

        targetVelocity.multiply(20 / this.plugin.LogicTicks);

        final Location to = Util.leadLocation(targetsHeart, targetVelocity, hangtime);
        // plugin.getServer().broadcastMessage("to " + to);
        // Calc range

        Vector victor = to.clone().subtract(loc).toVector();

        final double dist = Math.sqrt(Math.pow(victor.getX(), 2) + Math.pow(victor.getZ(), 2));
        elev = victor.getY();
        if (dist == 0) { return; }

        if (!hasLOS(theEntity)) {
            // target cant be seen..
            clearTarget();
            // plugin.getServer().broadcastMessage("No LoS");
            return;
        }

        // plugin.getServer().broadcastMessage("delta " + victor);

        // plugin.getServer().broadcastMessage("ld " +
        // to.clone().subtract(theEntity.getEyeLocation()));

        if (ballistics) {
            final Double launchAngle = Util.launchAngle(loc, to, v, elev, g);
            if (launchAngle == null) {
                // target cant be hit
                clearTarget();
                // plugin.getServer().broadcastMessage("Can't hit lead");
                return;

            }

            //	plugin.getServer().broadcastMessage(anim.a + " " + anim.b + " " + anim.a() + " " +anim.);
            // Apply angle
            victor.setY(Math.tan(launchAngle) * dist);
            final Vector noise = Vector.getRandom();
            // normalize vector
            victor = Util.normalizeVector(victor);

//            noise = noise.multiply(1 / 10.0);

            // victor = victor.add(noise);

            if (this.myProjectile == Arrow.class || this.myProjectile == org.bukkit.entity.ThrownPotion.class) {
                v += (1.188 * Math.pow(hangtime, 2));
            }
            else {
                v += (.5 * Math.pow(hangtime, 2));
            }

            v += (this.random.nextDouble() - .8) / 2;

            // apply power
            victor = victor.multiply(v / 20.0);

            // Shoot!
            // Projectile theArrow
            // =getMyEntity().launchProjectile(myProjectile);

        }
        else {
            if (dist > this.sentryRange) {
                // target cant be hit
                clearTarget();
                // plugin.getServer().broadcastMessage("Can't hit lead");
                return;

            }
        }

        if (this.lightning) {
            if (this.lightningLevel == 2) {
                to.getWorld().strikeLightning(to);
            }
            else if (this.lightningLevel == 1) {
                to.getWorld().strikeLightningEffect(to);
                theEntity.damage(getStrength(), getMyEntity());
            }
            else if (this.lightningLevel == 3) {
                to.getWorld().strikeLightningEffect(to);
                theEntity.setHealth(0);
            }
        }
        else {

            final Projectile theArrow;

            if (this.myProjectile == org.bukkit.entity.ThrownPotion.class) {
                final net.minecraft.server.v1_8_R3.World nmsWorld = ((CraftWorld) getMyEntity().getWorld()).getHandle();
                final EntityPotion ent = new EntityPotion(nmsWorld, loc.getX(), loc.getY(), loc.getZ(),
                                                          CraftItemStack.asNMSCopy(this.potionType));
                nmsWorld.addEntity(ent);
                theArrow = (Projectile) ent.getBukkitEntity();

            }

            else if (this.myProjectile == org.bukkit.entity.EnderPearl.class) {
                theArrow = getMyEntity().launchProjectile(this.myProjectile);
            }

            else {
                theArrow = getMyEntity().getWorld().spawn(loc, this.myProjectile);
            }

            if (this.myProjectile == Fireball.class || this.myProjectile == org.bukkit.entity.WitherSkull.class) {
                //TODO find an explanation for this magic number
                victor = victor.multiply(1 / 1000000000);
            }
            else if (this.myProjectile == SmallFireball.class) {
                //TODO find an explanation for this magic number
                victor = victor.multiply(1 / 1000000000);
                ((SmallFireball) theArrow).setIsIncendiary(this.incendiary);
                if (!this.incendiary) {
                    theArrow.setFireTicks(0);
                    ((SmallFireball) theArrow).setYield(0);
                }
            }
            else if (this.myProjectile == org.bukkit.entity.EnderPearl.class) {
                this.epcount++;
                if (this.epcount > Integer.MAX_VALUE - 1) { this.epcount = 0; }
                this.plugin.debug(this.epcount + "");
            }

            this.plugin.arrows.add(theArrow);
            theArrow.setShooter(getMyEntity());
            theArrow.setVelocity(victor);
        }

        // OK we're shooting
        // go twang
        if (effect != null) { getMyEntity().getWorld().playEffect(getMyEntity().getLocation(), effect, null); }

        if (this.myProjectile == Arrow.class) {
            Draw(false);
        }
        else {
            if (getMyEntity() instanceof org.bukkit.entity.Player) {
                net.citizensnpcs.util.PlayerAnimation.ARM_SWING.play((Player) getMyEntity(), 64);
            }
        }

    }
    public int getArmor() {

        double mod = 0;
        if (getMyEntity() instanceof Player) {
            for (final ItemStack armourItems : ((Player) getMyEntity()).getInventory().getArmorContents()) {
                if (armourItems == null) {
                    continue;
                }
                if (this.plugin.ArmorBuffs.containsKey(armourItems.getType())) {
                    mod += this.plugin.ArmorBuffs.get(armourItems.getType());
                }
            }
        }

        return (int) (this.armor + mod);
    }
    public void setArmor(final int armor) {
        this.armor = armor;
    }
    String getGreetingMessage(final Player player) {
        final String str =
            this.greetingMessage.replace("<NPC>", this.myNPC.getName()).replace("<PLAYER>", player.getName());
        return ChatColor.translateAlternateColorCodes('&', str);
    }
    public String getGuardTarget() {
        return this.guardTarget;
    }
    public void setGuardTarget(final String guardTarget) {
        this.guardTarget = guardTarget;
    }
    public double getHealth() {
        if (this.myNPC == null) { return 0; }
        if (getMyEntity() == null) { return 0; }
        return getMyEntity().getHealth();
    }
    public void setHealth(double health) {
        if (this.myNPC == null) { return; }
        if (getMyEntity() == null) { return; }
        if (getMyEntity().getMaxHealth() != this.sentryHealth) { getMyEntity().setMaxHealth(this.sentryHealth); }
        if (health > this.sentryHealth) { health = this.sentryHealth; }

        getMyEntity().setHealth(health);
    }
    public float getSpeed() {
        if (!this.myNPC.isSpawned()) { return this.sentrySpeed; }
        double mod = 0;
        if (getMyEntity() instanceof Player) {
            for (final ItemStack armourItems : ((Player) getMyEntity()).getInventory().getArmorContents()) {
                if (armourItems == null) {
                    continue;
                }
                if (this.plugin.SpeedBuffs.containsKey(armourItems.getType())) {
                    mod += this.plugin.SpeedBuffs.get(armourItems.getType());
                }
            }
        }
        return (float) (this.sentrySpeed + mod) * (this.getMyEntity().isInsideVehicle() ? 2 : 1);
    }
    public String getStats() {
        final DecimalFormat df = new DecimalFormat("#.0");
        final DecimalFormat df2 = new DecimalFormat("#.##");
        final double h = getHealth();

        return ChatColor.RED + "[HP]:" + ChatColor.WHITE + h + "/" + this.sentryHealth + ChatColor.RED + " [AP]:" +
               ChatColor.WHITE + getArmor() + ChatColor.RED + " [STR]:" + ChatColor.WHITE + getStrength() +
               ChatColor.RED + " [SPD]:" + ChatColor.WHITE + df.format(getSpeed()) + ChatColor.RED + " [RNG]:" +
               ChatColor.WHITE + this.sentryRange + ChatColor.RED + " [ATK]:" + ChatColor.WHITE +
               this.attackRateSeconds + ChatColor.RED + " [VIS]:" + ChatColor.WHITE + this.nightVision + ChatColor.RED +
               " [HEAL]:" + ChatColor.WHITE + this.healRate + ChatColor.RED + " [WARN]:" + ChatColor.WHITE +
               this.warningRange + ChatColor.RED + " [FOL]:" + ChatColor.WHITE +
               df2.format(Math.sqrt(this.followDistance));

    }
    public int getStrength() {
        double mod = 0;

        if (getMyEntity() instanceof Player) {
            if (this.plugin.StrengthBuffs
                .containsKey(((Player) getMyEntity()).getInventory().getItemInHand().getType())) {
                mod += this.plugin.StrengthBuffs.get(((Player) getMyEntity()).getInventory().getItemInHand().getType());
            }
        }

        return (int) (this.strength + mod);
    }
    public void setStrength(final int strength) {
        this.strength = strength;
    }
    private String getWarningMessage(final Player player) {
        final String str =
            this.warningMessage.replace("<NPC>", this.myNPC.getName()).replace("<PLAYER>", player.getName());
        return ChatColor.translateAlternateColorCodes('&', str);

    }

    void initialize() {

        // plugin.getServer().broadcastMessage("NPC " + npc.getName() +
        // " INITIALIZING!");

        // check for illegal values

        if (this.sentryWeight <= 0) { this.sentryWeight = 1.0; }

        if (this.attackRateSeconds > 30) { this.attackRateSeconds = 30.0; }

        if (this.sentryHealth < 0) { this.sentryHealth = 0; }

        if (this.sentryRange < 1) { this.sentryRange = 1; }

        if (this.sentryRange > 200) { this.sentryRange = 200; }

        if (this.sentryWeight <= 0) { this.sentryWeight = 1.0; }

        if (this.respawnDelaySeconds < -1) { this.respawnDelaySeconds = -1; }

        if (this.Spawn == null) {
            this.Spawn = getMyEntity().getLocation();
        }

        if (this.plugin.DenizenActive) {
            if (this.myNPC.hasTrait(net.aufdemrand.denizen.npc.traits.HealthTrait.class)) {
                this.myNPC.removeTrait(net.aufdemrand.denizen.npc.traits.HealthTrait.class);
            }
        }

        //disable citizens respawning. Cause Sentry doesn't always raise EntityDeath
        this.myNPC.data().set("respawn-delay", -1);

        setHealth(this.sentryHealth);

        this._myDamagers.clear();

        this.sentryStatus = Status.LOOKING;
        faceForward();

        this.healAnimation = new PacketPlayOutAnimation(((CraftEntity) getMyEntity()).getHandle(), 6);

        if (this.guardTarget == null) {
            this.myNPC.teleport(this.Spawn,
                                TeleportCause.PLUGIN); //it should be there... but maybe not if the position was saved elsewhere.
        }

        float pf = this.myNPC.getNavigator().getDefaultParameters().range();

        if (pf < this.sentryRange + 5) {
            pf = this.sentryRange + 5;
        }

        this.myNPC.data().set(NPC.DEFAULT_PROTECTED_METADATA, false);
        this.myNPC.data().set(NPC.TARGETABLE_METADATA, this.targetable);

        this.myNPC.getNavigator().getDefaultParameters().range(pf);
        this.myNPC.getNavigator().getDefaultParameters().stationaryTicks(5 * 20); // so 100?
        this.myNPC.getNavigator().getDefaultParameters().useNewPathfinder(false);
        //	myNPC.getNavigator().getDefaultParameters().stuckAction(new BodyguardTeleportStuckAction(this, this.plugin));

        // plugin.getServer().broadcastMessage("NPC GUARDING!");

        if (getMyEntity() instanceof org.bukkit.entity.Creeper) {
            this.myNPC.getNavigator().getDefaultParameters().attackStrategy(new CreeperAttackStrategy());
        }
        else if (getMyEntity() instanceof org.bukkit.entity.Spider) {
            this.myNPC.getNavigator().getDefaultParameters().attackStrategy(new SpiderAttackStrategy(this.plugin));
        }

        processTargets();

        if (this.taskID == -1) {
            final int delay = 40 + this.random.nextInt(60); //Max 5 sec delay
            this.plugin.debug(this.myNPC, "Starting logic ticking in " + delay + " ticks");
            this.taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, new SentryLogic(), delay,
                                                                          this.plugin.LogicTicks);
        }

        this.mountCreated = false;
    }
    public boolean isPyromancer() {
        return (this.myProjectile == Fireball.class || this.myProjectile == SmallFireball.class);
    }
    public boolean isPyromancer1() {
        return (!this.incendiary && this.myProjectile == SmallFireball.class);
    }
    public boolean isPyromancer2() {
        return (this.incendiary && this.myProjectile == SmallFireball.class);
    }
    public boolean isPyromancer3() {
        return (this.myProjectile == Fireball.class);
    }
    public boolean isStormcaller() {
        return (this.lightning);
    }
    public boolean isWarlock1() {
        return (this.myProjectile == org.bukkit.entity.EnderPearl.class);
    }
    public boolean isWitchDoctor() {
        return (this.myProjectile == org.bukkit.entity.ThrownPotion.class);
    }
    public void onDamage(final EntityDamageByEntityEvent event) {

        if (this.sentryStatus == Status.DYING) { return; }

        if (this.myNPC == null || !this.myNPC.isSpawned()) {
            // how did you get here?
            return;
        }

        if (this.guardTarget != null && this.guardEntity == null) {
            return; //don't take damage when bodyguard target isnt around.
        }

        if (System.currentTimeMillis() < this.okToTakeDamage + 500) { return; }
        this.okToTakeDamage = System.currentTimeMillis();

        event.getEntity().setLastDamageCause(event);

        final NPC npc = this.myNPC;

        LivingEntity attacker = null;

        HitType hit = HitType.NORMAL;

        double finalDamage = event.getDamage();

        // Find the attacker
        if (event.getDamager() instanceof Projectile) {
            if (((Projectile) event.getDamager()).getShooter() instanceof LivingEntity) {
                attacker = (LivingEntity) ((Projectile) event.getDamager()).getShooter();
            }
        }
        else if (event.getDamager() instanceof LivingEntity) {
            attacker = (LivingEntity) event.getDamager();
        }

        if (this.invincible) { return; }

        if (this.plugin.IgnoreListInvincibility) {

            if (isIgnored(attacker)) { return; }
        }

        // can i kill it? lets go kill it.
        if (attacker != null) {
            if (this.retaliate) {
                if (!(event.getDamager() instanceof Projectile) ||
                    (net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(attacker) == null)) {
                    // only retaliate to players or non-projectiles. Prevents stray sentry arrows from causing retaliation.
                    setTarget(attacker, true);
                }
            }
        }

        if (this.luckyHits) {
            // Calculate critical
            double damageModifier = event.getDamage();

            final int luckyHit = this.random.nextInt(100);

            if (luckyHit < this.plugin.Crit3Chance) {
                damageModifier = damageModifier * 2.00;
                hit = HitType.DISEMBOWEL;
            }
            else if (luckyHit < this.plugin.Crit3Chance + this.plugin.Crit2Chance) {
                damageModifier = damageModifier * 1.75;
                hit = HitType.MAIN;
            }
            else if (luckyHit < this.plugin.Crit3Chance + this.plugin.Crit2Chance + this.plugin.Crit1Chance) {
                damageModifier = damageModifier * 1.50;
                hit = HitType.INJURE;
            }
            else if (luckyHit < this.plugin.Crit3Chance + this.plugin.Crit2Chance + this.plugin.Crit1Chance +
                                this.plugin.GlanceChance) {
                damageModifier = damageModifier * 0.50;
                hit = HitType.GLANCE;
            }
            else if (luckyHit < this.plugin.Crit3Chance + this.plugin.Crit2Chance + this.plugin.Crit1Chance +
                                this.plugin.GlanceChance + this.plugin.MissChance) {
                damageModifier = 0;
                hit = HitType.MISS;
            }

            finalDamage = Math.round(damageModifier);
        }

        final int arm = getArmor();

        if (finalDamage > 0) {

            if (attacker != null) {
                // knockback
                npc.getEntity()
                   .setVelocity(attacker.getLocation().getDirection().multiply(1.0 / (this.sentryWeight + (arm / 5))));
            }

            // Apply armor
            finalDamage -= arm;

            // there was damage before armor.
            if (finalDamage <= 0) {
                npc.getEntity().getWorld()
                   .playEffect(npc.getEntity().getLocation(), org.bukkit.Effect.ZOMBIE_CHEW_IRON_DOOR, 1);
                hit = HitType.BLOCK;
            }
        }

        if (attacker instanceof Player && !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(attacker)) {

            this._myDamagers.add((Player) attacker);
            String msg = null;
            // Messages
            switch (hit) {
                case NORMAL:
                    msg = this.plugin.HitMessage;
                    break;
                case MISS:
                    msg = this.plugin.MissMessage;
                    break;
                case BLOCK:
                    msg = this.plugin.BlockMessage;
                    break;
                case MAIN:
                    msg = this.plugin.Crit2Message;
                    break;
                case DISEMBOWEL:
                    msg = this.plugin.Crit3Message;
                    break;
                case INJURE:
                    msg = this.plugin.Crit1Message;
                    break;
                case GLANCE:
                    msg = this.plugin.GlanceMessage;
                    break;
            }

            if (msg != null && !msg.isEmpty()) {
                attacker.sendMessage(
                    Util.format(msg, npc, attacker, ((Player) attacker).getItemInHand().getType(), finalDamage + ""));
            }
        }

        if (finalDamage > 0) {
            npc.getEntity().playEffect(EntityEffect.HURT);

            // is he dead?
            if (getHealth() - finalDamage <= 0) {

                //set the killer
                if (event.getDamager() instanceof HumanEntity) {
                    ((CraftLivingEntity) getMyEntity()).getHandle().killer =
                        (EntityHuman) ((CraftLivingEntity) event.getDamager()).getHandle();
                }

                die(true, event.getCause());

            }
            else { getMyEntity().damage(finalDamage); }
        }
    }
    //called from SentryInstance no @EventHandler should be here
    void onEnvironmentDamage(final EntityDamageEvent event) {

        if (this.sentryStatus == Status.DYING) { return; }

        if (!this.myNPC.isSpawned() || this.invincible) {
            return;
        }

        if (this.guardTarget != null && this.guardEntity == null) {
            return; //don't take damage when bodyguard target isn't around.
        }

        if (System.currentTimeMillis() < this.okToTakeDamage + 500) { return; }
        this.okToTakeDamage = System.currentTimeMillis();

        getMyEntity().setLastDamageCause(event);

        double finalDamage = event.getDamage();

        if (event.getCause() == DamageCause.CONTACT || event.getCause() == DamageCause.BLOCK_EXPLOSION) {
            finalDamage -= getArmor();
        }

        if (finalDamage > 0) {
            getMyEntity().playEffect(EntityEffect.HURT);

            if (event.getCause() == DamageCause.FIRE) {
                if (!getNavigator().isNavigating()) {
                    getNavigator().setTarget(
                        getMyEntity().getLocation().add(this.random.nextInt(2) - 1, 0, this.random.nextInt(2) - 1));
                }
            }

            if (getHealth() - finalDamage <= 0) {

                die(true, event.getCause());

                // plugin.getServer().broadcastMessage("Dead!");
            }
            else {
                getMyEntity().damage(finalDamage);

            }
        }

    }
    public void processTargets() {
        try {
            this.targets = 0;
            this.ignores = 0;
            this._ignoreTargets.clear();
            this._validTargets.clear();
            this._nationsEnemies.clear();
            this._factionEnemies.clear();

            for (final String t : this.validTargets) {
                if (t.contains("ENTITY:ALL")) { this.targets |= Target.ALL.level; }
                else if (t.contains("ENTITY:MONSTER")) { this.targets |= Target.MONSTERS.level; }
                else if (t.contains("ENTITY:PLAYER")) { this.targets |= Target.PLAYERS.level; }
                else if (t.contains("ENTITY:NPC")) { this.targets |= Target.NPCS.level; }
                else {
                    this._validTargets.add(t);
                    if (t.contains("NPC:")) { this.targets |= Target.NAMED_NPCS.level; }
                    else if (this.plugin.perms != null && this.plugin.perms.isEnabled() && t.contains("GROUP:")) {
                        this.targets |= Target.NAMED_NPCS.level;
                    }
                    else if (t.contains("EVENT:")) { this.targets |= Target.EVENTS.level; }
                    else if (t.contains("PLAYER:")) { this.targets |= Target.NAMED_PLAYERS.level; }
                    else if (t.contains("ENTITY:")) { this.targets |= Target.NAMED_ENTITIES.level; }
                    else if (Sentry.FactionsActive && t.contains("FACTION:")) { this.targets |= Target.FACTION.level; }
                    else if (Sentry.FactionsActive && t.contains("FACTIONENEMIES:")) {
                        this.targets |= Target.FACTION_ENEMIES.level;
                        this._factionEnemies.add(t.split(":")[1]);
                    }
                    else if (this.plugin.TownyActive && t.contains("TOWN:")) { this.targets |= Target.TOWNY.level; }
                    else if (this.plugin.TownyActive && t.contains("NATIONENEMIES:")) {
                        this.targets |= Target.TOWNY_ENEMIES.level;
                        this._nationsEnemies.add(t.split(":")[1]);
                    }
                    else if (this.plugin.TownyActive && t.contains("NATION:")) { this.targets |= Target.TOWNY.level; }
                    else if (this.plugin.WarActive && t.contains("WARTEAM:")) { this.targets |= Target.WAR.level; }
                    else if (t.contains("TEAM:")) { this.targets |= Target.MC_TEAMS.level; }
                    else if (this.plugin.ClansActive && t.contains("CLAN:")) { this.targets |= Target.CLANS.level; }
                }
            }
            for (final String t : this.ignoreTargets) {
                if (t.contains("ENTITY:ALL")) { this.ignores |= Target.ALL.level; }
                else if (t.contains("ENTITY:MONSTER")) { this.ignores |= Target.MONSTERS.level; }
                else if (t.contains("ENTITY:PLAYER")) { this.ignores |= Target.PLAYERS.level; }
                else if (t.contains("ENTITY:NPC")) { this.ignores |= Target.NPCS.level; }
                else if (t.contains("ENTITY:OWNER")) { this.ignores |= Target.OWNER.level; }
                else {
                    this._ignoreTargets.add(t);
                    if (this.plugin.perms != null && this.plugin.perms.isEnabled() && t.contains("GROUP:")) {
                        this.ignores |= Target.NAMED_NPCS.level;
                    }
                    else if (t.contains("NPC:")) { this.ignores |= Target.NAMED_NPCS.level; }
                    else if (t.contains("PLAYER:")) { this.ignores |= Target.NAMED_PLAYERS.level; }
                    else if (t.contains("ENTITY:")) { this.ignores |= Target.NAMED_ENTITIES.level; }
                    else if (Sentry.FactionsActive && t.contains("FACTION:")) { this.ignores |= Target.FACTION.level; }
                    else if (this.plugin.TownyActive && t.contains("TOWN:")) { this.ignores |= Target.TOWNY.level; }
                    else if (this.plugin.TownyActive && t.contains("NATION:")) { this.ignores |= Target.TOWNY.level; }
                    else if (this.plugin.WarActive && t.contains("TEAM:")) { this.ignores |= Target.WAR.level; }
                    else if (this.plugin.ClansActive && t.contains("CLAN:")) { this.ignores |= Target.CLANS.level; }
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

    }
    private boolean isMyChunkLoaded() {
        if (getMyEntity() == null) { return false; }
        final Location npcLoc = getMyEntity().getLocation();
        return npcLoc.getWorld().isChunkLoaded(npcLoc.getBlockX() >> 4, npcLoc.getBlockZ() >> 4);
    }
    public boolean setGuardTarget(final LivingEntity entity, final boolean forcePlayer) {

        if (this.myNPC == null) { return false; }

        if (entity == null) {
            this.guardEntity = null;
            this.guardTarget = null;
            clearTarget(); // clear active hostile target
            return true;
        }

        if (!forcePlayer) {

            final List<Entity> EntitiesWithinRange =
                getMyEntity().getNearbyEntities(this.sentryRange, this.sentryRange, this.sentryRange);

            for (final Entity aTarget : EntitiesWithinRange) {

                if (aTarget instanceof Player) {
                    //chesk for players
                    if (aTarget.getUniqueId().equals(entity.getUniqueId())) {
                        this.guardEntity = (LivingEntity) aTarget;
                        this.guardTarget = aTarget.getName();
                        clearTarget(); // clear active hostile target
                        return true;
                    }
                }
                else if (aTarget instanceof LivingEntity) {
                    //check for named mobs.
                    final LivingEntity ename = (LivingEntity) aTarget;
                    if (ename.getUniqueId().equals(entity.getUniqueId())) {
                        this.guardEntity = (LivingEntity) aTarget;
                        this.guardTarget = ename.getCustomName();
                        clearTarget(); // clear active hostile target
                        return true;
                    }
                }

            }
        }
        else {

            for (final Player loopPlayer : this.plugin.getServer().getOnlinePlayers()) {
                if (loopPlayer.getUniqueId().equals(entity.getUniqueId())) {
                    this.guardEntity = loopPlayer;
                    this.guardTarget = loopPlayer.getName();
                    clearTarget(); // clear active hostile target
                    return true;
                }

            }

        }

        return false;

    }
    public boolean UpdateWeapon() {
        Material weapon = Material.AIR;

        ItemStack is = null;

        if (getMyEntity() instanceof HumanEntity) {
            is = ((HumanEntity) getMyEntity()).getInventory().getItemInHand();
            weapon = is.getType();
            if (weapon != this.plugin.witchdoctor) { is.setDurability((short) 0); }
        }

        this.lightning = false;
        this.lightningLevel = 0;
        this.incendiary = false;
        this.potionEffects = this.plugin.WeaponEffects.get(weapon);

        this.myProjectile = null;

        if (weapon == this.plugin.archer || getMyEntity() instanceof org.bukkit.entity.Skeleton) {
            this.myProjectile = org.bukkit.entity.Arrow.class;
        }
        else if (weapon == this.plugin.pyro3 || getMyEntity() instanceof org.bukkit.entity.Ghast) {
            this.myProjectile = org.bukkit.entity.Fireball.class;
        }
        else if (weapon == this.plugin.pyro2 || getMyEntity() instanceof org.bukkit.entity.Blaze ||
                 getMyEntity() instanceof org.bukkit.entity.EnderDragon) {
            this.myProjectile = org.bukkit.entity.SmallFireball.class;
            this.incendiary = true;
        }
        else if (weapon == this.plugin.pyro1) {
            this.myProjectile = org.bukkit.entity.SmallFireball.class;
            this.incendiary = false;
        }
        else if (weapon == this.plugin.magi || getMyEntity() instanceof org.bukkit.entity.Snowman) {
            this.myProjectile = org.bukkit.entity.Snowball.class;
        }
        else if (weapon == this.plugin.warlock1) {
            this.myProjectile = org.bukkit.entity.EnderPearl.class;
        }
        else if (weapon == this.plugin.warlock2 || getMyEntity() instanceof org.bukkit.entity.Wither) {
            this.myProjectile = org.bukkit.entity.WitherSkull.class;
        }
        else if (weapon == this.plugin.warlock3) {
            this.myProjectile = org.bukkit.entity.WitherSkull.class;
        }
        else if (weapon == this.plugin.bombardier) {
            this.myProjectile = org.bukkit.entity.Egg.class;
        }
        else if (weapon == this.plugin.witchdoctor || getMyEntity() instanceof org.bukkit.entity.Witch) {
            if (is == null) {
                is = new ItemStack(Material.POTION, 1, (short) 16396);
            }
            this.myProjectile = org.bukkit.entity.ThrownPotion.class;
            this.potionType = is;
        }
        else if (weapon == this.plugin.sc1) {
            this.myProjectile = org.bukkit.entity.ThrownPotion.class;
            this.lightning = true;
            this.lightningLevel = 1;
        }
        else if (weapon == this.plugin.sc2) {
            this.myProjectile = org.bukkit.entity.ThrownPotion.class;
            this.lightning = true;
            this.lightningLevel = 2;
        }
        else if (weapon == this.plugin.sc3) {
            this.myProjectile = org.bukkit.entity.ThrownPotion.class;
            this.lightning = true;
            this.lightningLevel = 3;
        }
        else {
            return false; //melee
        }

        return true; //ranged
    }
    public void clearTarget() {
        this.plugin.debug(this.myNPC, "Removing npcs target");
        // this gets called while npc is dead, reset things.
        this.sentryStatus = Status.LOOKING;
        this.projectileTarget = null;
        this.meleeTarget = null;
        this._projTargetLostLoc = null;
    }
    public void setTarget(LivingEntity theEntity, final boolean isRetaliating) {
        Preconditions
            .checkNotNull(theEntity, "Cannot set a target to null! use clearTarget() to remove the target of the npc.");


        /* Ignore this call if the target is already the melee target or the projectile target */
        if (theEntity == this.meleeTarget || theEntity == this.projectileTarget) {
            //But update the status of this npc
            if (isRetaliating) { this.sentryStatus = Status.RETALIATING; }
            else { this.sentryStatus = Status.HOSTILE; }
            return;
        }

        /* Okay let's try an explain this...
         * We must be able to get the entity of this npc (eg the npc must not be null and spawned)
         * The target cannot be the entity it's guarding
         * The target cannot be it self
         */
        if (getMyEntity() == null || !this.myNPC.isSpawned() || theEntity == this.guardEntity ||
            theEntity == getMyEntity() || theEntity == this.meleeTarget || theEntity == this.projectileTarget) {
            return;
        }

        if (this.guardTarget != null && this.guardEntity == null) {
            theEntity = null; //don't go aggro when bodyguard target isn't around.
        }

        if (theEntity == null) {
            // no hostile target
            Draw(false);

            //		plugin.getServer().broadcastMessage(myNPC.getNavigator().getTargetAsLocation().toString());
            //plugin.getServer().broadcastMessage(((Boolean)myNPC.getTrait(Waypoints.class).getCurrentProvider().isPaused()).toString());

            if (this.guardEntity != null) {
                // yarr... im a guarrrd.

                getGoalController().setPaused(true);
                //	if (!myNPC.getTrait(Waypoints.class).getCurrentProvider().isPaused())  myNPC.getTrait(Waypoints.class).getCurrentProvider().setPaused(true);

                if (getNavigator().getEntityTarget() == null || (getNavigator().getEntityTarget() != null &&
                                                                 getNavigator().getEntityTarget().getTarget() !=
                                                                 this.guardEntity)) {

                    if (this.guardEntity.getLocation().getWorld() != getMyEntity().getLocation().getWorld()) {
                        this.myNPC.despawn();
                        this.myNPC.spawn((this.guardEntity.getLocation().add(1, 0, 1)));
                        return;
                    }

                    getNavigator().setTarget(this.guardEntity, false);
                    //		myNPC.getNavigator().getLocalParameters().stuckAction(bgteleport);
                    getNavigator().getLocalParameters().stationaryTicks(3 * 20);
                }
            }
            else {
                //not a guard
                getNavigator().cancelNavigation();

                faceForward();
                if (getGoalController().isPaused()) { getGoalController().setPaused(false); }
            }
            return;
        }


        //Now we're targeting!!
        final SentryTargetEntityEvent targetEntityEvent =
            new SentryTargetEntityEvent(this.myNPC, theEntity, isRetaliating);
        Bukkit.getServer().getPluginManager().callEvent(targetEntityEvent);
        if (targetEntityEvent.isCancelled()) {
            return;
        }

        if (!getNavigator().isNavigating()) { faceEntity(getMyEntity(), theEntity); }

        if (isRetaliating) { this.sentryStatus = Status.RETALIATING; }
        else { this.sentryStatus = Status.HOSTILE; }

        if (UpdateWeapon()) {
            //ranged
            this.plugin.debug(this.myNPC, "Set Target projectile");
            this.projectileTarget = theEntity;
            this.meleeTarget = null;
        }
        else {
            //melee
            // Manual Attack
            this.plugin.debug(this.myNPC, "Set Target melee");
            this.meleeTarget = theEntity;
            this.projectileTarget = null;
            if (getNavigator().getEntityTarget() != null && getNavigator().getEntityTarget().getTarget() == theEntity) {
                return; //already attacking this, dummy.
            }
            if (!getGoalController().isPaused()) { getGoalController().setPaused(true); }
            getNavigator().setTarget(theEntity, true);
            getNavigator().getLocalParameters().speedModifier(getSpeed());
            getNavigator().getLocalParameters().stuckAction(this.giveUp);
            getNavigator().getLocalParameters().stationaryTicks(5 * 20);
        }
    }
    protected net.citizensnpcs.api.ai.Navigator getNavigator() {
        NPC npc = getMountNPC();
        if (npc == null || !npc.isSpawned()) { npc = this.myNPC; }
        return npc.getNavigator();
    }
    protected net.citizensnpcs.api.ai.GoalController getGoalController() {
        NPC npc = getMountNPC();
        if (npc == null || !npc.isSpawned()) { npc = this.myNPC; }
        return npc.getDefaultGoalController();
    }
    public void dismount() {
        //get off and despawn the horse.
        if (this.myNPC.isSpawned()) {
            if (getMyEntity().isInsideVehicle()) {
                final NPC n = getMountNPC();
                if (n != null) {
                    getMyEntity().getVehicle().setPassenger(null);
                    n.despawn(net.citizensnpcs.api.event.DespawnReason.PLUGIN);
                }
            }
        }
    }
    public void mount() {
        if (this.myNPC.isSpawned()) {
            if (getMyEntity().isInsideVehicle()) { getMyEntity().getVehicle().setPassenger(null); }
            NPC n = getMountNPC();

            if (n == null || (!n.isSpawned() && !this.mountCreated)) {
                n = createMount();
            }

            if (n != null) {
                this.mountCreated = true;
                if (!n.isSpawned()) {
                    return; //dead mount
                }
                n.data().set(NPC.DEFAULT_PROTECTED_METADATA, false);
                n.getNavigator().getDefaultParameters().attackStrategy(new MountAttackStrategy());
                n.getNavigator().getDefaultParameters().useNewPathfinder(false);
                n.getNavigator().getDefaultParameters()
                 .speedModifier(this.myNPC.getNavigator().getDefaultParameters().speedModifier() * 2);
                n.getNavigator().getDefaultParameters()
                 .range(this.myNPC.getNavigator().getDefaultParameters().range() + 5);
                n.getEntity().setCustomNameVisible(false);
                n.getEntity().setPassenger(null);
                n.getEntity().setPassenger(getMyEntity());
            }
            else { this.mountID = -1; }

        }
    }
    public NPC createMount() {
        this.plugin.debug(this.myNPC, "Creating mount");

        if (this.myNPC.isSpawned()) {

            final NPC horseNPC;

            if (isMounted()) {
                horseNPC = CitizensAPI.getNPCRegistry().getById(this.mountID);

                if (horseNPC != null) {
                    horseNPC.despawn();
                }
                else {
                    this.plugin.getServer().getLogger().info("Cannot find mount NPC " + this.mountID);
                }
            }

            else {
                horseNPC = net.citizensnpcs.api.CitizensAPI.getNPCRegistry()
                                                           .createNPC(org.bukkit.entity.EntityType.HORSE,
                                                                      this.myNPC.getName() + "_Mount");
                horseNPC.getTrait(MobType.class).setType(org.bukkit.entity.EntityType.HORSE);
            }

            if (horseNPC == null) {
                this.plugin.getServer().getLogger().info("Cannot create mount NPC!");
            }

            if (getMyEntity() == null) {
                this.plugin.getServer().getLogger().info("why is this spawned but bukkit entity is null???");
            }

            //look at my horse, my horse is amazing.
            if (horseNPC != null) {
                horseNPC.spawn(getMyEntity().getLocation());
                final Owner o = horseNPC.getTrait(Owner.class);
                o.setOwner(this.myNPC.getTrait(Owner.class).getOwner());
                //cant do this is screws up the pathfinding.
                ((Horse) horseNPC.getEntity()).getInventory().setSaddle(new ItemStack(org.bukkit.Material.SADDLE));

                this.mountID = horseNPC.getId();
            }

            return horseNPC;

        }

        return null;
    }
    public boolean hasLOS(final Entity other) {
        return this.myNPC.isSpawned() && (this.ignoreLOS || getMyEntity().hasLineOfSight(other));
    }
    public LivingEntity getMyEntity() {
//        if (this.myNPC == null) { return null; }
//        if (this.myNPC.getEntity() == null) { return null; }
//        if (this.myNPC.getEntity().isDead()) { return null; }
//        if (!(this.myNPC.getEntity() instanceof LivingEntity)) {
//             this.plugin.getServer().getLogger()
//                       .info("Sentry " + this.myNPC.getName() + " is not a living entity! Errors inbound....");
//            return null;
//        }
//        return (LivingEntity) this.myNPC.getEntity();

        try {
            final Entity entity = this.myNPC.getEntity();
            return entity.isDead() ? null : (LivingEntity) this.myNPC.getEntity();
        } catch (final NullPointerException | ClassCastException ex) {
            // Ignore
        }
        return null;
    }
    protected NPC getMountNPC() {
        if (this.isMounted() && net.citizensnpcs.api.CitizensAPI.hasImplementation()) {

            return net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(this.mountID);

        }
        return null;
    }
    public int getSentryRange() {
        return this.sentryRange;
    }
    public void setSentryRange(final int sentryRange) {
        this.sentryRange = sentryRange;
    }
    public int getNightVision() {
        return this.nightVision;
    }
    public void setNightVision(final int nightVision) {
        this.nightVision = nightVision;
    }
    public int getFollowDistance() {
        return this.followDistance;
    }
    public void setFollowDistance(final int followDistance) {
        this.followDistance = followDistance;
    }
    public int getWarningRange() {
        return this.warningRange;
    }
    public void setWarningRange(final int warningRange) {
        this.warningRange = warningRange;
    }
    public int getRespawnDelaySeconds() {
        return this.respawnDelaySeconds;
    }
    public void setRespawnDelaySeconds(final int respawnDelaySeconds) {
        this.respawnDelaySeconds = respawnDelaySeconds;
    }
    public double getHealRate() {
        return this.healRate;
    }
    public void setHealRate(final double healRate) {
        this.healRate = healRate;
    }
    public double getAttackRateSeconds() {
        return this.attackRateSeconds;
    }
    public void setAttackRateSeconds(final double attackRateSeconds) {
        this.attackRateSeconds = attackRateSeconds;
    }
    public double getSentryHealth() {
        return this.sentryHealth;
    }
    public void setSentryHealth(final double sentryHealth) {
        this.sentryHealth = sentryHealth;
    }
    public double getSentryWeight() {
        return this.sentryWeight;
    }
    public void setSentryWeight(final double sentryWeight) {
        this.sentryWeight = sentryWeight;
    }
    public float getSentrySpeed() {
        return this.sentrySpeed;
    }
    public void setSentrySpeed(final float sentrySpeed) {
        this.sentrySpeed = sentrySpeed;
    }
    public boolean doesKillsDropInventory() {
        return this.killsDropInventory;
    }
    public void setKillsDropInventory(final boolean killsDropInventory) {
        this.killsDropInventory = killsDropInventory;
    }
    public boolean isDropInventory() {
        return this.dropInventory;
    }
    public void setDropInventory(final boolean dropInventory) {
        this.dropInventory = dropInventory;
    }
    public boolean isTargetable() {
        return this.targetable;
    }
    public void setTargetable(final boolean targetable) {
        this.targetable = targetable;
    }
    public boolean isLuckyHits() {
        return this.luckyHits;
    }
    public void setLuckyHits(final boolean luckyHits) {
        this.luckyHits = luckyHits;
    }
    public boolean isIgnoreLOS() {
        return this.ignoreLOS;
    }
    public void setIgnoreLOS(final boolean ignoreLOS) {
        this.ignoreLOS = ignoreLOS;
    }
    public boolean isInvincible() {
        return this.invincible;
    }
    public void setInvincible(final boolean invincible) {
        this.invincible = invincible;
    }
    public boolean isRetaliate() {
        return this.retaliate;
    }
    public void setRetaliate(final boolean retaliate) {
        this.retaliate = retaliate;
    }
    public int getMountID() {
        return this.mountID;
    }
    public void setMountID(final int mountID) {
        this.mountID = mountID;
    }
    public int getEpcount() {
        return this.epcount;
    }
    public void setEpcount(final int epcount) {
        this.epcount = epcount;
    }
    public String getGreetingMessage() {
        return this.greetingMessage;
    }
    public void setGreetingMessage(final String greetingMessage) {
        this.greetingMessage = greetingMessage;
    }
    public String getWarningMessage() {
        return this.warningMessage;
    }
    public void setWarningMessage(final String warningMessage) {
        this.warningMessage = warningMessage;
    }
    public LivingEntity getGuardEntity() {
        return this.guardEntity;
    }
    @SuppressWarnings("SameParameterValue")
    public void setGuardEntity(final LivingEntity guardEntity) {
        this.guardEntity = guardEntity;
    }
    public List<String> getIgnoreTargets() {
        return this.ignoreTargets;
    }
    public void setIgnoreTargets(final List<String> ignoreTargets) {
        this.ignoreTargets = ignoreTargets;
    }
    public List<String> getValidTargets() {
        return this.validTargets;
    }
    public void setValidTargets(final List<String> validTargets) {
        this.validTargets = validTargets;
    }
    public LivingEntity getMeleeTarget() {
        return this.meleeTarget;
    }
    public void setMeleeTarget(final LivingEntity meleeTarget) {
        this.meleeTarget = meleeTarget;
    }
    public SentryTrait getMyTrait() {
        return this.myTrait;
    }
    public List<PotionEffect> getPotionEffects() {
        return this.potionEffects;
    }
    public void setPotionEffects(final List<PotionEffect> potionEffects) {
        this.potionEffects = potionEffects;
    }
    public LivingEntity getProjectileTarget() {
        return this.projectileTarget;
    }
    public void setProjectileTarget(final LivingEntity projectileTarget) {
        this.projectileTarget = projectileTarget;
    }
    public Status getSentryStatus() {
        return this.sentryStatus;
    }
    public void setSentryStatus(final Status sentryStatus) {
        this.sentryStatus = sentryStatus;
    }
    public Location getSpawn() {
        return this.Spawn;
    }
    public void setSpawn(final Location spawn) {
        this.Spawn = spawn;
    }
    public Set<Player> get_myDamagers() {
        return this._myDamagers;
    }
    public int getLightningLevel() {
        return this.lightningLevel;
    }
    public boolean isIncendiary() {
        return this.incendiary;
    }
    public boolean isLoaded() {
        return this.loaded;
    }
    public NPC getMyNPC() {
        return this.myNPC;
    }
    public long getOkToFire() {
        return this.okToFire;
    }
    public long getOkToHeal() {
        return this.okToHeal;
    }
    public long getOkToReasses() {
        return this.okToReasses;
    }
    public long getOkToTakeDamage() {
        return this.okToTakeDamage;
    }
    public boolean isMountCreated() {
        return this.mountCreated;
    }
    private enum Target {
        ALL(powTwo(0)),                 /* 1 */
        PLAYERS(powTwo(1)),             /* 2 */
        NPCS(powTwo(2)),                /* 4 */
        MONSTERS(powTwo(3)),            /* 8 */
        EVENTS(powTwo(4)),              /* 16 */
        NAMED_ENTITIES(powTwo(5)),      /* 32 */
        NAMED_PLAYERS(powTwo(6)),       /* 64 */
        NAMED_NPCS(powTwo(7)),          /* 128 */
        FACTION(powTwo(8)),             /* 256 */
        TOWNY(powTwo(9)),               /* 512 */
        WAR(powTwo(10)),                /* 1024 */
        GROUPS(powTwo(11)),             /* 2048 */
        OWNER(powTwo(12)),              /* 4098 */
        CLANS(powTwo(13)),              /* 8196 */
        TOWNY_ENEMIES(powTwo(14)),      /* 16384 */
        FACTION_ENEMIES(powTwo(15)),    /* 32768 */
        MC_TEAMS(powTwo(16));           /* 65536 */

        private final int level;

        Target(final int level) {
            this.level = level;
        }
    }

    public enum HitType {
        BLOCK,
        DISEMBOWEL,
        GLANCE,
        INJURE,
        MAIN,
        MISS,
        NORMAL,
    }

    public enum Status {
        DEAD,
        DYING,
        HOSTILE,
        LOOKING,
        RETALIATING,
        STUCK,
        WAITING
    }

    private class SentryLogic implements Runnable {

        @Override
        public void run() {
            // plugin.getServer().broadcastMessage("tick " + (myNPC ==null) +
            if (getMyEntity() == null || getMyEntity().isDead()) {
                SentryInstance.this.sentryStatus = Status.DEAD; // in case it dies in a way im not handling.....
            }

            if (UpdateWeapon()) {
                //ranged
                if (SentryInstance.this.meleeTarget != null) {
                    SentryInstance.this.plugin.debug(SentryInstance.this.myNPC, "Switched to ranged");
                    final LivingEntity meleeTarget = SentryInstance.this.meleeTarget;
                    final boolean ret = SentryInstance.this.sentryStatus == Status.RETALIATING;
                    clearTarget();
                    setTarget(meleeTarget, ret);
                }
            }
            else {
                //melee
                if (SentryInstance.this.projectileTarget != null) {
                    SentryInstance.this.plugin.debug(SentryInstance.this.myNPC, "Switched to melee");
                    final boolean ret = SentryInstance.this.sentryStatus == Status.RETALIATING;
                    final LivingEntity projectileTarget = SentryInstance.this.projectileTarget;
                    clearTarget();
                    setTarget(projectileTarget, ret);
                }
            }

            if (SentryInstance.this.sentryStatus != Status.DEAD && SentryInstance.this.healRate > 0) {
                if (System.currentTimeMillis() > SentryInstance.this.okToHeal) {
                    if (getHealth() < SentryInstance.this.sentryHealth &&
                        SentryInstance.this.sentryStatus != Status.DEAD &&
                        SentryInstance.this.sentryStatus != Status.DYING) {
                        double heal = 1;
                        if (SentryInstance.this.healRate < 0.5) { heal = (0.5 / SentryInstance.this.healRate); }

                        setHealth(getHealth() + heal);

                        if (SentryInstance.this.healAnimation != null) {
                            net.citizensnpcs.util.NMS.sendPacketsNearby(null, getMyEntity().getLocation(),
                                                                        SentryInstance.this.healAnimation);
                        }

                        if (getHealth() >= SentryInstance.this.sentryHealth) {
                            SentryInstance.this._myDamagers.clear(); //healed to full, forget attackers
                        }

                    }
                    SentryInstance.this.okToHeal =
                        (long) (System.currentTimeMillis() + SentryInstance.this.healRate * 1000);
                }

            }

            if (SentryInstance.this.myNPC.isSpawned() && !getMyEntity().isInsideVehicle() && isMounted() &&
                isMyChunkLoaded()) { mount(); }

            if (SentryInstance.this.sentryStatus == Status.DEAD &&
                System.currentTimeMillis() > SentryInstance.this.isRespawnable &&
                SentryInstance.this.respawnDelaySeconds > 0 & SentryInstance.this.Spawn.getWorld().isChunkLoaded(
                    SentryInstance.this.Spawn.getBlockX() >> 4, SentryInstance.this.Spawn.getBlockZ() >> 4)) {
                // Respawn

                SentryInstance.this.plugin.debug(SentryInstance.this.myNPC, "respawning npc");
                if (SentryInstance.this.guardEntity == null) {
                    SentryInstance.this.myNPC.spawn(SentryInstance.this.Spawn.clone());
                    //	myNPC.teleport(Spawn,org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
                else {
                    SentryInstance.this.myNPC.spawn(SentryInstance.this.guardEntity.getLocation().add(2, 0, 2));
                    //	myNPC.teleport(guardEntity.getLocation().add(2, 0, 2),org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
            }
            else if ((SentryInstance.this.sentryStatus == Status.HOSTILE ||
                      SentryInstance.this.sentryStatus == Status.RETALIATING) &&
                     SentryInstance.this.myNPC.isSpawned()) {

                if (!isMyChunkLoaded()) {
                    clearTarget();
                    return;
                }

                if (SentryInstance.this.targets > 0 && SentryInstance.this.sentryStatus == Status.HOSTILE &&
                    System.currentTimeMillis() > SentryInstance.this.okToReasses) {
                    final LivingEntity target = findTarget(SentryInstance.this.sentryRange);
                    setTarget(target, false);
                    SentryInstance.this.okToReasses = System.currentTimeMillis() + 3000;
                }

                if (SentryInstance.this.projectileTarget != null && !SentryInstance.this.projectileTarget.isDead() &&
                    SentryInstance.this.projectileTarget.getWorld() == getMyEntity().getLocation().getWorld()) {
                    if (SentryInstance.this._projTargetLostLoc == null) {
                        SentryInstance.this._projTargetLostLoc = SentryInstance.this.projectileTarget.getLocation();
                    }

                    if (!getNavigator().isNavigating()) {
                        faceEntity(getMyEntity(), SentryInstance.this.projectileTarget);
                    }

                    Draw(true);

                    if (System.currentTimeMillis() > SentryInstance.this.okToFire) {
                        // Fire!
                        SentryInstance.this.okToFire =
                            (long) (System.currentTimeMillis() + SentryInstance.this.attackRateSeconds * 1000.0);
                        Fire(SentryInstance.this.projectileTarget);
                    }
                    if (SentryInstance.this.projectileTarget != null) {
                        SentryInstance.this._projTargetLostLoc = SentryInstance.this.projectileTarget.getLocation();
                    }
                }

                else if (SentryInstance.this.meleeTarget != null && !SentryInstance.this.meleeTarget.isDead()) {

                    if (isMounted()) { faceEntity(getMyEntity(), SentryInstance.this.meleeTarget); }

                    if (SentryInstance.this.meleeTarget.getWorld() == getMyEntity().getLocation().getWorld()) {
                        final double dist =
                            SentryInstance.this.meleeTarget.getLocation().distance(getMyEntity().getLocation());
                        //block if in range
                        Draw(dist < 3);
                        // Did it get away?
                        if (dist > SentryInstance.this.sentryRange) {
                            // it got away...
                            clearTarget();
                        }
                    }
                    else {
                        clearTarget();
                    }

                }

                else {
                    // target died or null
                    clearTarget();
                }

            }

            else if (SentryInstance.this.sentryStatus == Status.LOOKING && SentryInstance.this.myNPC.isSpawned()) {

                if (getMyEntity().isInsideVehicle()) {
                    faceAlignWithVehicle(); //sync the rider with the vehicle.
                }

                if (SentryInstance.this.guardEntity instanceof Player) {
                    if (!((Player) SentryInstance.this.guardEntity).isOnline()) {
                        SentryInstance.this.guardEntity = null;
                    }
                }

                if (SentryInstance.this.guardTarget != null && SentryInstance.this.guardEntity == null) {
                    // daddy? where are u?
                    setGuardTarget(SentryInstance.this.guardEntity, true);
                }

                if (SentryInstance.this.guardEntity != null) {

                    final Location npcLoc = getMyEntity().getLocation();

                    if (SentryInstance.this.guardEntity.getLocation().getWorld() != npcLoc.getWorld() ||
                        !isMyChunkLoaded()) {
                        if (Util.CanWarp(SentryInstance.this.guardEntity, SentryInstance.this.myNPC)) {
                            SentryInstance.this.myNPC.despawn();
                            SentryInstance.this.myNPC
                                .spawn((SentryInstance.this.guardEntity.getLocation().add(1, 0, 1)));
                        }
                        else {
                            SentryInstance.this.guardEntity.sendMessage(
                                SentryInstance.this.myNPC.getName() + " cannot follow you to " +
                                SentryInstance.this.guardEntity.getWorld().getName());
                            SentryInstance.this.guardEntity = null;
                        }

                    }
                    else {
                        final double dist = npcLoc.distanceSquared(SentryInstance.this.guardEntity.getLocation());
                        SentryInstance.this.plugin.debug(SentryInstance.this.myNPC,
                                                         dist + "" + getNavigator().isNavigating() + " " +
                                                         getNavigator().getEntityTarget());
                        if (dist > 1024) {
                            SentryInstance.this.myNPC
                                .teleport(SentryInstance.this.guardEntity.getLocation().add(1, 0, 1),
                                          TeleportCause.PLUGIN);
                        }
                        else if (dist > SentryInstance.this.followDistance && !getNavigator().isNavigating()) {
                            getNavigator().setTarget(SentryInstance.this.guardEntity, false);
                            getNavigator().getLocalParameters().stationaryTicks(3 * 20);
                        }
                        else if (dist < SentryInstance.this.followDistance && getNavigator().isNavigating()) {
                            getNavigator().cancelNavigation();
                        }
                    }
                }

                LivingEntity target = null;

                if (SentryInstance.this.targets > 0) {
                    target = findTarget(SentryInstance.this.sentryRange);
                }

                if (target != null) {
                    SentryInstance.this.okToReasses = System.currentTimeMillis() + 3000;
                    setTarget(target, false);
                }

            }

        }
    }
}
