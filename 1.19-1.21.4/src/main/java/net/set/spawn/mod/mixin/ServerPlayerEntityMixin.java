package net.set.spawn.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.*;
import com.llamalad7.mixinextras.sugar.ref.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.set.spawn.mod.*;
import net.set.spawn.mod.interfaces.MinecraftServerExtended;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Shadow
    @Final
    public MinecraftServer server;

    @Unique
    private String setSpawnError;

    @Shadow
    public abstract void sendMessage(Text message, boolean actionBar);

    @Shadow(aliases = "method_51469")
    public abstract ServerWorld getWorld();

    @Dynamic
    @WrapOperation(method = {"moveToSpawn", "method_14245(Lnet/minecraft/class_3218;Lnet/minecraft/class_2338;)Lnet/minecraft/class_2338;"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/random/Random;nextInt(I)I"))
    private int setSpawn(Random random, int bounds, Operation<Integer> original, @Local(ordinal = 0) BlockPos worldSpawn, @Local(ordinal = 0) int spawnRadius, @Share("seed") LocalRef<Seed> seed, @Share("originalRandomResult") LocalRef<Integer> originalRandomResult, @Share("isRandomSpawn") LocalBooleanRef isRandomSpawn) {
        // fallback for 1.21 with no else statement in the skylight check
        isRandomSpawn.set(true);
        int originalResult = original.call(random, bounds);

        if (((MinecraftServerExtended) this.server).setspawnmod$shouldModifySpawn()) {
            ((MinecraftServerExtended) this.server).setspawnmod$setShouldModifySpawn(false);
            seed.set(SetSpawn.findSeedObjectFromLong(this.getWorld().getSeed()));
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

        if (xLocal >=0 && xLocal < spawnDiameter && result >= 0 && result < bounds) {
            // we save the original result in case the set spawn is invalid, see fallbackOnInvalidSpawn
            originalRandomResult.set(originalResult);
            System.out.println("Setting spawn");
            return result;
        } else {
            this.setSpawnError = "The X or Z coordinates given (" + seed.get().getX() + ", " + seed.get().getZ() + ") are more than the worlds spawn radius (" + spawnRadius + " blocks) away from the world spawn. Not overriding player spawnpoint.";
        }
        return originalResult;
    }

    @Dynamic
    @ModifyVariable(method = {"moveToSpawn", "method_14245(Lnet/minecraft/class_3218;Lnet/minecraft/class_2338;)Lnet/minecraft/class_2338;"}, at = @At(value = "LOAD", ordinal = 0), ordinal = 5)
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

    @Group(min = 1, max = 1)
    @Inject(method = "moveToSpawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;refreshPositionAndAngles(Lnet/minecraft/util/math/BlockPos;FF)V", ordinal = 1), require = 0)
    private void failOnNonRandomSpawns(CallbackInfo ci, @Share("seed") LocalRef<Seed> seed) {
        if (seed.get() != null) {
            this.setSpawnError = "Failed to apply SetSpawn configuration because the spawn was not random. Not overriding player spawnpoint.";
        }
    }

    @Group
    @Dynamic
    @Inject(
            method = "method_14245(Lnet/minecraft/class_3218;Lnet/minecraft/class_2338;)Lnet/minecraft/class_2338;",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/class_3218;method_8587(Lnet/minecraft/class_1297;Lnet/minecraft/class_238;)Z", ordinal = 1),
                    @At(value = "INVOKE", target = "Lnet/minecraft/class_3222;method_61274(Lnet/minecraft/class_3218;Lnet/minecraft/class_238;)Z", ordinal = 1)
            },
            require = 0, allow = 1, remap = false
    )
    private void failOnNonRandomSpawns2(CallbackInfoReturnable<Boolean> cir, @Share("seed") LocalRef<Seed> seed, @Share("isRandomSpawn") LocalBooleanRef isRandomSpawn) {
        if (!isRandomSpawn.get() && seed.get() != null) {
            this.setSpawnError = "Failed to apply SetSpawn configuration because the spawn was not random. Not overriding player spawnpoint.";
        }
    }

    @Inject(method = "onSpawn", at = @At("TAIL"))
    private void sendErrorMessage(CallbackInfo ci) {
        if (this.setSpawnError != null) {
            this.sendMessage(Text.literal(this.setSpawnError + " This run is not verifiable.").formatted(Formatting.RED), false);
            this.setSpawnError = null;
        }
    }
}
