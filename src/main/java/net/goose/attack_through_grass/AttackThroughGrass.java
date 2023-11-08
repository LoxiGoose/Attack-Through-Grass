package net.goose.attack_through_grass;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AttackThroughGrass implements ModInitializer {
    /**
     * Runs the mod initializer.
     */
    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register(((world, player, pos, state, blockEntity) -> {
            if(state.getCollisionShape(world, pos).isEmpty()){
                EntityHitResult entityHitResult = rayTraceEntity(player, 1.0F, player.isCreative() ? 5.0F: 4.5F);
                if(entityHitResult != null){
                    player.attack(entityHitResult.getEntity()); // This method should already handle everything for us, sweeping, enchants, and everything.
                    player.resetLastAttackedTicks();
                    return false;
                }
            }

            return true;
        }));
    }

    @Nullable
    private static EntityHitResult rayTraceEntity(PlayerEntity player, float partialTicks, double blockReachDistance) {
        Vec3d from = player.getEyePos();
        Vec3d look = player.getRotationVec(partialTicks);
        Vec3d to = from.add(look.x * blockReachDistance, look.y * blockReachDistance, look.z * blockReachDistance);

        HitResult hitresult = player.getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
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
