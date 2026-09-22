package org.dsc;

import io.javalin.Javalin;
import org.dsc.store.Store;
import org.dsc.web.WebServer;

import java.nio.file.Path;

/** 入口：./gradlew run --args='--host 127.0.0.1 --port 5343' */
public final class Main {
    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 5343;
        String db = System.getProperty("dsc.db",
                Path.of(System.getProperty("user.home"), ".dsc-reviewer", "dsc.sqlite").toString());
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--host" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--db" -> db = args[++i];
            }
        }
        Store store = Store.file(Path.of(db));
        Javalin app = new WebServer(store).start(host, port);
        System.out.println("DSC Reviewer on http://" + host + ":" + port + " (db=" + db + ")");
    }
}
