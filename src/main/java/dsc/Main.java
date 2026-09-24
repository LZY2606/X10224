package dsc;

import dsc.web.DscService;
import dsc.web.WebApi;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public final class Main {
    public static void main(String[] args) throws Exception {
        int port = 5343;
        String host = "127.0.0.1";
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--port")) {
                port = Integer.parseInt(args[i + 1]);
            }
            if (args[i].equals("--host")) {
                host = args[i + 1];
            }
        }
        DscService svc = DscService.get();
        if (!svc.seeded()) {
            svc.seedFromCasePack(null);
        }

        Javalin app = Javalin.create(cfg -> {
            cfg.staticFiles.add("/web", Location.CLASSPATH);
            cfg.http.defaultContentType = "application/json";
        });
        WebApi.routes(app, svc);
        app.exception(dsc.store.ConflictException.class, (e, ctx) ->
                ctx.status(409).json(java.util.Map.of(
                        "error", "CONFLICT", "message", e.getMessage(),
                        "serverRevision", e.serverRevision)));
        app.exception(IllegalArgumentException.class, (e, ctx) ->
                ctx.status(400).json(java.util.Map.of("error", "BAD_REQUEST", "message", e.getMessage())));
        app.start(host, port);
        System.out.println("热转折案 DSC 审阅器已启动: http://" + host + ":" + port);
    }
}
