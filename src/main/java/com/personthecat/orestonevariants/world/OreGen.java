package com.personthecat.orestonevariants.world;

import com.personthecat.orestonevariants.Main;
import com.personthecat.orestonevariants.blocks.BaseOreVariant;
import com.personthecat.orestonevariants.config.Cfg;
import com.personthecat.orestonevariants.properties.OreProperties;
import com.personthecat.orestonevariants.properties.WorldGenProperties;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.OreBlock;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStage.Decoration;
import net.minecraft.world.gen.feature.*;
import net.minecraft.world.gen.placement.CountRangeConfig;
import net.minecraft.world.gen.placement.Placement;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.util.TriConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.personthecat.orestonevariants.util.CommonMethods.*;

public class OreGen {
    /** A cleaner reference to Decoration#UNDERGROUND_ORES. */
    private static final Decoration ORE_DEC = Decoration.UNDERGROUND_ORES;
    /** A cleaner reference to Placement#COUNT_RANGE. */
    private static final Placement COUNT_RNG = Placement.COUNT_RANGE;

    /** Handles all ore generation features for this mod. */
    public static void setupOreFeatures() {
        if (!(Cfg.enableVanillaOres.get() && Cfg.enableVanillaStone.get())) {
            disableGenerators();
        }
        if (Cfg.enableOSVOres.get()) {
            registerGenerators();
        }
    }

    /** Disables all applicable underground ore decorators. */
    private static void disableGenerators() {
        for (Biome b : ForgeRegistries.BIOMES) {
            final List<ConfiguredFeature<?>> ores = b.getFeatures(ORE_DEC);
            final List<ConfiguredFeature<?>> drain = new ArrayList<>();
            ores.forEach(ore ->
                findOreConfig(ore).ifPresent(config -> {
                    if (shouldDisable(config.state)) {
                        drain.add(ore);
                    }
                })
            );
            ores.removeAll(drain);
        }
    }

    /** Attempts to load a standard OreFeatureConfig from the input feature. */
    private static Optional<OreFeatureConfig> findOreConfig(ConfiguredFeature<?> feature) {
        if (feature.config instanceof DecoratedFeatureConfig) {
            final DecoratedFeatureConfig decorated = (DecoratedFeatureConfig) feature.config;
            if (decorated.feature.config instanceof OreFeatureConfig) {
                return full((OreFeatureConfig) decorated.feature.config);
            }
        }
        return empty();
    }

    /** Determines whether the input block should be drained, per the current biome config. */
    private static boolean shouldDisable(BlockState state) {
        return (!Cfg.enableVanillaOres.get() && state.getBlock() instanceof OreBlock)
            || (!Cfg.enableVanillaStone.get() && isStoneGen(state.getBlock()));
    }

    private static boolean isStoneGen(Block block) {
        return block == Blocks.STONE
            || block == Blocks.ANDESITE
            || block == Blocks.DIORITE
            || block == Blocks.GRANITE
            || block == Blocks.DIRT
            || block == Blocks.GRAVEL;
    }

    /** Generates and registers all ore decorators with the appropriate biomes. */
    private static void registerGenerators() {
        forEnabledProps((block, props, gen) -> {
            CountRangeConfig rangeConfig = new CountRangeConfig(gen.frequency, gen.height.min, 0, gen.height.max);
            VariantFeatureConfig variantConfig = new VariantFeatureConfig(props, gen.count, gen.denseRatio);
            gen.biomes.get().forEach(b -> b.addFeature(ORE_DEC, createFeature(variantConfig, rangeConfig)));
        });
    }

    /** Iterates through all WorldGenProperties and their respective BlockStates. */
    private static void forEnabledProps(TriConsumer<BlockState, OreProperties, WorldGenProperties> fun) {
        for (BaseOreVariant block : Main.BLOCKS) {
            for (WorldGenProperties gen : block.properties.gen) {
                fun.accept(block.getDefaultState(), block.properties, gen);
            }
        }
    }

    /** Shorthand for converting the input configs into a ConfiguredFeature. */
    private static ConfiguredFeature createFeature(VariantFeatureConfig variantConfig, CountRangeConfig rangeConfig) {
        return Biome.createDecoratedFeature(new VariantFeature(variantConfig), variantConfig, COUNT_RNG, rangeConfig);
    }
}