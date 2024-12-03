package net.set.spawn.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.MinecraftServer;
import net.set.spawn.mod.interfaces.MinecraftServerExtended;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements MinecraftServerExtended {
    @Unique
    private boolean shouldModifySpawn = false;

    @ModifyExpressionValue(method = "createWorlds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerWorldProperties;isInitialized()Z"), require = 0)
    private boolean checkIfNewWorld(boolean initialized) {
        this.shouldModifySpawn = !initialized;
        return initialized;
    }

    @Override
    public boolean setspawnmod$shouldModifySpawn() {
        return shouldModifySpawn;
    }

    @Override
    public void setspawnmod$setShouldModifySpawn(boolean shouldModifySpawn) {
        this.shouldModifySpawn = shouldModifySpawn;
    }
}
