import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Created by incre on 28.04.2017.
 */
public class FileSynch {
    private WatchService WATCH_SERVICE;
    private final String SOURCE_DIR;
    private final String DEST_DIR;
    private boolean isCloseWatch = false;

    public FileSynch(String SOURCE_DIR, String DEST_DIR) {
        this.SOURCE_DIR = SOURCE_DIR;
        this.DEST_DIR = DEST_DIR;
    }

    public void start() {
        try {
            this.WATCH_SERVICE = FileSystems.getDefault().newWatchService();
            startWatching();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startWatching() throws IOException {
        WatchKey key = Paths.get(SOURCE_DIR).register(WATCH_SERVICE,
                ENTRY_CREATE,
                ENTRY_DELETE,
                ENTRY_MODIFY);

        while (!isCloseWatch) {
            try {
                Thread.sleep(1000);
                WATCH_SERVICE.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return;
            }

            syncDirs();

            key.reset();
        }
    }

    public void close() throws IOException {
        isCloseWatch = true;
        System.out.println("close");
    }

    public void syncDirs() {
        createDirIfNotExists(DEST_DIR);
        procFiles(SOURCE_DIR, new CopyFilesFileVisitor(SOURCE_DIR, DEST_DIR));
        procFiles(DEST_DIR, new DeleteFilesFileVisitor(SOURCE_DIR, DEST_DIR));
    }


    private void createDirIfNotExists(String dir) {
        Path path = Paths.get(dir);
        if (!Files.exists(path)) {
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void procFiles(String dir, FileVisitor fileVisitor) {
        Path path = Paths.get(dir);
        try {
            Files.walkFileTree(path, fileVisitor);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Path getChangedRootDirPath(Path path, String sourceRootDir, String targetRootDir) {
        return Paths.get(path.toString().replace(sourceRootDir, targetRootDir));
    }

    class CopyFilesFileVisitor extends SimpleFileVisitor<Path> {
        private final String sourceDir;
        private final String targetDir;

        public CopyFilesFileVisitor(String sourceDir, String targetDir) {
            this.sourceDir = sourceDir;
            this.targetDir = targetDir;
        }

        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
            Path destPath = getChangedRootDirPath(path, sourceDir, targetDir);
            copyFileIfNotExistsOrBeChanged(path, destPath);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
            if (!path.toString().equals(sourceDir)) {
                Path destPath = getChangedRootDirPath(path, sourceDir, targetDir);
                copyFileIfNotExistsOrBeChanged(path, destPath);
            }
            return FileVisitResult.CONTINUE;
        }

        private void copyFileIfNotExistsOrBeChanged(Path sourcePath, Path targetPath) throws IOException {
            if (!Files.exists(targetPath) || (Files.size(sourcePath) != Files.size(targetPath))) {
                try {
                    if (Files.exists(sourcePath)) {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ex) {

                }
                System.out.println("File \"" + targetPath.toString() + "\" was copied.");
            }
        }
    }

    class DeleteFilesFileVisitor extends SimpleFileVisitor<Path> {
        private final String sourceDir;
        private final String targetDir;

        public DeleteFilesFileVisitor(String sourceDir, String targetDir) {
            this.sourceDir = sourceDir;
            this.targetDir = targetDir;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
            if (!path.toString().equals(targetDir)) {
                Path sourcePath = getChangedRootDirPath(path, targetDir, sourceDir);
                deleteIfNotExistsInSource(sourcePath, path);
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
            if (!path.toString().equals(targetDir)) {
                Path sourcePath = getChangedRootDirPath(path, targetDir, sourceDir);
                deleteIfNotExistsInSource(sourcePath, path);
            }

            return FileVisitResult.CONTINUE;
        }

        private void deleteIfNotExistsInSource(Path sourcePath, Path targetPath) {
            if (!Files.exists(sourcePath)) {
                if (Files.isDirectory(targetPath)) {
                    procFiles(targetPath.toString(), new DeleteFilesFileVisitor(sourcePath.toString(), targetPath.toString()));
                }
                try {
                    Files.deleteIfExists(targetPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("File \"" + targetPath.toString() + "\" was deleted.");
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 2) {
            System.out.println("args != 2");
            return;
        }

        final FileSynch fileSynch = new FileSynch(args[0], args[1]);
        new Thread(() -> {
            fileSynch.start();
        }).start();

        System.out.println("Write any to Exit");
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
        fileSynch.close();
    }
}
