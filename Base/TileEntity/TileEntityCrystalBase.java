/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2015
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.ChromatiCraft.Base.TileEntity;

import net.minecraft.world.World;
import Reika.ChromatiCraft.Magic.CrystalNetworker;
import Reika.ChromatiCraft.Magic.Interfaces.CrystalNetworkTile;

public abstract class TileEntityCrystalBase extends TileEntityChromaticBase implements CrystalNetworkTile {

	@Override
	protected void onFirstTick(World world, int x, int y, int z) {
		this.cachePosition();
	}

	@Override
	public final void cachePosition() {
		CrystalNetworker.instance.addTile(this);
	}

	public final void removeFromCache() {
		CrystalNetworker.instance.removeTile(this);
	}

	public final double getDistanceSqTo(double x, double y, double z) {
		double dx = x-xCoord;
		double dy = y-yCoord;
		double dz = z-zCoord;
		return dx*dx+dy*dy+dz*dz;
	}

	@Override
	public final World getWorld() {
		return worldObj;
	}

	@Override
	public final int getX() {
		return xCoord;
	}

	@Override
	public final int getY() {
		return yCoord;
	}

	@Override
	public final int getZ() {
		return zCoord;
	}

	@Override
	public int getUpdatePacketRadius() {
		return 512;
	}

}
