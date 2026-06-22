package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketPersonalDimensionRulesHandler implements IMessageHandler<PacketPersonalDimensionRules, IMessage> {

    @Override
    public IMessage onMessage(PacketPersonalDimensionRules message, MessageContext ctx) {
        ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
            PersonalDimensionRules rules = new PersonalDimensionRules();
            rules.disableMobSpawning = message.isDisableMobSpawning();
            rules.lockWeather = message.isLockWeather();
            rules.lockTime = message.isLockTime();
            rules.daylightCycle = message.isDaylightCycle();
            rules.timeValue = message.getTimeValue();
            PersonalDimensionManager.setRules(ctx.getServerHandler().player.getUniqueID(), rules);
        });
        return null;
    }
}
