package com.avandocmsg.messenger.desktop.sdk.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stages downloaded installer bytes for OS hand-off (W4: delegate to msi/dmg/deb). */
public final class UpdateInstaller {

    public Path stage(byte[] installerBytes, Path targetDir, String fileName) throws IOException {
        Files.createDirectories(targetDir);
        var target = targetDir.resolve(fileName);
        Files.write(target, installerBytes);
        return target;
    }
}
