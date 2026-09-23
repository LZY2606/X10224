package dsc;

import dsc.server.Server;
import dsc.store.Store;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 5343;
        String db = "data/dsc.db";
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--host" -> host = args[i + 1];
                case "--port" -> port = Integer.parseInt(args[i + 1]);
                case "--db" -> db = args[i + 1];
            }
        }
        Path dbPath = Path.of(db);
        if (dbPath.getParent() != null) Files.createDirectories(dbPath.getParent());
        Store store = new Store(dbPath);
        new Server(store).app().start(host, port);
        System.out.println("DSC 热转折案审阅器已启动: http://" + host + ":" + port);
    }
}
