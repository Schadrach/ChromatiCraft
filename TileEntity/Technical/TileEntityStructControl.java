/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2016
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.ChromatiCraft.TileEntity.Technical;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.World;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.MinecraftForge;
import Reika.ChromatiCraft.ChromatiCraft;
import Reika.ChromatiCraft.Auxiliary.ChromaStacks;
import Reika.ChromatiCraft.Auxiliary.ChromaStructures;
import Reika.ChromatiCraft.Auxiliary.ChromaStructures.Structures;
import Reika.ChromatiCraft.Auxiliary.MonumentCompletionRitual;
import Reika.ChromatiCraft.Auxiliary.OceanStructure;
import Reika.ChromatiCraft.Auxiliary.ProgressionManager.ProgressStage;
import Reika.ChromatiCraft.Base.TileEntity.InventoriedChromaticBase;
import Reika.ChromatiCraft.Block.Worldgen.BlockLootChest;
import Reika.ChromatiCraft.Block.Worldgen.BlockLootChest.LootChestAccessEvent;
import Reika.ChromatiCraft.Block.Worldgen.BlockStructureShield;
import Reika.ChromatiCraft.Block.Worldgen.BlockStructureShield.BlockType;
import Reika.ChromatiCraft.Registry.ChromaBlocks;
import Reika.ChromatiCraft.Registry.ChromaItems;
import Reika.ChromatiCraft.Registry.ChromaPackets;
import Reika.ChromatiCraft.Registry.ChromaSounds;
import Reika.ChromatiCraft.Registry.ChromaTiles;
import Reika.ChromatiCraft.Registry.CrystalElement;
import Reika.ChromatiCraft.Render.Particle.EntityCenterBlurFX;
import Reika.ChromatiCraft.World.Dimension.ChunkProviderChroma;
import Reika.ChromatiCraft.World.Dimension.Structure.MonumentGenerator;
import Reika.ChromatiCraft.World.IWG.DungeonGenerator;
import Reika.DragonAPI.DragonAPICore;
import Reika.DragonAPI.Instantiable.Data.BlockStruct.BlockArray;
import Reika.DragonAPI.Instantiable.Data.BlockStruct.FilledBlockArray;
import Reika.DragonAPI.Instantiable.Data.Immutable.Coordinate;
import Reika.DragonAPI.Instantiable.Data.Immutable.WorldChunk;
import Reika.DragonAPI.Instantiable.Data.Immutable.WorldLocation;
import Reika.DragonAPI.Instantiable.Data.Maps.MultiMap;
import Reika.DragonAPI.Interfaces.TileEntity.BreakAction;
import Reika.DragonAPI.Interfaces.TileEntity.HitAction;
import Reika.DragonAPI.Interfaces.TileEntity.InertIInv;
import Reika.DragonAPI.Interfaces.TileEntity.PlayerBreakHook;
import Reika.DragonAPI.Libraries.ReikaAABBHelper;
import Reika.DragonAPI.Libraries.ReikaInventoryHelper;
import Reika.DragonAPI.Libraries.IO.ReikaPacketHelper;
import Reika.DragonAPI.Libraries.IO.ReikaRenderHelper;
import Reika.DragonAPI.Libraries.IO.ReikaSoundHelper;
import Reika.DragonAPI.Libraries.Java.ReikaJavaLibrary;
import Reika.DragonAPI.Libraries.MathSci.ReikaMathLibrary;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TileEntityStructControl extends InventoriedChromaticBase implements BreakAction, HitAction, InertIInv, PlayerBreakHook {

	private Structures struct;
	private FilledBlockArray blocks;
	private CrystalElement color;
	private final EnumMap<CrystalElement, Coordinate> crystals = new EnumMap(CrystalElement.class);
	private boolean triggered = false;
	private boolean regenned = false;
	private int trapTick = 0;

	private UUID lastTriggerPlayer;

	private boolean isMonument;
	private boolean triggeredMonument;
	private MonumentCompletionRitual monument;

	@Override
	public ChromaTiles getTile() {
		return ChromaTiles.STRUCTCONTROL;
	}

	@Override
	public void updateEntity(World world, int x, int y, int z, int meta) {
		if (world.isRemote && struct != null && world.getClosestPlayer(x+0.5, y+0.5, z+0.5, 12) != null)
			this.spawnParticles(world, x, y, z);

		if (isMonument && triggeredMonument && monument != null) {
			monument.tick();
		}
		if (isMonument && DragonAPICore.debugtest) {
			for (int i = 0; i < 16; i++) {
				Coordinate c = TileEntityDimensionCore.getLocation(CrystalElement.elements[i]);
				c = c.offset(x, y, z);
				c.setBlock(world, ChromaTiles.DIMENSIONCORE.getBlock(), ChromaTiles.DIMENSIONCORE.getBlockMetadata());
				((TileEntityDimensionCore)c.getTileEntity(world)).setPlacer(world.getClosestPlayer(x, y, z, -1));
				((TileEntityDimensionCore)c.getTileEntity(world)).prime(true);
				((TileEntityDimensionCore)c.getTileEntity(world)).setColor(CrystalElement.elements[i]);
			}
			MonumentGenerator gen = ChunkProviderChroma.getMonumentGenerator();
			Map<Coordinate, Block> map = gen.getMineralBlocks();
			for (Coordinate c : map.keySet()) {
				c.setBlock(world, map.get(c));
			}
		}

		if (!triggered && struct != null && this.getTicksExisted()%4 == 0) {
			List<EntityPlayer> li = world.playerEntities;
			for (EntityPlayer ep : li) {
				if (ep.boundingBox.intersectsWith(this.getBox(x, y, z))) {
					this.onPlayerProximity(world, x, y, z, ep);
					lastTriggerPlayer = ep.getPersistentID();
				}
			}
		}
		//triggered = false;
		if (struct == Structures.OCEAN) {
			if (trapTick > 0) {
				trapTick--;
			}
			else {
				this.resetOceanTrap();
			}
		}
	}

	@Override
	public void onHit(World world, int x, int y, int z, EntityPlayer ep) {
		this.trigger(x, y, z, ep);
	}

	private void trigger(int x, int y, int z, EntityPlayer ep) {
		if (struct != null) {
			switch(struct) {
				case CAVERN:
					break;
				case BURROW:
					break;
				case OCEAN:
					if ((y == yCoord || y == yCoord-1) && Math.abs(x-xCoord) <= 3 && Math.abs(z-zCoord) <= 3)
						this.triggerOceanTrap(ep);
					break;
				case DESERT:
					break;
				default:
					break;
			}
		}
	}

	private void triggerOceanTrap(EntityPlayer ep) {
		BlockArray arr = OceanStructure.getPitCover(xCoord, yCoord, zCoord);
		for (int i = 0; i < arr.getSize(); i++) {
			Coordinate c = arr.getNthBlock(i);
			int dx = c.xCoord;
			int dy = c.yCoord;
			int dz = c.zCoord;
			worldObj.setBlock(dx, dy, dz, Blocks.air);
		}
		ChromaSounds.TRAP.playSound(ep, 1, 1);
		trapTick = 40;
		this.disableJetpack(ep);
		//ep.addPotionEffect(new PotionEffect(Potion.poison.id, 600, 2));
	}

	private void disableJetpack(EntityPlayer ep) {
		ItemStack chest = ep.getCurrentArmor(2);
		if (chest != null) {
			//no idea how to do this
		}
	}

	private void resetOceanTrap() {
		BlockArray arr = OceanStructure.getPitCover(xCoord, yCoord, zCoord);
		for (int i = 0; i < arr.getSize(); i++) {
			Coordinate c = arr.getNthBlock(i);
			int dx = c.xCoord;
			int dy = c.yCoord;
			int dz = c.zCoord;
			worldObj.setBlock(dx, dy, dz, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.CLOAK.metadata, 3);
		}
	}

	private AxisAlignedBB getBox(int x, int y, int z) {
		AxisAlignedBB aabb = ReikaAABBHelper.getBlockAABB(x, y, z);
		switch(struct) {
			case CAVERN:
				return aabb.expand(3, 1, 3);
			case BURROW:
				return aabb.offset(2, 1, 0).expand(0.5, 0.5, 0.5);
			case OCEAN:
				return aabb.offset(0, 2, 0).expand(1, 1, 1);
			case DESERT:
				return aabb.expand(5, 2, 5).offset(0, 1, 0);
			default:
				return aabb;
		}
	}

	private void onPlayerProximity(World world, int x, int y, int z, EntityPlayer ep) {
		if (struct != null)
			this.calcCrystals(world, x, y, z);
		switch(struct) {
			case CAVERN:
				if (!world.isRemote) {
					world.setBlock(x+7, y, z, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.CLOAK.metadata, 3);
					world.setBlock(x+7, y-1, z, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.CLOAK.metadata, 3);
				}
				ChromaSounds.TRAP.playSound(ep, 1, 1);
				break;
			case BURROW:
				world.setBlockMetadataWithNotify(x+2, y+1, z, BlockStructureShield.BlockType.CRACK.metadata, 3);
				ReikaSoundHelper.playBreakSound(world, x+2, y+1, z, Blocks.stone);
				if (world.isRemote)
					ReikaRenderHelper.spawnDropParticles(world, x+2, y+1, z, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.STONE.metadata);
				break;
			case OCEAN:
				BlockArray blocks = OceanStructure.getCovers(x, y, z);
				for (int i = 0; i < blocks.getSize(); i++) {
					Coordinate c = blocks.getNthBlock(i);
					int dx = c.xCoord;
					int dy = c.yCoord;
					int dz = c.zCoord;
					world.setBlockMetadataWithNotify(dx, dy, dz, BlockType.CRACKS.metadata, 3);
				}
				int r = 1;
				for (int i = -r; i <= r; i++) {
					for (int k = -r; k <= r; k++) {
						ReikaSoundHelper.playBreakSound(world, x+i, y+1, z+k, Blocks.stone);
						if (world.isRemote) {
							ReikaRenderHelper.spawnDropParticles(world, x+i, y+3, z+k, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.STONE.metadata);
						}
					}
				}
				ChromaSounds.TRAP.playSound(ep, 1, 1);
				break;
			case DESERT:
				ChromaSounds.TRAP.playSound(ep, 1, 1);
				ReikaSoundHelper.playBreakSound(world, x, y+2, z, Blocks.stone);
				if (world.isRemote)
					ReikaRenderHelper.spawnDropParticles(world, x, y+2, z, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.STONE.metadata);

				x -= 7;
				z -= 7;
				y -= 3;

				world.setBlock(x+5, y+9, z+5, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.STONE.metadata, 3);
				world.setBlock(x+5, y+9, z+9, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.STONE.metadata, 3);
				world.setBlock(x+9, y+9, z+5, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.STONE.metadata, 3);
				world.setBlock(x+9, y+9, z+9, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.STONE.metadata, 3);

				world.setBlock(x+5, y+5, z+5, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.CLOAK.metadata, 3);
				world.setBlock(x+5, y+5, z+9, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.CLOAK.metadata, 3);
				world.setBlock(x+9, y+5, z+5, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.CLOAK.metadata, 3);
				world.setBlock(x+9, y+5, z+9, ChromaBlocks.STRUCTSHIELD.getBlockInstance(), BlockType.CLOAK.metadata, 3);

				world.setBlockMetadataWithNotify(x+12, y+5, z+6, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+12, y+5, z+7, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+12, y+5, z+8, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+12, y+6, z+7, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+11, y+5, z+6, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+11, y+5, z+7, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+11, y+5, z+8, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+8, y+5, z+11, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+8, y+5, z+12, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+8, y+5, z+2, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+8, y+5, z+3, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+7, y+5, z+11, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+7, y+5, z+12, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+2, y+5, z+6, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+2, y+5, z+7, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+2, y+5, z+8, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+2, y+6, z+7, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+3, y+5, z+6, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+3, y+5, z+7, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+3, y+5, z+8, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+3, y+6, z+7, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+6, y+5, z+2, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+6, y+5, z+3, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+6, y+5, z+11, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+6, y+5, z+12, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+7, y+5, z+2, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+7, y+5, z+3, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+7, y+6, z+2, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+7, y+6, z+3, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+7, y+6, z+11, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+7, y+6, z+12, BlockType.CRACK.metadata, 3);
				world.setBlockMetadataWithNotify(x+11, y+6, z+7, BlockType.CRACK.metadata, 3);
				break;
			default:
				break;
		}
		this.openUpperChests();
		ProgressStage.ANYSTRUCT.stepPlayerTo(ep);
		this.getProgressStage().stepPlayerTo(ep);
		triggered = true;
	}

	public void reopenStructure() {
		boolean flag = false;
		if (struct != null) {
			switch(struct) {
				case CAVERN:
					worldObj.setBlock(xCoord+7, yCoord, zCoord, Blocks.air);
					worldObj.setBlock(xCoord+7, yCoord-1, zCoord, Blocks.air);
					flag = true;
					break;
				case DESERT:
					int x = xCoord-7;
					int z = zCoord-7;
					int y = yCoord-3;

					worldObj.setBlock(x+5, y+9, z+5, Blocks.air);
					worldObj.setBlock(x+5, y+9, z+9, Blocks.air);
					worldObj.setBlock(x+9, y+9, z+5, Blocks.air);
					worldObj.setBlock(x+9, y+9, z+9, Blocks.air);

					worldObj.setBlock(x+5, y+5, z+5, Blocks.sandstone);
					worldObj.setBlock(x+5, y+5, z+9, Blocks.sandstone);
					worldObj.setBlock(x+9, y+5, z+5, Blocks.sandstone);
					worldObj.setBlock(x+9, y+5, z+9, Blocks.sandstone);
					flag = true;
					break;
				default:
					break;
			}
		}
		if (flag) {
			triggered = false;
			lastTriggerPlayer = null;
		}
	}

	private void openUpperChests() {
		switch(struct) {
			case CAVERN:
				if (blocks != null) {
					for (int i = 0; i < blocks.getSize(); i++) {
						Coordinate c = blocks.getNthBlock(i);
						int x = c.xCoord;
						int y = c.yCoord;
						int z = c.zCoord;
						if (y > yCoord && worldObj.getBlock(x, y, z) == ChromaBlocks.LOOTCHEST.getBlockInstance()) {
							worldObj.setBlockMetadataWithNotify(x, y, z, worldObj.getBlockMetadata(x, y, z)%8, 3);
							ReikaSoundHelper.playBreakSound(worldObj, x, y, z, Blocks.stone);
						}
					}
				}
				//worldObj.setBlockMetadataWithNotify(xCoord+7, yCoord, zCoord, worldObj.getBlockMetadata(xCoord+7, yCoord, zCoord)%8, 3);
				//worldObj.setBlockMetadataWithNotify(xCoord+7, yCoord-1, zCoord, worldObj.getBlockMetadata(xCoord+7, yCoord-1, zCoord)%8, 3);
				break;
			case BURROW:
				if (blocks != null) {
					for (int i = 0; i < blocks.getSize(); i++) {
						Coordinate c = blocks.getNthBlock(i);
						int x = c.xCoord+5;
						int y = c.yCoord+8;
						int z = c.zCoord+2;
						if (y > yCoord && worldObj.getBlock(x, y, z) == ChromaBlocks.LOOTCHEST.getBlockInstance()) {
							worldObj.setBlockMetadataWithNotify(x, y, z, worldObj.getBlockMetadata(x, y, z)%8, 3);
							ReikaSoundHelper.playBreakSound(worldObj, x, y, z, Blocks.stone);
						}
					}
				}
				break;
			case OCEAN:
				if (blocks != null) {
					for (int i = 0; i < blocks.getSize(); i++) {
						Coordinate c = blocks.getNthBlock(i);
						int x = c.xCoord;//-3;
						int y = c.yCoord;//-5;
						int z = c.zCoord;//-3;
						if (y > yCoord && worldObj.getBlock(x, y, z) == ChromaBlocks.LOOTCHEST.getBlockInstance()) {
							worldObj.setBlockMetadataWithNotify(x, y, z, worldObj.getBlockMetadata(x, y, z)%8, 3);
							ReikaSoundHelper.playBreakSound(worldObj, x, y, z, Blocks.stone);
						}
					}
				}
				break;
			case DESERT:
				if (blocks != null) {
					for (int i = 0; i < blocks.getSize(); i++) {
						Coordinate c = blocks.getNthBlock(i);
						int x = c.xCoord-7;
						int y = c.yCoord-3;
						int z = c.zCoord-7;
						if (y > yCoord && worldObj.getBlock(x, y, z) == ChromaBlocks.LOOTCHEST.getBlockInstance()) {
							worldObj.setBlockMetadataWithNotify(x, y, z, worldObj.getBlockMetadata(x, y, z)%8, 3);
							ReikaSoundHelper.playBreakSound(worldObj, x, y, z, Blocks.stone);
						}
					}
				}
				break;
			default:
				break;
		}
	}

	@Override
	public void onFirstTick(World world, int x, int y, int z) {
		if (struct != null) {
			this.calcCrystals(world, x, y, z);
			this.regenerate();
			DungeonGenerator.instance.generateStructure(struct, this);
		}
		LootChestWatcher.instance.cache(this);
		this.syncAllData(true);
	}

	private void regenerate() {
		if (regenned)
			return;
		if (struct != null) {
			FilledBlockArray copy = (FilledBlockArray)blocks.copy();
			if (struct == Structures.BURROW) {
				copy.offset(5, 8, 2);
			}
			if (struct == Structures.DESERT) {
				copy.offset(-7, -3, -7);
			}
			copy.placeExcept(new Coordinate(this), 3);
			DungeonGenerator.populateChests(struct, copy, rand);
			if (struct == Structures.OCEAN) {
				BlockLootChest.setMaxReach(worldObj, xCoord-2, yCoord-1, zCoord, 2.5);
				BlockLootChest.setMaxReach(worldObj, xCoord, yCoord-1, zCoord-1, 2.5);
			}
		}
		regenned = true;
		triggered = false;
	}

	@Override
	protected void animateWithTick(World world, int x, int y, int z) {

	}

	@SideOnly(Side.CLIENT)
	private void spawnParticles(World world, int x, int y, int z) {
		switch(struct) {
			case CAVERN:
				for (int i = -1; i <= 1; i += 2) {
					if (!crystals.isEmpty()) {
						CrystalElement e = ReikaJavaLibrary.getRandomListEntry(rand, (ArrayList<CrystalElement>)new ArrayList(crystals.keySet()));
						Coordinate c = crystals.get(e);
						double dd = ReikaMathLibrary.py3d(c.xCoord, c.yCoord, c.zCoord);
						double v = 0.2;
						double vx = -c.xCoord/dd*v;
						double vy = -c.yCoord/dd*v+0.15*i;
						double vz = -c.zCoord/dd*v;
						//ReikaJavaLibrary.pConsole(vx, e == CrystalElement.BROWN && x == 24 && z == 383);
						c = c.offset(x, y, z);
						EntityCenterBlurFX fx = new EntityCenterBlurFX(e, world, c.xCoord+0.5, c.yCoord+0.5, c.zCoord+0.5, vx, vy, vz);
						fx.setGravity(0.1F*i).setLife(60);
						//fx.noClip = false;
						Minecraft.getMinecraft().effectRenderer.addEffect(fx);
					}
				}
				break;
			case BURROW:
				if (world.getBlock(x, y-2, z) == ChromaBlocks.LAMP.getBlockInstance()) {
					double dx = x+0.5;
					double dz = z+0.5;
					double d = 0.4;
					switch(rand.nextInt(5)) {
						case 0:
							break;
						case 1:
							dx += d;
							break;
						case 2:
							dx -= d;
							break;
						case 3:
							dz += d;
							break;
						case 4:
							dz -= d;
							break;
					}
					EntityCenterBlurFX fx = new EntityCenterBlurFX(color, world, dx, y-2+0.5, dz, 0, 0, 0).setGravity(-0.05F);
					Minecraft.getMinecraft().effectRenderer.addEffect(fx);
				}
				break;
			case OCEAN:
				break;
			case DESERT:
				break;
			default:
				break;
		}
	}

	public void generate(Structures s, CrystalElement e) {
		if (!s.isNatural())
			throw new IllegalArgumentException("You cannot generate a structure control in the wrong structure!");
		struct = s;
		color = e;
		WeightedRandomChestContent[] loot = ChestGenHooks.getItems(ChestGenHooks.STRONGHOLD_LIBRARY, rand);
		WeightedRandomChestContent.generateChestContents(rand, loot, this, ChestGenHooks.getCount(ChestGenHooks.STRONGHOLD_LIBRARY, rand));
		int n = 1+rand.nextInt(4)*(1+rand.nextInt(2));
		//ReikaJavaLibrary.pConsole("genning "+n+" fragments @ "+xCoord+", "+zCoord);
		for (int i = 0; i < n; i++) {
			ReikaInventoryHelper.addToIInv(ChromaItems.FRAGMENT.getItemInstance(), this);
		}
		switch(struct) {
			case CAVERN:
				ReikaInventoryHelper.addToIInv(ChromaStacks.cavernLoot, this, true);
				break;
			case BURROW:
				ReikaInventoryHelper.addToIInv(ChromaStacks.burrowLoot, this, true);
				break;
			case OCEAN:
				ReikaInventoryHelper.addToIInv(ChromaStacks.oceanLoot, this, true);
				break;
			case DESERT:
				ReikaInventoryHelper.addToIInv(ChromaStacks.desertLoot, this, true);
				break;
			default:
				break;
		}
	}

	private void calcCrystals(World world, int x, int y, int z) {
		switch(struct) {
			case CAVERN:
				blocks = ChromaStructures.getCavernStructure(world, x, y, z);
				break;
			case BURROW:
				blocks = ChromaStructures.getBurrowStructure(world, x, y, z, color);
				break;
			case OCEAN:
				blocks = ChromaStructures.getOceanStructure(world, x, y, z);
				break;
			case DESERT:
				blocks = ChromaStructures.getDesertStructure(world, x, y, z);
				break;
			default:
				break;
		}
		if (blocks != null) {
			for (int i = 0; i < blocks.getSize(); i++) {
				Coordinate c1 = blocks.getNthBlock(i);
				if (worldObj.getBlock(c1.xCoord, c1.yCoord, c1.zCoord) == ChromaBlocks.CRYSTAL.getBlockInstance()) {
					Coordinate c = new Coordinate(c1.xCoord-xCoord, c1.yCoord-yCoord, c1.zCoord-zCoord);
					CrystalElement e = CrystalElement.elements[worldObj.getBlockMetadata(c1.xCoord, c1.yCoord, c1.zCoord)];
					crystals.put(e, c);
					//ReikaJavaLibrary.pConsole(c+":"+world.isRemote, e == CrystalElement.RED && x == 24 && z == 383);
				}
			}
		}
	}

	@Override
	public void breakBlock() {
		if (struct != null) {
			switch(struct) {
				case CAVERN:
					if (blocks != null) {
						for (int i = 0; i < blocks.getSize(); i++) {
							Coordinate c = blocks.getNthBlock(i);
							int x = c.xCoord;
							int y = c.yCoord;
							int z = c.zCoord;
							if (worldObj.getBlock(x, y, z) == ChromaBlocks.STRUCTSHIELD.getBlockInstance())
								worldObj.setBlockMetadataWithNotify(x, y, z, worldObj.getBlockMetadata(x, y, z)%8, 3);
							else if (worldObj.getBlock(x, y, z) == ChromaBlocks.LOOTCHEST.getBlockInstance()) {
								worldObj.setBlockMetadataWithNotify(x, y, z, worldObj.getBlockMetadata(x, y, z)%8, 3);
								ReikaSoundHelper.playBreakSound(worldObj, x, y, z, Blocks.stone);
							}
						}
					}
					worldObj.setBlockMetadataWithNotify(xCoord+7, yCoord, zCoord, worldObj.getBlockMetadata(xCoord+7, yCoord, zCoord)%8, 3);
					worldObj.setBlockMetadataWithNotify(xCoord+7, yCoord-1, zCoord, worldObj.getBlockMetadata(xCoord+7, yCoord-1, zCoord)%8, 3);
					break;
				case BURROW:
					if (blocks != null) {
						for (int i = 0; i < blocks.getSize(); i++) {
							Coordinate c = blocks.getNthBlock(i);
							int x = c.xCoord+5;
							int y = c.yCoord+8;
							int z = c.zCoord+2;
							if (worldObj.getBlock(x, y, z) == ChromaBlocks.STRUCTSHIELD.getBlockInstance())
								worldObj.setBlockMetadataWithNotify(x, y, z, worldObj.getBlockMetadata(x, y, z)%8, 3);
							else if (worldObj.getBlock(x, y, z) == ChromaBlocks.LOOTCHEST.getBlockInstance()) {
								worldObj.setBlockMetadataWithNotify(x, y, z, worldObj.getBlockMetadata(x, y, z)%8, 3);
								ReikaSoundHelper.playBreakSound(worldObj, x, y, z, Blocks.stone);
							}
						}
					}
					break;
				case OCEAN:
					this.triggerOceanTrap(worldObj.getClosestPlayer(xCoord+0.5, yCoord+0.5, zCoord+0.5, 8));
					if (blocks != null) {
						for (int i = 0; i < blocks.getSize(); i++) {
							Coordinate c = blocks.getNthBlock(i);
							int x = c.xCoord;//-3;
							int y = c.yCoord;//-5;
							int z = c.zCoord;//-3;
							if (worldObj.getBlock(x, y, z) == ChromaBlocks.STRUCTSHIELD.getBlockInstance())
								worldObj.setBlockMetadataWithNotify(x, y, z, worldObj.getBlockMetadata(x, y, z)%8, 3);
							else if (worldObj.getBlock(x, y, z) == ChromaBlocks.LOOTCHEST.getBlockInstance()) {
								worldObj.setBlockMetadataWithNotify(x, y, z, worldObj.getBlockMetadata(x, y, z)%8, 3);
								ReikaSoundHelper.playBreakSound(worldObj, x, y, z, Blocks.stone);
							}
						}
					}
					break;
				case DESERT:
					if (blocks != null) {
						for (int i = 0; i < blocks.getSize(); i++) {
							Coordinate c = blocks.getNthBlock(i);
							int x = c.xCoord-7;
							int y = c.yCoord-3;
							int z = c.zCoord-7;
							if (worldObj.getBlock(x, y, z) == ChromaBlocks.STRUCTSHIELD.getBlockInstance())
								worldObj.setBlockMetadataWithNotify(x, y, z, worldObj.getBlockMetadata(x, y, z)%8, 3);
							else if (worldObj.getBlock(x, y, z) == ChromaBlocks.LOOTCHEST.getBlockInstance()) {
								worldObj.setBlockMetadataWithNotify(x, y, z, worldObj.getBlockMetadata(x, y, z)%8, 3);
								ReikaSoundHelper.playBreakSound(worldObj, x, y, z, Blocks.stone);
							}
						}
					}
					break;
				default:
					break;
			}
		}
		LootChestWatcher.instance.remove(this);
		if (struct != null)
			DungeonGenerator.instance.deleteStructure(struct, this);
		if (monument != null && monument.isRunning() && !monument.isComplete()) {
			monument.endRitual();
		}
	}

	public int getBrightness() {
		return struct == Structures.BURROW || struct == Structures.DESERT ? 15 : 0;
	}

	@Override
	public void writeToNBT(NBTTagCompound NBT) {
		super.writeToNBT(NBT);

		if (struct != null)
			NBT.setString("struct", struct.name());

		if (lastTriggerPlayer != null)
			NBT.setString("lastPlayer", lastTriggerPlayer.toString());

		NBT.setInteger("color", this.getColor().ordinal());

		NBT.setBoolean("trigger", triggered);
		NBT.setBoolean("regen", regenned);

		NBT.setInteger("ttick", trapTick);

		NBT.setBoolean("monument", isMonument);
		NBT.setBoolean("monument_t", triggeredMonument);
	}

	@Override
	public void readFromNBT(NBTTagCompound NBT) {
		super.readFromNBT(NBT);

		if (NBT.hasKey("struct"))
			struct = Structures.valueOf(NBT.getString("struct"));

		if (NBT.hasKey("lastPlayer"))
			lastTriggerPlayer = UUID.fromString(NBT.getString("lastPlayer"));

		color = CrystalElement.elements[NBT.getInteger("color")];

		triggered = NBT.getBoolean("trigger");
		regenned = NBT.getBoolean("regen");

		trapTick = NBT.getInteger("ttick");

		isMonument = NBT.getBoolean("monument");
		triggeredMonument = NBT.getBoolean("monument_t");
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack is, int side) {
		return false;
	}

	@Override
	public int getSizeInventory() {
		return 27;
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack is) {
		return false;
	}

	public CrystalElement getColor() {
		return color != null ? color : CrystalElement.WHITE;
	}

	@SideOnly(Side.CLIENT)
	public boolean isVisible() {
		return true;//ProgressionManager.instance.isPlayerAtStage(Minecraft.getMinecraft().thePlayer, this.getProgressStage());
	}

	public boolean isBreakable(EntityPlayer ep) {
		return !isMonument && this.breakByPlayer(ep);
	}

	private ProgressStage getProgressStage() {
		switch(struct) {
			case CAVERN:
				return ProgressStage.CAVERN;
			case BURROW:
				return ProgressStage.BURROW;
			case OCEAN:
				return ProgressStage.OCEAN;
			case DESERT:
				return ProgressStage.DESERTSTRUCT;
			default:
				return null;
		}
	}

	public void setMonument() {
		isMonument = true;
		this.syncAllData(false);
	}

	public boolean isMonument() {
		return isMonument;
	}

	public void endMonumentRitual() {
		if (triggeredMonument) {
			triggeredMonument = false;
			if (monument != null && monument.isRunning()) {
				monument.endRitual();
			}
			monument = null;
			ReikaPacketHelper.sendDataPacketWithRadius(ChromatiCraft.packetChannel, ChromaPackets.MONUMENTEND.ordinal(), this, Integer.MAX_VALUE);
		}
	}

	public boolean triggerMonument(EntityPlayer ep) {
		triggeredMonument = true;
		monument = new MonumentCompletionRitual(worldObj, xCoord, yCoord, zCoord, ep);
		if (monument.doChecks(false)) {
			monument.start();
			return true;
		}
		return false;
	}

	public boolean isTriggerPlayer(EntityPlayer ep) {
		return ep.getUniqueID().equals(lastTriggerPlayer);
	}

	public static class LootChestWatcher {

		public static final LootChestWatcher instance = new LootChestWatcher();

		private final MultiMap<WorldChunk, WorldLocation> cache = new MultiMap();

		private LootChestWatcher() {

		}

		private void cache(TileEntityStructControl te) {
			WorldLocation loc = new WorldLocation(te);
			WorldChunk wc = new WorldChunk(te.worldObj, te.worldObj.getChunkFromBlockCoords(te.xCoord, te.zCoord));
			cache.addValue(wc, loc);
		}

		private void remove(TileEntityStructControl te) {
			WorldLocation loc = new WorldLocation(te);
			WorldChunk wc = new WorldChunk(te.worldObj, te.worldObj.getChunkFromBlockCoords(te.xCoord, te.zCoord));
			cache.remove(wc, loc);
		}

		@SubscribeEvent
		public void onAccess(LootChestAccessEvent evt) {
			int x = evt.x;
			int z = evt.z;
			int r = 2;
			for (int i = -r; i <= r; i++) {
				for (int j = -r; j <= r; j++) {
					int dx = x+i*16;
					int dz = z+i*16;
					WorldChunk wc = new WorldChunk(evt.world, evt.world.getChunkFromBlockCoords(dx, dz));
					for (WorldLocation loc : cache.get(wc)) {
						TileEntity tile = evt.world.getTileEntity(loc.xCoord, loc.yCoord, loc.zCoord);
						if (tile instanceof TileEntityStructControl) {
							TileEntityStructControl te = (TileEntityStructControl)tile;
							te.trigger(evt.x, evt.y, evt.z, evt.player);
						}
					}
				}
			}
		}

	}

	static {
		MinecraftForge.EVENT_BUS.register(LootChestWatcher.instance);
	}

	@Override
	public boolean breakByPlayer(EntityPlayer ep) {
		if (struct == Structures.OCEAN) {
			return ep != null && ep.getDistance(xCoord+0.5, yCoord+0.5, zCoord+0.5) <= 2.5;
		}
		return true;
	}

	/** Is often null! */
	public Structures getStructureType() {
		return struct;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return INFINITE_EXTENT_AABB;
	}

}
