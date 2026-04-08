package com.experimento.launcher.servers;

import com.experimento.launcher.model.ServerEntry;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ServersDatService {

    private ServersDatService() {}

    public static Path serversDatPath(Path gameDir) {
        return gameDir.resolve("servers.dat");
    }

    public static void writeServers(Path gameDir, List<ServerEntry> entries) throws Exception {
        Files.createDirectories(gameDir);
        Path dest = serversDatPath(gameDir);
        List<CompoundBinaryTag> compounds = new ArrayList<>();
        for (ServerEntry e : entries) {
            if (e == null || e.address == null || e.address.isBlank()) {
                continue;
            }
            String name = e.name == null || e.name.isBlank() ? e.address : e.name;
            compounds.add(
                    CompoundBinaryTag.builder()
                            .putString("name", name)
                            .putString("ip", e.address.trim())
                            .putBoolean("acceptTextures", false)
                            .build());
        }
        // Minecraft expects TAG_List with element type TAG_Compound (10), not an inferred type.
        List<BinaryTag> elements = new ArrayList<>(compounds.size());
        elements.addAll(compounds);
        ListBinaryTag list = ListBinaryTag.listBinaryTag(BinaryTagTypes.COMPOUND, elements);
        CompoundBinaryTag root = CompoundBinaryTag.builder().put("servers", list).build();
        BinaryTagIO.writer().write(root, dest, BinaryTagIO.Compression.NONE);
    }
}
