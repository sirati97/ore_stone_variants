package com.personthecat.orestonevariants.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.personthecat.orestonevariants.properties.OreProperties;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.BlockStateArgument;
import net.minecraft.command.arguments.BlockStateInput;
import net.minecraft.command.arguments.SuggestionProviders;
import net.minecraft.util.text.*;
import net.minecraft.world.server.ServerWorld;
import org.hjson.JsonObject;
import org.hjson.JsonValue;
import personthecat.fresult.Result;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.personthecat.orestonevariants.util.CommonMethods.*;
import static com.personthecat.orestonevariants.io.SafeFileIO.*;
import static com.personthecat.orestonevariants.util.HjsonTools.*;

public class CommandOSV {

    /** The text formatting to be used for the command usage header. */
    private static final Style USAGE_HEADER_STYLE = Style.EMPTY
        .setColor(Color.func_240744_a_(TextFormatting.GREEN))
        .setBold(true);
    /** The text formatting to be used for displaying command usage. */
    private static final Style USAGE_STYLE = Style.EMPTY
        .setColor(Color.func_240744_a_(TextFormatting.GRAY));
    /** The actual text to be used by the help message. */
    private static final String[][] USAGE_TEXT = {
        { "generate <ore_name> [name]", "Generates an ore preset from the specified",
                "registry name. World gen is not included." },
        { "editConfig <mod_name|all>", "Attempts to disable all ore generation for",
                "the specified mod via its config file." },
        { "setStoneLayer <preset> <min> <max> <density>", "Attempts to generate world gen variables",
                "based on a range of y values and a 0-1 density." },
        { "update <dir> <cfg> <key_path> <value>", "Manually update a preset value." }
    };
    /** the number of lines to occupy each page of the help message. */
    private static final int USAGE_LENGTH = 5;
    /** The header to be used by the help message /  usage text. */
    private static final String USAGE_HEADER = " --- OSV Command Usage ({} / {}) ---";
    /** The help message / usage text. */
    private static final TextComponent[] USAGE_MSG = createHelpMessage();
    /** A demo suggestion provider suggesting player names. */
    private static final SuggestionProvider<CommandSource> DEMO_SUGGESTIONS = createDemoSuggestion();
    /** A suggestion provider suggesting an optional name parameter. */
    private static final SuggestionProvider<CommandSource> OPTIONAL_NAME = createNameSuggestion();
    /** A suggestion provider suggesting all of the supported mod names or "all." */
    private static final SuggestionProvider<CommandSource> MOD_NAMES = createModNames();
    /** A suggestion provider suggesting the possible preset directories. */
    private static final SuggestionProvider<CommandSource> DIRECTORIES = createDirectories();
    /** A suggestion provider suggesting all files in the preset directory. */
    private static final SuggestionProvider<CommandSource> PRESET_NAMES = createPresetNames();
    /** A suggestion provider suggesting all files in the preset directory. */
    private static final SuggestionProvider<CommandSource> ALL_PRESET_NAMES = createAllPresetNames();
    /** A suggestion provider suggesting json path examples. */
    private static final SuggestionProvider<CommandSource> PATH_EXAMPLES = createPathSuggestions();
    /** A suggestion provider suggesting that any value is acceptable. */
    private static final SuggestionProvider<CommandSource> ANY_VALUE = createAnySuggestion();
    /** A suggestion provider suggesting example integers. */
    private static final SuggestionProvider<CommandSource> INTEGER_SUGGESTION = createIntegerSuggestion();
    /** A suggestion provider suggestion example decimals. */
    private static final SuggestionProvider<CommandSource> DECIMAL_SUGGESTION = createDecimalSuggestion();

    /** Creates and registers the parent OSV command. */
    public static void register(Commands manager) {
        manager.getDispatcher().register(createCommandOSV());
        info("Successfully registered /osv with Commands.");
    }

    private static SuggestionProvider<CommandSource> register(String name, SuggestionProvider<ISuggestionProvider> provider) {
        return SuggestionProviders.register(osvLocation(name), provider);
    }

    private static SuggestionProvider<CommandSource> register(String name, String... suggestions) {
        return register(name, (ctx, builder) -> ISuggestionProvider.suggest(suggestions, builder));
    }

    /** Generates the top level command used by this mod. */
    private static LiteralArgumentBuilder<CommandSource> createCommandOSV() {
        return LiteralArgumentBuilder.<CommandSource>literal("osv")
            .executes(wrap(CommandOSV::help))
            .then(createHelp())
            .then(createSayHello())
            .then(createGenerate())
            .then(createEditConfig())
            .then(createSetStoneLayer())
            .then(createUpdate());
    }

    /** Generates the help sub-command. */
    private static LiteralArgumentBuilder<CommandSource> createHelp() {
        return literal("help")
            .executes(wrap(CommandOSV::help))
            .then(arg("page", 1, USAGE_MSG.length)
                .executes(wrap(CommandOSV::helpPage)));
    }

    /** Generates the sayHello sub-command. */
    private static LiteralArgumentBuilder<CommandSource> createSayHello() {
        return literal("sayHello")
            .executes(wrap(CommandOSV::helloWorld))
            .then(arg("name")
                .executes(wrap(CommandOSV::helloName))
                .suggests(DEMO_SUGGESTIONS));
    }

    /** Generates the generate sub-command. */
    private static LiteralArgumentBuilder<CommandSource> createGenerate() {
        return literal("generate")
            .then(blkArg("ore")
                .executes(wrap(CommandOSV::generate))
            .then(arg("name")
                .executes(wrap(CommandOSV::generateNamed))
                .suggests(OPTIONAL_NAME)));
    }

    /** Generates the editConfig sub-command. */
    private static LiteralArgumentBuilder<CommandSource> createEditConfig() {
        return literal("editConfig")
            .then(arg("mod")
                .executes(wrap(CommandOSV::editConfig))
                .suggests(MOD_NAMES));
    }

    /** Generates the setStoneLayer sub-command. */
    private static LiteralArgumentBuilder<CommandSource> createSetStoneLayer() {
        return literal("setStoneLayer")
            .then(arg("preset")
                .suggests(PRESET_NAMES)
            .then(arg("min", 0, 255)
            .then(arg("max", 0, 255)
            .then(arg("density", 0.0, 1.0)
                .executes(wrap(CommandOSV::setStoneLayer))))));
    }

    /** Generates the update sub-command. */
    private static LiteralArgumentBuilder<CommandSource> createUpdate() {
        return literal("update")
            .then(arg("dir")
                .suggests(DIRECTORIES)
            .then(arg("preset")
                .suggests(ALL_PRESET_NAMES)
            .then(pathArg("path")
                .suggests(PATH_EXAMPLES)
            .then(arg("value")
                .executes(wrap(CommandOSV::update))
                .suggests(ANY_VALUE)))));
    }

    /** Wraps a standard consumer so that all errors will be forwarded to the user. */
    private static Command<CommandSource> wrap(Consumer<CommandContext<CommandSource>> fn) {
        return ctx -> (int) Result.of(() -> fn.accept(ctx))
            .ifErr(e -> sendError(ctx, e.getMessage()))
            .map(v -> 1)
            .orElse(-1);
    }

    /** Generates the demo suggestion provider. */
    private static SuggestionProvider<CommandSource> createDemoSuggestion() {
        return register("demo_suggestion", (ctx, builder) -> {
            final List<String> suggestions = list("World", "[<other>]");
            suggestions.addAll(ctx.getSource().getPlayerNames());
            return ISuggestionProvider.suggest(suggestions, builder);
        });
    }

    /** Generates the optional name suggestion provider. */
    private static SuggestionProvider<CommandSource> createNameSuggestion() {
        return register("optional_name_suggestion", "[<named_manually>]");
    }

    /** Generates the possible mod name suggestion provider. */
    private static SuggestionProvider<CommandSource> createModNames() {
        return register("mod_names_suggestion", "all", "[<mod_name>]");
    }

    /** Generates the preset directory suggestion provider. */
    private static SuggestionProvider<CommandSource> createDirectories() {
        return register("directories_suggestion", (ctx, builder) -> {
            final Stream<String> names = list(safeListFiles(getOSVDir())).stream()
                .filter(File::isDirectory)
                .map(File::getName);
            return ISuggestionProvider.suggest(names, builder);
        });
    }

    /** Generates the preset name provider. */
    private static SuggestionProvider<CommandSource> createPresetNames() {
        return register("presets_suggestion", (ctx, builder) -> {
            final Stream<String> names = getUnqualifiedNames(OreProperties.DIR);
            return ISuggestionProvider.suggest(names, builder);
        });
    }

    /** Variant of #createPresetName which dynamically checks the requested directory. */
    private static SuggestionProvider<CommandSource> createAllPresetNames() {
        return register("all_presets_suggestion", (ctx, builder) -> {
            final String dir = ctx.getArgument("dir", String.class);
            final Stream<String> names = getUnqualifiedNames(new File(getOSVDir(), dir));
            return ISuggestionProvider.suggest(names, builder);
        });
    }

    /** Retrieves all of the file names in a directory, minus ".(h)json" */
    private static Stream<String> getUnqualifiedNames(File dir) {
        return list(safeListFiles(dir)).stream()
            .map(File::getName)
            .filter(n -> n.endsWith("json"))
            .map(n -> n.replaceAll("\\.h?json", ""));
    }

    /** Generates the path suggestion provider. */
    private static SuggestionProvider<CommandSource> createPathSuggestions() {
        return register("path_suggestions", "path", "path.value", "path.value[index].value");
    }

    /** Generates the "any value" suggestion provider. */
    private static SuggestionProvider<CommandSource> createAnySuggestion() {
        return register("any_suggestion", "[<any_value>]");
    }

    /** Generates the integer suggestion provider. */
    private static SuggestionProvider<CommandSource> createIntegerSuggestion() {
        return register("integer_suggestion", "[<integer>]", "-1", "0", "1");
    }

    /** Generates the decimal suggestion provider. */
    private static SuggestionProvider<CommandSource> createDecimalSuggestion() {
        return register("decimal_suggestion", "[<decimal>]", "0.5", "1.0", "1.5");
    }

    /** Displays the help page with no arguments passed. */
    private static void help(CommandContext<CommandSource> ctx) {
        doHelp(ctx.getSource(), 1);
    }

    /** Displays the help page with a page number passed. */
    private static void helpPage(CommandContext<CommandSource> ctx) {
        doHelp(ctx.getSource(), ctx.getArgument("page", Integer.class));
    }

    /** Runs the actual help argument. */
    private static void doHelp(CommandSource source, int page) {
        source.sendFeedback(USAGE_MSG[page - 1], true);
    }

    /** Executes the hello world function of /osv. */
    private static void helloWorld(CommandContext<CommandSource> ctx) {
        sendMessage(ctx,"Hello, World!");
    }

    /** Executes the hello name function of /osv. */
    private static void helloName(CommandContext<CommandSource> ctx) {
        sendMessage(ctx,f("Hello, {}!", ctx.getArgument("name", String.class)));
    }

    /** Executes the generate command with no name argument. */
    private static void generate(CommandContext<CommandSource> ctx) {
        doGenerate(ctx, empty());
    }

    /** Executes the fully qualified generate command. */
    private static void generateNamed(CommandContext<CommandSource> ctx) {
        doGenerate(ctx, full(ctx.getArgument("name", String.class)));
    }

    /** Processes the generate command. */
    private static void doGenerate(CommandContext<CommandSource> ctx, Optional<String> name) {
        final BlockState ore = ctx.getArgument("ore", BlockStateInput.class).getState();
        final ServerWorld world = ctx.getSource().getWorld();
        final JsonObject json = new JsonObject();//PropertyGenerator.getBlockInfo(ore, world, name);
        final String fileName = getStringOr(json, "name", "generate_test");
            //.orElseThrow(() -> runEx("Unreachable."));
        final File file = new File(OreProperties.DIR, fileName + ".hjson");
        writeJson(json, file).expect("Error writing new hjson file.");
        sendMessage(ctx, "PropertyGenerator is unfinished. Your files are bogus.");
    }

    /** Executes the edit config command. */
    private static void editConfig(CommandContext<CommandSource> ctx) {
        sendMessage(ctx, "No mods are yet supported, as of this version.");
    }

    /** Executes the set stone layer command. */
    private static void setStoneLayer(CommandContext<CommandSource> ctx) {
        final String preset = ctx.getArgument("preset", String.class);
        final int min = ctx.getArgument("min", Integer.class);
        final int max = ctx.getArgument("max", Integer.class);
        if (min > max) {
            throw runEx("max > min");
        }
        final double density = ctx.getArgument("density", Double.class);
        int size = (int) (((max - min) + 25) * density);
        size = getMin(52, size); // >52 -> cascading gen lag.
        // Lower density -> greater size -> lower count (invert)
        // 15 count per 5 blocks high
        // Minimum of 15
        final int count = (int) ((1.0 - density) * (max - min) * 15 / 5) + 15;

        // Todo: This should be moved to a `doUpdate`.
        execute(ctx, f("osv update stone {} gen[0].height \"[{},{}]\"", preset, min, max));
        execute(ctx, f("osv update stone {} gen[0].size {}", size));
        execute(ctx, f("osv update stone {} gen[0].count {}", count));
    }

    /** Executes the update command. */
    private static void update(CommandContext<CommandSource> ctx) {
        final String dir = ctx.getArgument("dir", String.class);
        final String preset = ctx.getArgument("preset", String.class);
        final PathArgumentResult path = ctx.getArgument("path", PathArgumentResult.class);
        final String value = ctx.getArgument("value", String.class);
        final File file = new File(f("{}/{}/{}.hjson", getOSVDir(), dir, preset));
        final JsonValue toSet = JsonValue.readHjson(value);
        final JsonObject json = readJson(file)
            .orElseThrow(() -> runExF("Preset not found: {}", file.getName()));
        setValueFromPath(json, path, toSet);
        writeJson(json, file)
            .expectF("Error writing to file: {}", file.getName());
        sendMessage(ctx, "Successfully updated " + file.getName());
    }

    /** Generates the help message, displaying usage for each sub-command. */
    private static TextComponent[] createHelpMessage() {
        final List<StringTextComponent> msgs = new ArrayList<>();
        final int numLines = getNumElements(USAGE_TEXT) - USAGE_TEXT.length;
        final int numPages = (int) Math.ceil((double) numLines / (double) USAGE_LENGTH) - 1;
        // The actual pages.
        for (int i = 0; i < USAGE_TEXT.length; i += USAGE_LENGTH) {
            final StringTextComponent header = getUsageHeader((i / USAGE_LENGTH) + 1, numPages);
            // The elements on each page.
            for (int j = i; j < i + USAGE_LENGTH; j++) {
                if (j >= USAGE_TEXT.length) { // ?
                    continue;
                }
                final String[] full = USAGE_TEXT[j];
                // Append the required elements;
                header.appendString("\n");
                appendUsageText(header, full[0], full[1]);
                // Append any extra lines below;
                for (int k = 2; k < full.length; k++) {
                    header.appendString(" ");
                    header.appendString((full[k]));
                }
            }
            msgs.add(header);
        }
        return toArray(msgs, StringTextComponent.class);
    }

    private static int getNumElements(String[][] matrix) {
        int numElements = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                numElements++;
            }
        }
        return numElements;
    }

    private static StringTextComponent getUsageHeader(int page, int max) {
        final String header = f(USAGE_HEADER, String.valueOf(page), String.valueOf(max));
        StringTextComponent full = stc("");
        StringTextComponent headerSTC = stc(header);
        headerSTC.setStyle(USAGE_HEADER_STYLE);
        full.append(headerSTC);
        return full;
    }

    /** A slightly neater way to append so many components to the help message. */
    private static void appendUsageText(TextComponent msg, String command, String usage) {
        msg.append(usageText(command, usage));
    }

    /** Formats the input text to nicely display a command'spawnStructure usage. */
    private static TextComponent usageText(String command, String usage) {
        TextComponent msg = stc(""); // Parent has no formatting.
        msg.append(stc(command).setStyle(USAGE_STYLE));
        msg.append(stc(" :\n " + usage));
        return msg;
    }

    /** Executes additional commands internally. */
    private static int execute(CommandContext<CommandSource> ctx, String command) {
        final Commands manager = ctx.getSource().getServer().getCommandManager();
        return manager.handleCommand(ctx.getSource(), command);
    }
    
    /** Shorthand for sending a message to the input user. */
    private static void sendMessage(CommandContext<CommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(stc(msg), true);
    }

    /** Shorthand for sending an error to the input user. */
    private static void sendError(CommandContext<CommandSource> ctx, String msg) {
        ctx.getSource().sendErrorMessage(stc(msg));
    }

    /** Shorthand method for creating StringTextComponents. */
    private static StringTextComponent stc(String s) {
        return new StringTextComponent(s);
    }

    /** Shorthand for creating a literal argument. */
    private static LiteralArgumentBuilder<CommandSource> literal(String name) {
        return Commands.literal(name);
    }

    /** Shorthand method for creating an integer argument. */
    private static RequiredArgumentBuilder<CommandSource, Integer> arg(String name, int min, int max) {
        return Commands.argument(name, IntegerArgumentType.integer(min, max))
            .suggests(INTEGER_SUGGESTION);
    }

    /** Shorthand method for creating a decimal argument. */
    private static RequiredArgumentBuilder<CommandSource, Double> arg(String name, double min, double max) {
        return Commands.argument(name, DoubleArgumentType.doubleArg(min, max))
            .suggests(DECIMAL_SUGGESTION);
    }

    /** Shorthand method for creating a string argument. */
    private static RequiredArgumentBuilder<CommandSource, String> arg(String name) {
        return Commands.argument(name, StringArgumentType.string());
    }

    /** Shorthand method for creating a block argument. */
    private static RequiredArgumentBuilder<CommandSource, BlockStateInput> blkArg(String name) {
        return Commands.argument(name, BlockStateArgument.blockState());
    }

    private static RequiredArgumentBuilder<CommandSource, PathArgumentResult> pathArg(String name) {
        return Commands.argument(name, new PathArgument());
    }
}