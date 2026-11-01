package dev.spotsimon.mtrsignalcc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.spotsimon.mtrsignalcc.SignalControl;
import dev.spotsimon.mtrsignalcc.SignalKind;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.mtr.block.BlockSignalBase;

import java.util.List;
import java.util.OptionalInt;

/**
 * /mtrsignal info   <pos>
 * /mtrsignal mode   <pos> <auto|manual> [side]
 * /mtrsignal aspect <pos> <aspect> [side]      (manual mode only)
 */
public final class SignalCommand {
    private SignalCommand() {}

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }

    public static void register(RegisterCommandsEvent event) {
        final CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("mtrsignal")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("info")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(SignalCommand::info)))
                .then(Commands.literal("mode")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("auto", "manual"), b))
                                        .executes(c -> mode(c, null))
                                        .then(Commands.argument("side", IntegerArgumentType.integer(1, 2))
                                                .executes(c -> mode(c, IntegerArgumentType.getInteger(c, "side")))))))
                .then(Commands.literal("aspect")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("aspect", IntegerArgumentType.integer(0, 3))
                                        .executes(c -> aspect(c, null))
                                        .then(Commands.argument("side", IntegerArgumentType.integer(1, 2))
                                                .executes(c -> aspect(c, IntegerArgumentType.getInteger(c, "side"))))))));
    }

    private static BlockSignalBase.BlockEntityBase find(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        final BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        if (c.getSource().getLevel().getBlockEntity(pos) instanceof BlockSignalBase.BlockEntityBase be && SignalKind.of(be) != null) {
            return be;
        }
        throw error("No MTR signal at " + pos.toShortString());
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        final BlockSignalBase.BlockEntityBase be = find(c);
        final SignalKind kind = SignalKind.of(be);
        final StringBuilder sb = new StringBuilder(kind.family + ", " + kind.aspectCount() + " aspects, "
                + (be.isDoubleSided ? "double sided" : "single sided"));
        for (int side : SignalControl.resolveSides(be, null, false)) {
            final OptionalInt current = SignalControl.currentAspect(be, side);
            sb.append("\n side ").append(side + 1).append(": ")
                    .append(SignalControl.isManual(be, side) ? "manual" : "auto").append(", ")
                    .append(current.isPresent() ? current.getAsInt() + " (" + kind.aspectName(current.getAsInt()) + ")" : "aspect unknown");
        }
        final String text = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int mode(CommandContext<CommandSourceStack> c, Integer side) throws CommandSyntaxException {
        final BlockSignalBase.BlockEntityBase be = find(c);
        final String mode = StringArgumentType.getString(c, "mode").toLowerCase();
        if (!mode.equals("auto") && !mode.equals("manual")) {
            throw error("Mode must be auto or manual");
        }
        try {
            for (int s : SignalControl.resolveSides(be, side, false)) {
                SignalControl.setMode(be, s, mode.equals("manual"));
            }
        } catch (IllegalArgumentException e) {
            throw error(e.getMessage());
        }
        c.getSource().sendSuccess(() -> Component.literal("Signal set to " + mode), true);
        return 1;
    }

    private static int aspect(CommandContext<CommandSourceStack> c, Integer side) throws CommandSyntaxException {
        final BlockSignalBase.BlockEntityBase be = find(c);
        final int aspect = IntegerArgumentType.getInteger(c, "aspect");
        try {
            SignalControl.setAspect(be, SignalControl.resolveSides(be, side, true)[0], aspect);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw error(e.getMessage());
        }
        c.getSource().sendSuccess(() -> Component.literal("Aspect set to " + aspect), true);
        return 1;
    }
}
