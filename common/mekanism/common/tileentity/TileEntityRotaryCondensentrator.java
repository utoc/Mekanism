package mekanism.common.tileentity;

import java.util.ArrayList;

import mekanism.api.Object3D;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTransmission;
import mekanism.api.gas.IGasAcceptor;
import mekanism.api.gas.IGasStorage;
import mekanism.api.gas.ITubeConnection;
import mekanism.common.IActiveState;
import mekanism.common.ISustainedTank;
import mekanism.common.PacketHandler;
import mekanism.common.PacketHandler.Transmission;
import mekanism.common.block.BlockMachine.MachineType;
import mekanism.common.network.PacketTileEntity;
import mekanism.common.util.ChargeUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.google.common.io.ByteArrayDataInput;

public class TileEntityRotaryCondensentrator extends TileEntityElectricBlock implements IActiveState, ISustainedTank, IFluidHandler, IGasStorage, IGasAcceptor, ITubeConnection
{
	public GasStack gasTank;
	
	public FluidTank fluidTank;
	
	public static final int MAX_GAS = 10000;
	
	public int updateDelay;
	
	/** 0: gas -> fluid; 1: fluid -> gas */
	public int mode;
	
	public int gasOutput = 16;
	
	public boolean isActive;
	
	public boolean clientActive;
	
	public TileEntityRotaryCondensentrator()
	{
		super("RotaryCondensentrator", MachineType.ROTARY_CONDENSENTRATOR.baseEnergy);
		fluidTank = new FluidTank(10000);
		inventory = new ItemStack[5];
	}
	
	@Override
	public void onUpdate()
	{
		if(worldObj.isRemote)
		{
			if(updateDelay > 0)
			{
				updateDelay--;
				
				if(updateDelay == 0 && clientActive != isActive)
				{
					isActive = clientActive;
					MekanismUtils.updateBlock(worldObj, xCoord, yCoord, zCoord);
				}
			}
		}
		
		if(!worldObj.isRemote)
		{
			if(updateDelay > 0)
			{
				updateDelay--;
					
				if(updateDelay == 0 && clientActive != isActive)
				{
					PacketHandler.sendPacket(Transmission.ALL_CLIENTS, new PacketTileEntity().setParams(Object3D.get(this), getNetworkedData(new ArrayList())));
				}
			}
			
			ChargeUtils.discharge(4, this);
			
			if(mode == 1 && getGas() != null)
			{
				GasStack toSend = new GasStack(getGas().getGas(), Math.min(getGas().amount, gasOutput));
				setGas(new GasStack(getGas().getGas(), getGas().amount - GasTransmission.emitGasToNetwork(toSend, this, MekanismUtils.getLeft(facing))));
				
				TileEntity tileEntity = Object3D.get(this).getFromSide(MekanismUtils.getLeft(facing)).getTileEntity(worldObj);
				
				if(tileEntity instanceof IGasAcceptor)
				{
					if(((IGasAcceptor)tileEntity).canReceiveGas(MekanismUtils.getLeft(facing).getOpposite(), getGas().getGas()))
					{
						int added = ((IGasAcceptor)tileEntity).receiveGas(new GasStack(getGas().getGas(), Math.min(getGas().amount, gasOutput)));
						
						setGas(new GasStack(getGas().getGas(), getGas().amount - added));
					}
				}
			}
		}
	}
	
	@Override
	public void handlePacketData(ByteArrayDataInput dataStream)
	{
		super.handlePacketData(dataStream);
		
		mode = dataStream.readInt();
		
		if(dataStream.readInt() == 1)
		{
			fluidTank.setFluid(new FluidStack(dataStream.readInt(), dataStream.readInt()));
		}
		else {
			fluidTank.setFluid(null);
		}
		
		if(dataStream.readBoolean())
		{
			gasTank = new GasStack(GasRegistry.getGas(dataStream.readInt()), dataStream.readInt());
		}
		else {
			gasTank = null;
		}
		
		
		MekanismUtils.updateBlock(worldObj, xCoord, yCoord, zCoord);
	}
	
	@Override
	public ArrayList getNetworkedData(ArrayList data)
	{
		super.getNetworkedData(data);
		
		data.add(mode);
		
		if(fluidTank.getFluid() != null)
		{
			data.add(1);
			data.add(fluidTank.getFluid().fluidID);
			data.add(fluidTank.getFluid().amount);
		}
		else {
			data.add(0);
		}
		
		if(gasTank != null)
		{
			data.add(true);
			data.add(gasTank.getGas().getID());
			data.add(gasTank.amount);
		}
		else {
			data.add(false);
		}
		
		return data;
	}
	
	@Override
    public void readFromNBT(NBTTagCompound nbtTags)
    {
        super.readFromNBT(nbtTags);

        mode = nbtTags.getInteger("mode");
        gasTank = GasStack.readFromNBT(nbtTags.getCompoundTag("gasTank"));
        
    	if(nbtTags.hasKey("fluidTank"))
    	{
    		fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
    	}
    }

	@Override
    public void writeToNBT(NBTTagCompound nbtTags)
    {
        super.writeToNBT(nbtTags);
        
        nbtTags.setInteger("mode", mode);
        
        if(gasTank != null)
        {
        	nbtTags.setCompoundTag("gasTank", gasTank.write(new NBTTagCompound()));
        }
        
        if(fluidTank.getFluid() != null)
        {
        	nbtTags.setTag("fluidTank", fluidTank.writeToNBT(new NBTTagCompound()));
        }
    }
	
	public int getScaledFluidLevel(int i)
	{
		return fluidTank.getFluid() != null ? fluidTank.getFluid().amount*i / 10000 : 0;
	}
	
	public int getScaledGasLevel(int i)
	{
		return gasTank != null ? gasTank.amount*i / MAX_GAS : 0;
	}
	
	@Override
    public void setActive(boolean active)
    {
    	isActive = active;
    	
    	if(clientActive != active && updateDelay == 0)
    	{
    		PacketHandler.sendPacket(Transmission.ALL_CLIENTS, new PacketTileEntity().setParams(Object3D.get(this), getNetworkedData(new ArrayList())));
    		
    		updateDelay = 10;
    		clientActive = active;
    	}
    }
    
    @Override
    public boolean getActive()
    {
    	return isActive;
    }
    
    @Override
    public boolean renderUpdate()
    {
    	return false;
    }
    
    @Override
    public boolean lightUpdate()
    {
    	return true;
    }

	@Override
	public boolean canTubeConnect(ForgeDirection side)
	{
		return side == MekanismUtils.getLeft(facing);
	}

	@Override
	public int receiveGas(GasStack stack)
	{
		if(gasTank == null || (gasTank != null && gasTank.getGas() == stack.getGas()))
		{
			int stored = getGas() != null ? getGas().amount : 0;
			int toUse = Math.min(getMaxGas()-stored, stack.amount);
			
			setGas(new GasStack(stack.getGas(), stored + toUse));
	    	
	    	return toUse;
		}
		
		return 0;
	}

	@Override
	public boolean canReceiveGas(ForgeDirection side, Gas type)
	{
		return mode == 0 && (getGas() == null || getGas().getGas() == type) && side == MekanismUtils.getLeft(facing);
	}

	@Override
	public GasStack getGas(Object... data)
	{
		return gasTank;
	}

	@Override
	public void setGas(GasStack stack, Object... data)
	{
		if(stack == null || stack.amount == 0)
		{
			gasTank = null;
		}
		else {
			gasTank = new GasStack(stack.getGas(), Math.max(Math.min(stack.amount, getMaxGas()), 0));
		}
		
		MekanismUtils.saveChunk(this);
	}

	@Override
	public int getMaxGas(Object... data)
	{
		return MAX_GAS;
	}

	@Override
	public void setFluidStack(FluidStack fluidStack, Object... data)
	{
		fluidTank.setFluid(fluidStack);
	}

	@Override
	public FluidStack getFluidStack(Object... data)
	{
		return fluidTank.getFluid();
	}

	@Override
	public boolean hasTank(Object... data)
	{
		return true;
	}

	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill)
	{
		if(canFill(from, resource.getFluid()))
		{
			return fluidTank.fill(resource, doFill);
		}
		
		return 0;
	}

	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain)
	{
		if(fluidTank.getFluid() != null && fluidTank.getFluid().getFluid() == resource.getFluid())
		{
			return drain(from, resource.amount, doDrain);
		}
		
		return null;
	}

	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid)
	{
		return mode == 1 && from == MekanismUtils.getRight(facing);
	}

	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid)
	{
		return mode == 0 && from == MekanismUtils.getRight(facing);
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from)
	{
		if(from == MekanismUtils.getRight(facing))
		{
			return new FluidTankInfo[] {fluidTank.getInfo()};
		}
		
		return null;
	}

	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain)
	{
		if(canDrain(from, null))
		{
			return fluidTank.drain(maxDrain, doDrain);
		}
		
		return null;
	}
}