package net.set.spawn.mod.mixin;

import net.minecraft.server.MinecraftServer;
import net.set.spawn.mod.interfaces.MinecraftServerExtended;
import org.spongepowered.asm.mixin.*;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements MinecraftServerExtended {
    @Unique
    private boolean shouldModifySpawn = false;

    @Override
    public boolean setspawnmod$shouldModifySpawn() {
        return shouldModifySpawn;
    }

    @Override
    public void setspawnmod$setShouldModifySpawn(boolean shouldModifySpawn) {
        this.shouldModifySpawn = shouldModifySpawn;
    }
}
