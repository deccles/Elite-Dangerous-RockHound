package org.dce.ed;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class SingleInstanceCoordinator implements AutoCloseable {

    private static final String LOCK_FILE_NAME = "rockhound-instance.lock";
    private static final String PORT_FILE_NAME = "rockhound-instance.port";
    private static final int SIGNAL_RETRY_COUNT = 40;
    private static final long SIGNAL_RETRY_DELAY_MILLIS = 25L;
    private static final int ACTIVATION_READ_TIMEOUT_MILLIS = 500;

    private final Path portFile;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ServerSocket serverSocket;
    private final Thread listenerThread;
    private final AtomicBoolean closed = new AtomicBoolean();

    private SingleInstanceCoordinator(
            Path portFile,
            FileChannel lockChannel,
            FileLock lock,
            ServerSocket serverSocket,
            Runnable activationHandler) throws IOException {
        this.portFile = portFile;
        this.lockChannel = lockChannel;
        this.lock = lock;
        this.serverSocket = serverSocket;

        writePortFile(portFile, serverSocket.getLocalPort());
        listenerThread = Thread.ofPlatform()
                .daemon()
                .name("rockhound-instance-activation")
                .start(() -> listenForActivation(activationHandler));
    }

    static SingleInstanceCoordinator start(Path directory, Runnable activationHandler) throws IOException {
        return tryStart(directory, activationHandler)
                .orElseThrow(() -> new IOException("Another RockHound instance is already running"));
    }

    static Optional<SingleInstanceCoordinator> tryStart(Path directory, Runnable activationHandler) throws IOException {
        Files.createDirectories(directory);
        Path lockFile = directory.resolve(LOCK_FILE_NAME);
        Path portFile = directory.resolve(PORT_FILE_NAME);
        IOException lastSignalFailure = null;

        for (int attempt = 0; attempt < SIGNAL_RETRY_COUNT; attempt++) {
            FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock acquiredLock = null;
            try {
                acquiredLock = channel.tryLock();
            } catch (OverlappingFileLockException ignored) {
                // A coordinator in this JVM already owns it.
            }

            if (acquiredLock != null) {
                return Optional.of(startPrimary(portFile, channel, acquiredLock, activationHandler));
            }
            channel.close();

            try {
                signalExistingInstance(portFile);
                return Optional.empty();
            } catch (IOException e) {
                lastSignalFailure = e;
            }

            try {
                Thread.sleep(SIGNAL_RETRY_DELAY_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while contacting the running RockHound instance", interrupted);
            }
        }
        throw new IOException("Could not contact the running RockHound instance", lastSignalFailure);
    }

    private static SingleInstanceCoordinator startPrimary(
            Path portFile,
            FileChannel channel,
            FileLock acquiredLock,
            Runnable activationHandler) throws IOException {
        ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return new SingleInstanceCoordinator(portFile, channel, acquiredLock, server, activationHandler);
        } catch (IOException | RuntimeException e) {
            closeAfterStartupFailure(server, acquiredLock, channel, e);
            throw e;
        }
    }

    private static void closeAfterStartupFailure(
            ServerSocket server, FileLock acquiredLock, FileChannel channel, Exception failure) {
        try {
            if (server != null) {
                server.close();
            }
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
        try {
            acquiredLock.release();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
        try {
            channel.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    boolean isPrimaryInstance() {
        return lock.isValid();
    }

    private static void writePortFile(Path portFile, int port) throws IOException {
        Path temporary = portFile.resolveSibling(PORT_FILE_NAME + ".tmp");
        Files.writeString(temporary, Integer.toString(port), StandardCharsets.US_ASCII);
        try {
            Files.move(temporary, portFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveFailure) {
            Files.move(temporary, portFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void signalExistingInstance(Path portFile) throws IOException {
        final int port;
        try {
            port = Integer.parseInt(Files.readString(portFile, StandardCharsets.US_ASCII).trim());
        } catch (NumberFormatException e) {
            throw new IOException("Invalid RockHound activation port", e);
        }
        if (port < 1 || port > 65535) {
            throw new IOException("Invalid RockHound activation port: " + port);
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 250);
            socket.getOutputStream().write(1);
        }
    }

    private void listenForActivation(Runnable activationHandler) {
        while (!serverSocket.isClosed()) {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(ACTIVATION_READ_TIMEOUT_MILLIS);
                if (socket.getInputStream().read() == 1) {
                    activationHandler.run();
                }
            } catch (SocketTimeoutException ignored) {
                // Reject clients that connect without completing the one-byte protocol.
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("[RockHound] Instance activation listener failed: " + e.getMessage());
                }
            } catch (RuntimeException e) {
                System.err.println("[RockHound] Instance activation handler failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        IOException failure = null;
        try {
            serverSocket.close();
        } catch (IOException e) {
            failure = e;
        }
        try {
            listenerThread.join(ACTIVATION_READ_TIMEOUT_MILLIS + 250L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            IOException interrupted = new IOException("Interrupted while stopping the activation listener", e);
            if (failure == null) {
                failure = interrupted;
            } else {
                failure.addSuppressed(interrupted);
            }
        }
        try {
            Files.deleteIfExists(portFile);
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        try {
            lock.release();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        try {
            lockChannel.close();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
