package net.set.spawn.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
    private int setSpawn(
            Random random,
            int bounds,
            Operation<Integer> original,
            @Local(ordinal = 0) BlockPos worldSpawn,
            @Local(ordinal = 0) int spawnRadius,
            @Share("seed") LocalRef<Seed> seed,
            @Share("originalRandomResult") LocalRef<Integer> originalRandomResult,
            @Share("newRandomValue") LocalRef<Integer> newRandomValue
    ) {
        int originalResult = original.call(random, bounds);

        if (((MinecraftServerExtended) this.server).setspawnmod$shouldModifySpawn()) {
            ((MinecraftServerExtended) this.server).setspawnmod$setShouldModifySpawn(false);
            seed.set(SetSpawn.findSeedObjectFromLong(this.getServerWorld().method_3588().getSeed()));
        }
        Seed seedObject = seed.get();
        if (seedObject == null) {
            return originalResult;
        }

        // Transform x and z coordinates into corresponding Random#nextInt result.
        int spawnDiameter = spawnRadius * 2 + 1;
        int x = MathHelper.floor(seedObject.getX());
        int z = MathHelper.floor(seedObject.getZ());
        int xLocal = x - worldSpawn.getX() + spawnRadius;
        int result = xLocal + (z - worldSpawn.getZ() + spawnRadius) * spawnDiameter;

        if (xLocal >= 0 && xLocal < spawnDiameter && result >= 0 && result < bounds) {
            // we save the original result in case the set spawn is invalid, see fallbackOnInvalidSpawn
            originalRandomResult.set(originalResult);
            newRandomValue.set(result);
            System.out.println("Setting spawn");
            return result;
        } else {
            this.setSpawnError = "The X or Z coordinates given (" + seed.get().getX() + ", " + seed.get().getZ() + ") are more than the worlds spawn radius (" + spawnRadius + " blocks) away from the world spawn. Not overriding player spawnpoint.";
        }
        return originalResult;
    }

    @ModifyExpressionValue(
            method = "method_21281",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/dimension/Dimension;method_17190(IIZ)Lnet/minecraft/util/math/BlockPos;"
            )
    )
    private BlockPos captureIfHasGrassBlock(
            BlockPos blockPos,
            @Share("originalRandomResult") LocalRef<Integer> originalRandomResult,
            @Share("validIncludingObstructed") LocalBooleanRef validIncludingObstructed
    ) {
        if (originalRandomResult.get() != null) {
            // whether or not the spawn is obstructed, it has a grass block above sea level and is valid as an obstructed spawn if all other spawns are obstructed or invalid
            validIncludingObstructed.set(blockPos != null);
        }
        return blockPos;
    }

    @ModifyVariable(method = "method_21281", at = @At(value = "LOAD", ordinal = 0), ordinal = 5)
    private int fallbackOnInvalidSpawn(
            int p,
            @Local(ordinal = 2) int k,
            @Local(ordinal = 3) int n,
            @Local(ordinal = 4) LocalIntRef o,
            @Share("seed") LocalRef<Seed> seed,
            @Share("originalRandomResult") LocalRef<Integer> originalRandomResult,
            @Share("newRandomValue") LocalRef<Integer> newRandomValue,
            @Share("validIncludingObstructed") LocalBooleanRef validIncludingObstructed
    ) {
        // checks if the for loop is on its second iteration (p == 1), meaning the setspawn given spawn was invalid
        // and restores the original result of Random#nextInt
        if (p == 1 && originalRandomResult.get() != null) {
            o.set(originalRandomResult.get());
            originalRandomResult.set(null);
            p = 0;

            this.setSpawnError = "There is no valid spawning location at the specified coordinates (" + seed.get().getX() + ", " + seed.get().getZ() + "). Not overriding player spawnpoint.";
        }
        // if we made it to the end of the loop after an obstructed spawn and didn't find another non-obstructed spawn
        // redo the last iteration of the loop with the choice obstructed spawn
        if (p == k && originalRandomResult.get() == null && newRandomValue.get() != null && validIncludingObstructed.get()) {
            o.set(newRandomValue.get() - n * (p - 1));
            newRandomValue.set(null);
            p = k - 1;
            this.setSpawnError = null;
        }
        return p;
    }

    @Inject(
            method = "method_21281",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/ServerPlayerEntity;refreshPositionAndAngles(Lnet/minecraft/util/math/BlockPos;FF)V",
                    ordinal = 1
            )
    )
    private void failOnNonRandomSpawns(CallbackInfo ci, @Share("seed") LocalRef<Seed> seed) {
        if (seed.get() != null) {
            this.setSpawnError = "Failed to apply SetSpawn configuration because the spawn was not random. Not overriding player spawnpoint.";
        }
    }

    @Inject(method = "listenToScreenHandler", at = @At("TAIL"))
    private void sendErrorMessage(CallbackInfo ci) {
        if (this.setSpawnError != null) {
            this.sendMessage(new LiteralText(this.setSpawnError + " This run is not verifiable.").formatted(Formatting.RED), false);
            this.setSpawnError = null;
        }
    }
}
