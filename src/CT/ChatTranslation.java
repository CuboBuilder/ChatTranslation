package CT;

import Argon.Argon;
import Argon.RegisterArgonEvents;
import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public class ChatTranslation extends Plugin {
    private static long lastPing = 0;

    @Override
    public void init() {
        Events.on(EventType.ServerLoadEvent.class, event -> {
            Log.debug("Chat Translation ServerLoadEvent");
            Vars.netServer.admins.chatFilters.add((p, m) -> {
                if (lastPing > System.currentTimeMillis()) {
                    //Events.fire(new EventType.PlayerChatEvent(p, m));
                    Log.debug("requesting translation for: " + m);
                    requestTranslation(p, m);
                    return null;
                } else {
                    Log.debug("translation offline");
                }
                return m;
            });
        });

        Events.on(RegisterArgonEvents.class, ignored -> {
            //register argon events
            Timer.schedule(() -> Argon.fire(new CTPingRequest()), 15, 60);
            Argon.on(CTPingResponse.class, ignored1 -> lastPing = System.currentTimeMillis() + 75_000L);
            Argon.on(TranslateMessageResponse.class, event -> {
                if (event.id > 100) return;
                Core.app.post(() -> {
                    Player sender = Groups.player.getByID(event.playerID);
                    if (sender == null) return;
                    Log.debug("distributing message (@ -> @)", event.originalLocale, event.locale);
                    //send message
                    String message;
                    if (event.originalLocale().charAt(0) != event.locale().charAt(0) || event.originalLocale().charAt(1) != event.locale().charAt(1) || !event.original().equals(event.translated())) {
                        message = Vars.netServer.chatFormatter.format(sender, event.translated) + "\n[lightgray][" + event.originalLocale() + "] " + event.original();
                    } else {
                        message = Vars.netServer.chatFormatter.format(sender, event.original);
                    }

                    for (Player p : Groups.player)
                        if (p.locale.charAt(0) == event.locale.charAt(0) && p.locale.charAt(1) == event.locale.charAt(1))
                            Call.sendMessage(p.con, message, event.translated, sender);
                });
            });
        });

        Log.info("Chat Translation has finished loading!");
    }

    private static void requestTranslation(Player p, String message) {
        Seq<String> locales = new Seq<>();
        Groups.player.each(p3 -> locales.addUnique(p3.locale.substring(0,2)));
        locales.addUnique("es");
        for (int i = 0; i < locales.size; i++)
            Argon.fire(new TranslateMessage(message, locales.get(i), i, p.id));
    }

    public record TranslateMessage(String message, String targetLanguage, long id, int playerID) {
    }

    public record TranslateMessageResponse(String original, String translated, String originalLocale, String locale,
                                           long id, int playerID) {
    }

    public record CTPingRequest() {
    }

    public record CTPingResponse() {
    }
}
