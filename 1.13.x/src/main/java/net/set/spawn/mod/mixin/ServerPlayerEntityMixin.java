package net.set.spawn.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.*;
import com.llamalad7.mixinextras.sugar.ref.*;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.*;
import net.set.spawn.mod.*;
import net.set.spawn.mod.interfaces.MinecraftServerExtended;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import java.util.Random;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Shadow
    @Final
    public MinecraftServer server;

    @Unique
    private String setSpawnError;

    @Shadow
    public abstract void sendMessage(Text message, boolean actionBar);

    @Shadow
    public abstract ServerWorld getServerWorld();

    @WrapOperation(method = "method_21281", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I"))
    private int setSpawn(Random random, int bounds, Operation<Integer> original, @Local(ordinal = 0) BlockPos worldSpawn, @Local(ordinal = 0) int spawnRadius, @Share("seed") LocalRef<Seed> seed, @Share("originalRandomResult") LocalRef<Integer> originalRandomResult) {
        int originalResult = original.call(random, bounds);

        if (((MinecraftServerExtended) this.server).setspawnmod$shouldModifySpawn()) {
            ((MinecraftServerExtended) this.server).setspawnmod$setShouldModifySpawn(false);
            seed.set(SetSpawn.findSeedObjectFromLong(this.getServerWorld().method_3588().getSeed()));
        }
        Seed seedObject = seed.get();
        if (seedObject == null) {
            return originalResult;
        }

        // Transform x and z coordinates into correct Random#nextInt result.
        int result = ((MathHelper.floor(seedObject.getX()) - worldSpawn.getX()) + spawnRadius) + ((MathHelper.floor(seedObject.getZ()) - worldSpawn.getZ()) + spawnRadius) * (spawnRadius * 2 + 1);

        if (result >= 0 && result < bounds) {
            // we save the original result in case the set spawn is invalid, see fallbackOnInvalidSpawn
            System.out.println("Setting spawn");
            originalRandomResult.set(originalResult);
            return result;
        } else {
            this.setSpawnError = "The X or Z coordinates given (" + seed.get().getX() + ", " + seed.get().getZ() + ") are more than the worlds spawn radius (" + spawnRadius + " blocks) away from the world spawn. Not overriding player spawnpoint.";
        }
        return originalResult;
    }

    @ModifyVariable(method = "method_21281", at = @At(value = "LOAD", ordinal = 0), ordinal = 5)
    private int fallbackOnInvalidSpawn(int p, @Local(ordinal = 4) LocalIntRef o, @Share("seed") LocalRef<Seed> seed, @Share("originalRandomResult") LocalRef<Integer> originalRandomResult) {
        // checks if the for loop is on its second iteration (p == 1), meaning the setspawn given spawn was invalid
        // and restores the original result of Random#nextInt
        if (p == 1 && originalRandomResult.get() != null) {
            o.set(originalRandomResult.get());
            originalRandomResult.set(null);
            p = 0;

            this.setSpawnError = "There is no valid spawning location at the specified coordinates (" + seed.get().getX() + ", " + seed.get().getZ() + "). Not overriding player spawnpoint.";
        }
        return p;
    }

    @Inject(method = "method_21281", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ServerPlayerEntity;refreshPositionAndAngles(Lnet/minecraft/util/math/BlockPos;FF)V", ordinal = 1))
    private void failOnNonRandomSpawns(CallbackInfo ci, @Share("seed") LocalRef<Seed> seed) {
        if (seed.get() != null) {
            this.setSpawnError = "Failed to apply SetSpawn configuration because the spawn was not random. Not overriding player spawnpoint.";
        }
    }

    @Inject(method = "listenToScreenHandler", at = @At("TAIL"))
    private void sendErrorMessage(CallbackInfo ci) {
        if (this.setSpawnError != null) {
            this.sendMessage(new LiteralText(this.setSpawnError + " This run is not verifiable.").setStyle(new Style().setFormatting(Formatting.RED)), false);
            this.setSpawnError = null;
        }
    }
}
