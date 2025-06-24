package net.set.spawn.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.*;
import com.llamalad7.mixinextras.sugar.ref.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
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

    @WrapOperation(method = "moveToSpawn", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I"))
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
            seed.set(SetSpawn.findSeedObjectFromLong(this.getServerWorld().getSeed()));
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

    @Dynamic
    @ModifyExpressionValue(
            method = "moveToSpawn",
            at = {
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/network/SpawnLocating;findOverworldSpawn(Lnet/minecraft/server/world/ServerWorld;IIZ)Lnet/minecraft/util/math/BlockPos;"
                    ),
                    // 1.18+ findOverworldSpawn, no boolean
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/class_5322;method_29194(Lnet/minecraft/class_3218;II)Lnet/minecraft/class_2338;"
                    ),
                    @At(
                            value = "INVOKE",
                            // Dimension#getTopSpawningBlockPosition
                            target = "Lnet/minecraft/class_2869;method_12444(IIZ)Lnet/minecraft/class_2338;",
                            remap = false
                    )
            },
            require = 1,
            allow = 1
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

    @ModifyVariable(method = "moveToSpawn", at = @At(value = "LOAD", ordinal = 0), ordinal = 5)
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
            method = "moveToSpawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;refreshPositionAndAngles(Lnet/minecraft/util/math/BlockPos;FF)V",
                    ordinal = 1
            )
    )
    private void failOnNonRandomSpawns(CallbackInfo ci, @Share("seed") LocalRef<Seed> seed) {
        if (seed.get() != null) {
            this.setSpawnError = "Failed to apply SetSpawn configuration because the spawn was not random. Not overriding player spawnpoint.";
        }
    }

    @Group(min = 1, max = 1)
    // 1.14-1.16.5
    @Inject(method = "onSpawn", at = @At("TAIL"), require = 0)
    private void sendErrorMessage(CallbackInfo ci) {
        if (this.setSpawnError != null) {
            // it is not possible to fix this without more subprojects. you are warned.
            this.sendMessage(new LiteralText("§c" + this.setSpawnError + " This run is not verifiable."), false);
            this.setSpawnError = null;
        }
    }

    @Dynamic
    @Group
    // 1.17-1.18.2
    @Inject(method = "method_14235(Lnet/minecraft/class_1703;)V", at = @At("TAIL"), require = 0, remap = false)
    private void sendErrorMessage2(CallbackInfo ci) {
        if (this.setSpawnError != null) {
            // sorry the code is bad. it is the only way.
            this.sendMessage(new LiteralText("§c" + this.setSpawnError + " This run is not verifiable."), false);
            this.setSpawnError = null;
        }
    }
}
