package ac.grim.grimac.manager.tick.impl;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.impl.movement.PlayerCompensationRunner;
import ac.grim.grimac.manager.tick.Tickable;

public class TickPlayerCompensation implements Tickable {
    @Override
    public void tick() {
        GrimAPI.INSTANCE.getPlayerDataManager().getEntries().forEach(entry -> {
            entry.checkManager.getPacketCheck(PlayerCompensationRunner.class).tick();
        });
    }
}
