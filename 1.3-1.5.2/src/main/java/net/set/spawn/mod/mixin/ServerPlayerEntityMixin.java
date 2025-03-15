package net.set.spawn.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.*;
import com.llamalad7.mixinextras.sugar.ref.*;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.*;
import net.set.spawn.mod.*;
import net.set.spawn.mod.interfaces.MinecraftServerExtended;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Unique
    private String setSpawnError;

    @Shadow
    public abstract void method_3331(String message);

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I", ordinal = 0))
    private int setSpawnX(Random random, int bounds, Operation<Integer> original, @Local(argsOnly = true) MinecraftServer server, @Local BlockPos worldSpawn, @Share("seed") LocalRef<Seed> seed, @Share("zCoord") LocalRef<Integer> zCoord, @Share("isRandomSpawn") LocalBooleanRef isRandomSpawn) {
        isRandomSpawn.set(true);
        int originalResult = original.call(random, bounds);

        if (((MinecraftServerExtended) server).setspawnmod$shouldModifySpawn()) {
            ((MinecraftServerExtended) server).setspawnmod$setShouldModifySpawn(false);
            seed.set(SetSpawn.findSeedObjectFromLong(server.getWorld(0).getSeed()));
        }
        Seed seedObject = seed.get();
        if (seedObject == null) {
            return originalResult;
        }

        // Transform x and z coordinates into correct Random#nextInt result.
        int resultX = MathHelper.floor(seedObject.getX()) - worldSpawn.x + 10;
        int resultZ = MathHelper.floor(seedObject.getZ()) - worldSpawn.z + 10;

        if (resultX >= 0 && resultX < bounds && resultZ >= 0 && resultZ < bounds) {
            zCoord.set(resultZ);
            System.out.println("Setting spawn");
            return resultX;
        } else {
            this.setSpawnError = "The X or Z coordinates given (" + seed.get().getX() + ", " + seed.get().getZ() + ") are more than the worlds spawn radius (" + 10 + " blocks) away from the world spawn. Not overriding player spawnpoint.";
        }
        return originalResult;
    }

    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I", ordinal = 1))
    private int setSpawnZ(int original, @Share("zCoord") LocalRef<Integer> zCoord) {
        // if zCoord is not null, it has been validated in the method
        return zCoord.get() != null ? zCoord.get() : original;
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ServerPlayerEntity;refreshPositionAndAngles(DDDFF)V"))
    private void failOnNonRandomSpawns(CallbackInfo ci, @Share("seed") LocalRef<Seed> seed, @Share("isRandomSpawn") LocalBooleanRef isRandomSpawn) {
        if (!isRandomSpawn.get() && seed.get() != null) {
            this.setSpawnError = "Failed to apply SetSpawn configuration because the spawn was not random. Not overriding player spawnpoint.";
        }
    }

    @Inject(method = "listenToScreenHandler", at = @At("TAIL"))
    private void sendErrorMessage(CallbackInfo ci) {
        if (this.setSpawnError != null) {
            this.method_3331("§c" + this.setSpawnError + " This run is not verifiable.");
            this.setSpawnError = null;
        }
    }
}
