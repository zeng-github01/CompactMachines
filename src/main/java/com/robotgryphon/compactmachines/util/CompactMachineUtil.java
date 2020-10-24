package com.robotgryphon.compactmachines.util;

import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.block.tiles.CompactMachineTile;
import com.robotgryphon.compactmachines.core.Registrations;
import com.robotgryphon.compactmachines.data.CompactMachineData;
import com.robotgryphon.compactmachines.data.MachineData;
import com.robotgryphon.compactmachines.reference.EnumMachineSize;
import net.minecraft.block.Block;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.util.Optional;

public abstract class CompactMachineUtil {

    public static void teleportInto(ServerPlayerEntity serverPlayer, BlockPos machinePos, EnumMachineSize size) {
        World serverWorld = serverPlayer.getServerWorld();

        MinecraftServer serv = serverWorld.getServer();
        if (serv != null) {
            if (serverWorld.getDimensionKey() == Registrations.COMPACT_DIMENSION) {
                IFormattableTextComponent msg = new TranslationTextComponent(CompactMachines.MODID + ".cannot_enter")
                        .mergeStyle(TextFormatting.RED);

                serverPlayer.sendStatusMessage(msg, true);
                return;
            }

            ServerWorld compactWorld = serv.getWorld(Registrations.COMPACT_DIMENSION);
            if (compactWorld == null)
                return;

            CompactMachineTile tile = (CompactMachineTile) serverWorld.getTileEntity(machinePos);
            if (tile == null)
                return;

            PlayerUtil.setLastPosition(serverPlayer);

            serv.deferTask(() -> {
                BlockPos spawnPoint;

                MachineData md = MachineData.getMachineData(compactWorld);

                if (tile.machineId == -1) {
                    int nextID = MachineData.getNextMachineId(compactWorld);

                    BlockPos center = getCenterOfMachineById(nextID);

                    // Bump the center up a bit so the floor is Y = 60
                    center = center.offset(Direction.UP, size.getInternalSize() / 2);

                    CompactStructureGenerator.generateCompactStructure(compactWorld, size, center);

                    tile.setMachineId(nextID);
                    md.registerMachine(nextID,
                            new CompactMachineData(nextID, center, serverPlayer.getUniqueID(), size));

                    BlockPos.Mutable spawn = center.toMutable();
                    spawn.setY(62);

                    spawnPoint = spawn.toImmutable();
                } else {
                    Optional<CompactMachineData> info = md.getMachineData(tile.machineId);

                    // We have no machine info here?
                    if (!info.isPresent()) {
                        IFormattableTextComponent text = new TranslationTextComponent("messages.compactmachines.no_machine_data")
                                .mergeStyle(TextFormatting.RED)
                                .mergeStyle(TextFormatting.BOLD);

                        serverPlayer.sendStatusMessage(text, true);
                        return;
                    }

                    CompactMachineData data = info.get();
                    BlockPos.Mutable center = data.getCenter().toMutable();
                    center.setY(62);

                    spawnPoint = data.getSpawnPoint().orElse(center);
                }

                CompactMachinePlayerUtil.addPlayerToMachine(serverPlayer, tile.machineId);
                serverPlayer.teleport(compactWorld, spawnPoint.getX() + 0.5, spawnPoint.getY(), spawnPoint.getZ() + 0.5, serverPlayer.rotationYaw, serverPlayer.rotationPitch);
            });
        }
    }



    public static EnumMachineSize getMachineSizeFromNBT(@Nullable CompoundNBT tag) {
        try {
            if (tag == null)
                return EnumMachineSize.TINY;

            if (!tag.contains("size"))
                return EnumMachineSize.TINY;

            String sizeFromTag = tag.getString("size");
            return EnumMachineSize.getFromSize(sizeFromTag);
        } catch (Exception ex) {
            return EnumMachineSize.TINY;
        }
    }

    public static Block getMachineBlockBySize(EnumMachineSize size) {
        switch (size) {
            case TINY:
                return Registrations.MACHINE_BLOCK_TINY.get();

            case SMALL:
                return Registrations.MACHINE_BLOCK_SMALL.get();

            case NORMAL:
                return Registrations.MACHINE_BLOCK_NORMAL.get();

            case LARGE:
                return Registrations.MACHINE_BLOCK_LARGE.get();

            case GIANT:
                return Registrations.MACHINE_BLOCK_GIANT.get();

            case MAXIMUM:
                return Registrations.MACHINE_BLOCK_MAXIMUM.get();
        }

        return Registrations.MACHINE_BLOCK_NORMAL.get();
    }

    public static Item getMachineBlockItemBySize(EnumMachineSize size) {
        switch (size) {
            case TINY:
                return Registrations.MACHINE_BLOCK_ITEM_TINY.get();

            case SMALL:
                return Registrations.MACHINE_BLOCK_ITEM_SMALL.get();

            case NORMAL:
                return Registrations.MACHINE_BLOCK_ITEM_NORMAL.get();

            case LARGE:
                return Registrations.MACHINE_BLOCK_ITEM_LARGE.get();

            case GIANT:
                return Registrations.MACHINE_BLOCK_ITEM_GIANT.get();

            case MAXIMUM:
                return Registrations.MACHINE_BLOCK_ITEM_MAXIMUM.get();
        }

        return Registrations.MACHINE_BLOCK_ITEM_NORMAL.get();
    }

    public static BlockPos getCenterOfMachineById(int id) {
        Vector3i location = MathUtil.getRegionPositionByIndex(id);
        return new BlockPos((location.getX() * 1024) + 8, 60, (location.getZ() * 1024) + 8);
    }

    public static void setMachineSpawn(ServerWorld world, BlockPos position) {
        MachineData machineData = MachineData.getMachineData(world);

        Optional<CompactMachineData> compactMachineData = machineData.getMachineContainingPosition(position);
        compactMachineData.ifPresent(d -> {
            d.setSpawnPoint(position);
            machineData.updateMachineData(d);
        });
    }

    public static Optional<MachineData> getMachineData(World world) {
        if (world == null)
            return Optional.empty();

        if (world instanceof ServerWorld) {
            ServerWorld sWorld = (ServerWorld) world;
            MachineData md = MachineData.getMachineData(sWorld);
            return Optional.of(md);
        }

        return Optional.empty();
    }

    public static Optional<CompactMachineData> getMachineInfoByInternalPosition(ServerWorld world, Vector3d pos) {
        MachineData machineData = MachineData.getMachineData(world);
        return machineData.getMachineContainingPosition(pos);
    }

    public static Optional<CompactMachineData> getMachineInfoByInternalPosition(ServerWorld world, BlockPos pos) {
        MachineData machineData = MachineData.getMachineData(world);
        return machineData.getMachineContainingPosition(pos);
    }

    public static void updateMachineWorldPosition(ServerWorld world, int machineID, BlockPos pos) {
        MachineData machineData = MachineData.getMachineData(world);
        Optional<CompactMachineData> machineById = machineData.getMachineData(machineID);
        machineById.ifPresent(data -> {
            data.setWorldPosition(world, pos);
            data.removeFromPlayerInventory();

            // Write changes to disk
            machineData.updateMachineData(data);
        });
    }
}
