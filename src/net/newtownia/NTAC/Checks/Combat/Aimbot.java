package net.newtownia.NTAC.Checks.Combat;

import com.comphenix.packetwrapper.WrapperPlayClientUseEntity;
import com.comphenix.protocol.events.PacketEvent;
import net.newtownia.NTAC.Action.ActionData;
import net.newtownia.NTAC.Action.ViolationManager;
import net.newtownia.NTAC.NTAC;
import net.newtownia.NTAC.Utils.EntityUtils;
import net.newtownia.NTAC.Utils.LogUtils;
import net.newtownia.NTAC.Utils.MathUtils;
import net.newtownia.NTAC.Utils.PunishUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

public class Aimbot extends AbstractCombatCheck
{
    private int dataCount = 5;
    private int threshold = 5;
    private int minYawChange = 5;
    private int invalidateThreshold = 60000;
    private ActionData actionData;

    private Map<UUID, List<Double>> playerAttackAngles;
    private Map<UUID, Float> playerLastAttackYaw;
    private ViolationManager vlManager;

    public Aimbot(NTAC pl, CombatBase combatBase)
    {
        super(pl, combatBase, "Aimbot");
        vlManager = new ViolationManager();
        playerAttackAngles = new HashMap<>();
        playerLastAttackYaw = new HashMap<>();

        loadConfig();

        Bukkit.getScheduler().runTaskTimer(pl, new Runnable() {
            @Override
            public void run() {
                vlManager.resetAllOldViolation(invalidateThreshold);
            }
        }, 20L, 20L);
    }

    @Override
    protected void onAttackPacketReceive(PacketEvent event, WrapperPlayClientUseEntity packet)
    {
        if (!isEnabled())
            return;

        Player p = event.getPlayer();
        UUID pUUID = p.getUniqueId();

        if (p.hasPermission("ntac.bypass.aimbot"))
            return;

        if (playerLastAttackYaw.containsKey(pUUID))
        {
            if (MathUtils.isSame((double)p.getLocation().getYaw(),
                    playerLastAttackYaw.get(pUUID), minYawChange))
            {
                playerLastAttackYaw.put(pUUID, p.getLocation().getYaw());
                return;
            }
        }
        playerLastAttackYaw.put(pUUID, p.getLocation().getYaw());

        Entity attacked = EntityUtils.getEntityByEntityID(packet.getTargetID(), p.getLocation().getWorld());
        if (attacked == null)
        {
            LogUtils.error("Unable to find attacked entity");
            return;
        }
        if (EntityUtils.isPassive(attacked))
            return;

        double angleDiff = MathUtils.getYawDiff(p.getLocation(), attacked.getLocation());
        if (angleDiff < 0)
            angleDiff *= -1;

        if (!playerAttackAngles.containsKey(pUUID))
        {
            playerAttackAngles.put(pUUID, new ArrayList<>(Collections.singletonList(angleDiff)));
            return;
        }

        List<Double> angles = playerAttackAngles.get(pUUID);
        angles.add(0, angleDiff);
        while (angles.size() > dataCount)
            angles.remove(dataCount);

        if (angles.size() == dataCount)
        {
            double average = MathUtils.getAverageDouble(angles);
            boolean suspicious = true;
            for (double angle : angles)
            {
                if (MathUtils.isSame(angle, average, threshold))
                    suspicious = false;
            }
            if (suspicious)
            {
                vlManager.addViolation(p, 1);
                PunishUtils.runViolationAction(p, vlManager, actionData);
            }
        }
    }

    @Override
    public void loadConfig()
    {
        YamlConfiguration config = pl.getConfiguration();
        dataCount = Integer.parseInt(config.getString("Aimbot.Data-Count"));
        threshold = Integer.parseInt(config.getString("Aimbot.Angle-Threshold"));
        minYawChange = Integer.parseInt(config.getString("Aimbot.Min-Yaw"));
        invalidateThreshold = Integer.parseInt(config.getString("Aimbot.Invalidate-Threshold"));
        actionData = new ActionData(config, "Aimbot.Actions");
    }
}
