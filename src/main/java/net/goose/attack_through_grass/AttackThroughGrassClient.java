package net.goose.attack_through_grass;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
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

import java.util.ArrayList;
import java.util.List;

public class AttackThroughGrassClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            HitResult hitResult = rayTraceBlock(player, 1.0F, player.isCreative() ? 5.0F : 4.5F);
            if(hitResult.getType() == HitResult.Type.BLOCK){
                BlockPos blockPos = BlockPos.ofFloored(hitResult.getPos());
                if(player.getWorld().getBlockState(blockPos).getCollisionShape(player.getWorld(), blockPos).isEmpty()){
                    EntityHitResult entityHitResult = rayTraceEntity(player, 1.0F, player.isCreative() ? 5.0F: 4.5F);
                    if(entityHitResult != null){
                        client.interactionManager.attackEntity(player, entityHitResult.getEntity());
                        client.player.swingHand(client.player.getActiveHand());
                        client.attackCooldown = 10;
                        return true;
                    }
                }
            }

            return false;
        });
    }

    @Nullable
    public static HitResult rayTraceBlock(PlayerEntity player, float partialTicks, double blockReachDistance){
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
                                && e.canHit()
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
}
