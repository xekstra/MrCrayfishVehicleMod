package com.mrcrayfish.vehicle.entity;

import com.mrcrayfish.vehicle.client.VehicleHelper;
import com.mrcrayfish.vehicle.network.PacketHandler;
import com.mrcrayfish.vehicle.network.datasync.VehicleDataValue;
import com.mrcrayfish.vehicle.network.message.MessageHelicopterInput;
import com.mrcrayfish.vehicle.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Author: MrCrayfish
 */
public abstract class HelicopterEntity extends PoweredVehicleEntity
{
    protected static final DataParameter<Float> LIFT = EntityDataManager.defineId(HelicopterEntity.class, DataSerializers.FLOAT);
    protected static final DataParameter<Float> FORWARD_INPUT = EntityDataManager.defineId(HelicopterEntity.class, DataSerializers.FLOAT);
    protected static final DataParameter<Float> SIDE_INPUT = EntityDataManager.defineId(HelicopterEntity.class, DataSerializers.FLOAT);

    protected final VehicleDataValue<Float> lift = new VehicleDataValue<>(this, LIFT);
    protected final VehicleDataValue<Float> forwardInput = new VehicleDataValue<>(this, FORWARD_INPUT);
    protected final VehicleDataValue<Float> sideInput = new VehicleDataValue<>(this, SIDE_INPUT);

    protected Vector3d velocity = Vector3d.ZERO;
    protected float bladeSpeed;

    @OnlyIn(Dist.CLIENT)
    protected float bladeRotation;
    @OnlyIn(Dist.CLIENT)
    protected float prevBladeRotation;
    @OnlyIn(Dist.CLIENT)
    protected float bodyRotationX;
    @OnlyIn(Dist.CLIENT)
    protected float prevBodyRotationX;
    @OnlyIn(Dist.CLIENT)
    protected float bodyRotationY;
    @OnlyIn(Dist.CLIENT)
    protected float prevBodyRotationY;
    @OnlyIn(Dist.CLIENT)
    protected float bodyRotationZ;
    @OnlyIn(Dist.CLIENT)
    protected float prevBodyRotationZ;

    protected HelicopterEntity(EntityType<?> entityType, World worldIn)
    {
        super(entityType, worldIn);
    }

    @Override
    public void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(LIFT, 0F);
        this.entityData.define(FORWARD_INPUT, 0F);
        this.entityData.define(SIDE_INPUT, 0F);
    }

    @Override
    public SoundEvent getEngineSound()
    {
        return null;
    }

    @Override
    public void updateVehicleMotion()
    {
        this.motion = Vector3d.ZERO;

        boolean operating = this.canDrive() && this.getControllingPassenger() != null;
        Entity entity = this.getControllingPassenger();
        if(entity != null && this.isFlying() && operating)
        {
            float deltaYaw = entity.getYHeadRot() % 360.0F - this.yRot;
            while(deltaYaw < -180.0F)
            {
                deltaYaw += 360.0F;
            }
            while(deltaYaw >= 180.0F)
            {
                deltaYaw -= 360.0F;
            }
            this.yRotO = this.yRot;
            this.yRot = this.yRot + deltaYaw * 0.05F;
        }

        VehicleProperties properties = this.getProperties();
        float enginePower = properties.getEnginePower();
        float bladeLength = 8F;
        float drag = 0.001F;

        this.updateBladeSpeed();

        Vector3d heading = Vector3d.ZERO;
        if(this.isFlying())
        {
            // Calculates the movement based on the input from the controlling passenger
            Vector3d input = this.getInput();
            if(operating && input.length() > 0)
            {
                Vector3d movementForce = input.scale(enginePower).scale(0.05);
                heading = heading.add(movementForce);
            }

            // Makes the helicopter slowly fall due to it tilting during travel
            Vector3d downForce = new Vector3d(0, -1.5F * (this.velocity.multiply(1, 0, 1).scale(20).length() / enginePower), 0).scale(0.05);
            heading = heading.add(downForce);

            // Adds a slight drag to the helicopter as it travels through the air
            Vector3d dragForce = this.velocity.scale(this.velocity.length()).scale(-drag);
            heading = heading.add(dragForce);
        }
        else
        {
            // Slows the helicopter if it's only the ground
            this.velocity = this.velocity.multiply(0.85, 0, 0.85);
        }

        // Adds gravity and the lift needed to counter it
        float gravity = -1.6F;
        float lift = 1.6F * (this.bladeSpeed / 200F);
        heading = heading.add(0, gravity + lift, 0);

        // Lerps the velocity to the new heading
        this.velocity = CommonUtils.lerp(this.velocity, heading, 0.015F);
        this.motion = this.motion.add(this.velocity);

        // Makes the helicopter fall if it's not being operated by a pilot
        if(!operating)
        {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.04, 0));
        }

        if(this.level.isClientSide())
        {
            this.onPostClientUpdate();
        }
    }

    protected Vector3d getInput()
    {
        if(this.getControllingPassenger() != null)
        {
            double strafe = MathHelper.clamp(this.getSideInput(), -1.0F, 1.0F);
            double forward = MathHelper.clamp(this.getForwardInput(), -1.0F, 1.0F);
            Vector3d input = new Vector3d(strafe, 0, forward).yRot((float) Math.toRadians(-this.yRot));
            return input.length() > 1.0 ? input.normalize() : input;
        }
        return Vector3d.ZERO;
    }

    protected void updateBladeSpeed()
    {
        if(this.canDrive() && this.getControllingPassenger() != null)
        {
            //TODO somehow include engine power
            float enginePower = this.getProperties().getEnginePower();
            float maxBladeSpeed = this.getMaxBladeSpeed();
            if(this.bladeSpeed < maxBladeSpeed)
            {
                this.bladeSpeed += this.getLift() > 0 ? (enginePower / 4F) : 0.5F;
                if(this.bladeSpeed > maxBladeSpeed)
                {
                    this.bladeSpeed = maxBladeSpeed;
                }
            }
            else
            {
                this.bladeSpeed *= 0.95F;
            }
        }
        else
        {
            this.bladeSpeed *= 0.95F;
        }
    }

    protected float getMaxBladeSpeed()
    {
        if(this.getLift() > 0)
        {
            return 200F + this.getProperties().getEnginePower();
        }
        else if(this.isFlying())
        {
            if(this.getLift() < 0)
            {
                return 150F;
            }
            return 200F;
        }
        return 80F;
    }

    @Override
    public void onClientUpdate()
    {
        super.onClientUpdate();

        this.prevBladeRotation = this.bladeRotation;
        this.prevBodyRotationX = this.bodyRotationX;
        this.prevBodyRotationY = this.bodyRotationY;
        this.prevBodyRotationZ = this.bodyRotationZ;

        Entity entity = this.getControllingPassenger();
        if(entity != null && entity.equals(Minecraft.getInstance().player))
        {
            ClientPlayerEntity player = (ClientPlayerEntity) entity;
            float lift = VehicleHelper.getLift();
            this.setLift(lift);
            this.setForwardInput(player.zza);
            this.setSideInput(player.xxa);
            PacketHandler.instance.sendToServer(new MessageHelicopterInput(lift, player.zza, player.xxa));
        }
    }

    @OnlyIn(Dist.CLIENT)
    protected void onPostClientUpdate()
    {
        this.bladeRotation += this.bladeSpeed;

        if(this.isFlying())
        {
            this.bodyRotationX = (float) (-this.motion.x * 30F);
            this.bodyRotationZ = (float) (this.motion.z * 30F);
        }
        else
        {
            this.bodyRotationX *= 0.5F;
            this.bodyRotationZ *= 0.5F;
        }
    }

    @Override
    protected void updateEnginePitch()
    {
        float normal = MathHelper.clamp(this.bladeSpeed / 200F, 0.0F, 1.25F) * 0.6F;
        normal += (this.motion.scale(20).length() / this.getProperties().getEnginePower()) * 0.4F;
        this.enginePitch = this.getMinEnginePitch() + (this.getMaxEnginePitch() - this.getMinEnginePitch()) * MathHelper.clamp(normal, 0.0F, 1.0F);
    }

    @Override
    public void addPassenger(Entity passenger)
    {
        super.addPassenger(passenger);
        //passenger.yRot = this.yRot;
    }

    @Override
    protected void updateTurning() {}

    @Override
    public double getPassengersRidingOffset()
    {
        return 0;
    }

    /*
     * Overridden to prevent players from taking fall damage when landing a plane
     */
    @Override
    public boolean causeFallDamage(float distance, float damageMultiplier)
    {
        return false;
    }

    public float getLift()
    {
        return this.lift.get(this);
    }

    public void setLift(float lift)
    {
        this.lift.set(this, lift);
    }

    public float getForwardInput()
    {
        return this.forwardInput.get(this);
    }

    public void setForwardInput(float input)
    {
        this.forwardInput.set(this, input);
    }

    public float getSideInput()
    {
        return this.sideInput.get(this);
    }

    public void setSideInput(float input)
    {
        this.sideInput.set(this, input);
    }

    public boolean isFlying()
    {
        return !this.onGround;
    }

    @Override
    public boolean canChangeWheels()
    {
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    public float getBladeRotation(float partialTicks)
    {
        return this.prevBladeRotation + (this.bladeRotation - this.prevBladeRotation) * partialTicks;
    }

    @OnlyIn(Dist.CLIENT)
    public float getBodyRotationX(float partialTicks)
    {
        return this.prevBodyRotationX + (this.bodyRotationX - this.prevBodyRotationX) * partialTicks;
    }

    @OnlyIn(Dist.CLIENT)
    public float getBodyRotationY(float partialTicks)
    {
        return this.prevBodyRotationY + (this.bodyRotationY - this.prevBodyRotationY) * partialTicks;
    }

    @OnlyIn(Dist.CLIENT)
    public float getBodyRotationZ(float partialTicks)
    {
        return this.prevBodyRotationZ + (this.bodyRotationZ - this.prevBodyRotationZ) * partialTicks;
    }
}
