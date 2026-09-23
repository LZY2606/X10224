package dsc;

import dsc.derive.Engine;
import dsc.store.Database;
import dsc.store.VersionStore;
import dsc.web.Server;

public final class Main {
    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 7070;
        String dbPath = System.getenv().getOrDefault("DSC_DB", "dsc.db");
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--host")) host = args[i + 1];
            if (args[i].equals("--port")) port = Integer.parseInt(args[i + 1]);
            if (args[i].equals("--db")) dbPath = args[i + 1];
        }
        Database db = new Database(dbPath);
        VersionStore store = new VersionStore(db);
        Engine engine = new Engine(store);
        Server server = new Server(store, engine);
        final int p = port;
        server.create().start(host, port);
        System.out.println("DSC baseline reviewer listening on http://" + host + ":" + p);
    }
}
