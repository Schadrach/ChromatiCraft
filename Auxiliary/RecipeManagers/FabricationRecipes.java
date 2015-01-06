/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2015
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.ChromatiCraft.Auxiliary.RecipeManagers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import net.minecraft.item.ItemStack;
import Reika.ChromatiCraft.Magic.ElementTagCompound;
import Reika.ChromatiCraft.Registry.ItemMagicRegistry;
import Reika.DragonAPI.Instantiable.Data.KeyedItemStack;

public class FabricationRecipes {

	private static final FabricationRecipes instance = new FabricationRecipes();

	private ArrayList<ItemStack> products = new ArrayList();
	private final Map<KeyedItemStack, ElementTagCompound> data;
	private static final float SCALE = 0.8F;

	public static final int FACTOR = 100;
	public static final int POWER2 = 2;

	public static FabricationRecipes recipes() {
		return instance;
	}

	private FabricationRecipes() {
		data = ItemMagicRegistry.instance.getMap();
		for (ElementTagCompound tag : data.values()) {
			for (int i = 0; i < POWER2; i++)
				tag.square();
			tag.scale(FACTOR);
		}
	}

	public Collection<ItemStack> getItemsFabricableWith(ElementTagCompound tag) {
		Collection<ItemStack> items = new ArrayList();
		for (KeyedItemStack ks : data.keySet()) {
			ElementTagCompound val = data.get(ks).copy().scale(SCALE);
			if (tag.containsAtLeast(val))
				items.add(ks.getItemStack());
		}
		return items;
	}

	public boolean isItemFabricable(ItemStack is, ElementTagCompound tag) {
		KeyedItemStack ks = new KeyedItemStack(is);
		return data.containsKey(ks) && tag.containsAtLeast(this.getItemCost(ks));
	}

	public ElementTagCompound getItemCost(ItemStack is) {
		return this.getItemCost(new KeyedItemStack(is));
	}

	private ElementTagCompound getItemCost(KeyedItemStack is) {
		ElementTagCompound tag = data.get(is);
		return tag != null ? tag.copy().scale(1/SCALE) : null;
	}

}
