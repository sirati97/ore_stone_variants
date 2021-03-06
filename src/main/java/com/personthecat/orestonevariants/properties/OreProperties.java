package com.personthecat.orestonevariants.properties;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Encoder;
import com.personthecat.orestonevariants.Main;
import com.personthecat.orestonevariants.config.Cfg;
import com.personthecat.orestonevariants.util.Lazy;
import com.personthecat.orestonevariants.util.Range;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.loot.LootTable;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.loading.FMLLoader;
import org.hjson.JsonArray;
import org.hjson.JsonObject;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.personthecat.orestonevariants.io.SafeFileIO.safeListFiles;
import static com.personthecat.orestonevariants.util.CommonMethods.*;
import static com.personthecat.orestonevariants.util.HjsonTools.*;

/**
 * The primary data holder containing all of the information needed for
 * multiple ores to share properties.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class OreProperties {
    /** An identifier for these properties. */
    public final String name;
    /** Stores the actual lookup to defer ores being loaded. */
    public final String oreLookup;
    /** A reference to the original BlockState represented by these properties. */
    public final Lazy<BlockState> ore;
    /** Standard block properties to be applied when creating new variants. */
    public final Block.Properties block;
    /** Information regarding this ore's texture sprites. */
    public final TextureProperties texture;
    /** Information regarding this ore's world generation variables. */
    public final List<WorldGenProperties> gen;
    /** Information regarding this ore's drop overrides, if any. */
    public final Optional<LootTable> drops;
    /** Information regarding this ore's smelting recipe. Generated later.*/
    public final RecipeProperties recipe;
    /** The amount of experience to drop for this ore. Better location? */
    public final Optional<Range> xp;
    /** The translation key to return for this ore type. */
    public final Optional<String> translationKey;

    /** The name of the directory containing all of the presets. */
    private static final String FOLDER = "/config/" + Main.MODID + "/ores/";
    /** The path leading to the folder. */
    public static final File DIR = new File(FMLLoader.getGamePath() + FOLDER);

    /** Enables serialization via vanilla configs. */
    private static final Encoder<OreProperties> ENCODER = Codec.STRING
        .comap(properties -> properties.name);
    /** Enables deserialization in vanilla configs. */
    private static final Decoder<OreProperties> DECODER = Codec.STRING
        .map(s -> of(s).orElseThrow(() -> runExF("Undefined OreProperties: {}", s)));
    /** Required because of this class' use in world generation. */
    public static final Codec<OreProperties> CODEC = Codec.of(ENCODER, DECODER);

    /** Helps organize the categories inside of the root object. Needs work? */
    private OreProperties(
        ResourceLocation location,
        String oreLookup,
        JsonObject root,
        JsonObject block,
        JsonObject texture,
        JsonArray gen
    ) {
        this(
            location.getPath(),
            oreLookup,
            BlockPropertiesHelper.from(block),
            TextureProperties.from(location, texture),
            WorldGenProperties.list(gen),
            getLootTable(root, "loot"),
            RecipeProperties.from(getObjectOrNew(root, "recipe")),
            getRange(block, "xp"),
            getString(block, "translationKey")
        );
    }

    /** Primary constructor */
    public OreProperties(
        String name,
        String oreLookup,
        Block.Properties block,
        TextureProperties texture,
        List<WorldGenProperties> gen,
        Optional<LootTable> drops,
        RecipeProperties recipe,
        Optional<Range> xp,
        Optional<String> translationKey
    ) {
        this.name = name;
        this.oreLookup = oreLookup;
        this.ore = new Lazy<>(() -> getBlockState(oreLookup).orElseThrow(() -> noBlockNamed(oreLookup)));
        this.block = block;
        this.texture = texture;
        this.gen = gen;
        this.drops = drops;
        this.recipe = recipe;
        this.xp = xp;
        this.translationKey = translationKey;
    }

    /** Generates a new OreProperties object from the input file. */
    private static Optional<OreProperties> fromFile(File f) {
        info("Checking: {}", f.getName());
        final JsonObject root = readJson(f).orElseThrow(() -> runExF("Invalid hjson file: {}.", f.getPath()));
        final String mod = getStringOr(root, "mod", "custom");
        final String name = getString(root, "name")
            .orElseGet(() -> noExtension(f))
            .toLowerCase();
        if (!Cfg.oreEnabled(name) || Cfg.modFamiliar(mod) && !Cfg.modEnabled(mod)) {
            info("Skipping {}. It is supported, but not enabled", name);
            return empty();
        } else {
            info("Loading new ore properties: {}", name);
        }
        final ResourceLocation location = new ResourceLocation(mod, name);
        final JsonObject block = getObjectOrNew(root, "block");
        final JsonObject texture = getObjectOrNew(root, "texture");
        final JsonArray gen = getArrayOrNew(root, "gen");
        final String lookup = getStringOr(block, "location", "air");
        return full(new OreProperties(location, lookup, root, block, texture, gen));
    }

    /** Generates properties for all of the presets inside of the directory. */
    public static Set<OreProperties> setupOreProperties() {
        final Set<OreProperties> properties = new HashSet<>();
        for (File f : safeListFiles(DIR)) {
            if (!f.getName().equals("TUTORIAL.hjson")) {
                fromFile(f).ifPresent(properties::add);
            }
        }
        return properties;
    }

    /** Locates the OreProperties corresponding to `name`. */
    public static Optional<OreProperties> of(String name) {
        return find(Main.ORE_PROPERTIES, props -> props.name.equals(name));
    }

    /** Locates the OreProperties corresponding to each entry in the list. */
    public static Set<OreProperties> of(List<String> names) {
        return names.stream()
            .map(name -> of(name)
                .orElseThrow(() -> runExF("There are no properties named \"{}.\" Fix your property group.", name)))
            .collect(Collectors.toSet());
    }
}