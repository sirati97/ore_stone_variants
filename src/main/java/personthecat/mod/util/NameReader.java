package personthecat.mod.util;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class NameReader
{
	//I was doing these things a lot.
	public static String getOre(String name)
	{		
		name = name.toLowerCase().replaceAll("tile.", "").replaceAll("lit_", "").replaceAll(".name", "").replaceAll("_custom", "");
		String[] nameCorrector = name.split("_");
		
		//Keep taking off the last piece until it reads "ore."
		while (!nameCorrector[nameCorrector.length - 1].equals("ore"))
		{			
			if (nameCorrector.length < 1) return null;
			
			name = nameCorrector[0];
			
			for (int i = 1; i < nameCorrector.length - 1; i++)
			{
				name = name + "_" + nameCorrector[i];
			}
			
			nameCorrector = name.split("_");
		}
		
		return name;
	}
	
	public static String getOreIgnoreDense(String name)
	{
		return getOre(name).replaceAll("dense_", "");
	}
	
	public static String getMod(String name)
	{
		String[] nameSplit = getOreIgnoreDense(name).split("_");
		
		return (nameSplit.length == 3) ? nameSplit[0] : "vanilla";
	}
	
	public static String getOreWithoutMod(String name)
	{
		return getOreIgnoreDense(name).replaceAll(getMod(name) + "_", "");
	}
	
	public static String getOreFromPath(String path)
	{
		String[] nameFinder = path.split("/");
		
		return nameFinder[nameFinder.length - 1];
	}
	
	public static boolean isDynamic(Block blockIn)
	{
		return blockIn.getUnlocalizedName().contains("_custom");
	}
	
	public static boolean isDense(Block blockIn)
	{
		return blockIn.getRegistryName().getResourcePath().contains("dense_");
	}
	
	public static boolean isLit(Block blockIn)
	{
		return blockIn.getRegistryName().getResourcePath().contains("lit_");
	}
	
	public static Block getDenseVariant(Block fromBlock)
	{
		return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(Reference.MODID, "dense_" + fromBlock.getRegistryName().getResourcePath()));
	}
	
	public static Block getNormalVariant(Block fromBlock)
	{
		return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(Reference.MODID, fromBlock.getRegistryName().getResourcePath().replaceAll("dense_", "")));
	}
	
	public static Block getLitVariant(Block fromBlock)
	{
		return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(Reference.MODID, fromBlock.getRegistryName().getResourcePath().replaceAll("redstone", "lit_redstone")));
	}
	
	public static Block getUnlitVariant(Block fromBlock)
	{
		return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(Reference.MODID, fromBlock.getRegistryName().getResourcePath().replaceAll("lit_", "")));
	}
}
