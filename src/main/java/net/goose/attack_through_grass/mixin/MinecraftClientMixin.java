package net.goose.attack_through_grass.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Nullable
    private static HitResult rayTraceBlock(PlayerEntity player, float partialTicks, double blockReachDistance){
        Vec3d from = player.getEyePos();
        Vec3d look = player.getRotationVec(partialTicks);
        Vec3d to = from.add(look.x * blockReachDistance, look.y * blockReachDistance, look.z * blockReachDistance);

        return player.getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
    }

    @Nullable
    private static EntityHitResult rayTraceEntity(PlayerEntity player, float partialTicks, double blockReachDistance) {
        Vec3d from = player.getEyePos();
        Vec3d look = player.getRotationVec(partialTicks);
        Vec3d to = from.add(look.x * blockReachDistance, look.y * blockReachDistance, look.z * blockReachDistance);
        HitResult hitresult = rayTraceBlock(player, partialTicks, blockReachDistance);
        if (hitresult.getType() != HitResult.Type.MISS) {
            to = hitresult.getPos();
        }

        return ProjectileUtil.getEntityCollision(
                player.getWorld(),
                player,
                from,
                to,
                new Box(from, to),
                EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR
                        .and(e -> e != null
                                && e.isCollidable()
                                && e instanceof LivingEntity
                                && !getAllRidingEntities(player).contains(e)
                        )
        );
    }

    private static List<Entity> getAllRidingEntities(PlayerEntity player) {
        List<Entity> ridingEntities = new ArrayList<>();
        Entity entity = player;
        while (entity.hasVehicle()) {
            entity = entity.getVehicle();
            ridingEntities.add(entity);
        }
        return ridingEntities;
    }


    @Shadow @Nullable public ClientPlayerInteractionManager interactionManager;

    @Shadow @Final public GameOptions options;
    @Shadow @Nullable public ClientPlayerEntity player;
    private boolean fabric_attackCancelled;

    @Inject(
            method = "handleInputEvents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",
                    ordinal = 0
            )
    )
    private void injectHandleInputEventsForPreAttackCallback(CallbackInfo ci) {
        boolean wentThrough = false;

        if (options.attackKey.isPressed()) {
            HitResult hitResult = rayTraceBlock(player, 1.0F, player.isCreative() ? 5.0F : 4.5F);
            if(hitResult.getType() == HitResult.Type.BLOCK){
                BlockPos blockPos = new BlockPos(hitResult.getPos());
                if(player.getWorld().getBlockState(blockPos).getCollisionShape(player.getWorld(), blockPos).isEmpty()){
                    EntityHitResult entityHitResult = rayTraceEntity(player, 1.0F, player.isCreative() ? 5.0F: 4.5F);
                    if(entityHitResult != null){
                        MinecraftClient minecraftClient = MinecraftClient.getInstance();
                        minecraftClient.interactionManager.attackEntity(player, entityHitResult.getEntity());
                        minecraftClient.player.swingHand(minecraftClient.player.getActiveHand());
                        minecraftClient.attackCooldown = 10;
                        wentThrough = true;
                    }
                }
            }
        }

        fabric_attackCancelled = wentThrough;
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void injectDoAttackForCancelling(CallbackInfoReturnable<Boolean> cir) {
        if (fabric_attackCancelled) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void injectHandleBlockBreakingForCancelling(boolean breaking, CallbackInfo ci) {
        if (fabric_attackCancelled) {
            if (interactionManager != null) {
                interactionManager.cancelBlockBreaking();
            }

            ci.cancel();
        }
    }
}
