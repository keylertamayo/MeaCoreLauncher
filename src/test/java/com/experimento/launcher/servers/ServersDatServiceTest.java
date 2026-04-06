package com.experimento.launcher.servers;

import com.experimento.launcher.model.ServerEntry;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
class ServersDatServiceTest {

    @Test
    void serversListUsesCompoundElementType() throws Exception {
        Path dir = Files.createTempDirectory("serversdat-test");
        try {
            List<ServerEntry> entries = List.of(new ServerEntry("Test", "host.example:25565"));
            ServersDatService.writeServers(dir, entries);

            CompoundBinaryTag root =
                    BinaryTagIO.readCompressedPath(ServersDatService.serversDatPath(dir));
            ListBinaryTag servers = root.getList("servers");
            assertEquals(BinaryTagTypes.COMPOUND, servers.elementType());
            assertEquals(1, servers.size());
            CompoundBinaryTag row = servers.getCompound(0);
            assertEquals("Test", row.getString("name"));
            assertEquals("host.example:25565", row.getString("ip"));
            assertEquals(false, row.getBoolean("acceptTextures"));
        } finally {
            Files.deleteIfExists(ServersDatService.serversDatPath(dir));
            Files.deleteIfExists(dir);
        }
    }
}
