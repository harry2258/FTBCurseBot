package com.feed_the_beast.ftbcurseappbot.globalCommands;

import com.feed_the_beast.ftbcurseappbot.Main;
import com.feed_the_beast.javacurselib.examples.app_v1.CurseApp;
import com.feed_the_beast.javacurselib.service.contacts.contacts.ContactsResponse;
import com.feed_the_beast.javacurselib.websocket.WebSocket;
import com.feed_the_beast.javacurselib.websocket.messages.notifications.ConversationMessageNotification;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

/**
 * Created by progwml6 on 5/20/16.
 */
@Slf4j
public class MCStatus extends CommandBase {

    @Override
    public void onMessage (WebSocket webSocket, ConversationMessageNotification msg) {
        log.info("mcstatus " + msg.body.replace(Main.getBotTrigger() + "mcstatus", ""));
        webSocket.sendMessage(msg.conversationID, getMCStatus());

    }

    @Override
    public Pattern getTriggerRegex () {
        return getSimpleCommand("mcstatus");
    }

    @Override
    public String getHelp () {
        return "gets the health status of MC services from mojang";
    }

    //TODO test this and hook up to something that calls getMCStatus() every x amount of time
    public static void sendMCHealthChangeNotifications (@Nonnull ContactsResponse cr, @Nonnull WebSocket ws, @Nonnull String message) {
        if (message.isEmpty()) {
            log.info("no change in mc health status");
            return;
        }
        if (Main.getMcStatusChangeNotificationsEnabled().isPresent()) {
            for (String s : Main.getMcStatusChangeNotificationsEnabled().get()) {
                if (s.contains(".")) {
                    String[] g = s.split("\\.");
                    ws.sendMessage(CurseApp.getChangelIDFromChannelName(cr, g[0], g[1]), message);
                } else {
                    ws.sendMessage(CurseApp.getChangelIDFromChannelName(cr, null, s), message);
                }
            }
        }
    }

    public static String getMCStatus () {
        updateMCHealth();
        if (mcstatushealth == null || mcstatushealth.isEmpty()) {
            return "ERROR getting mc status";
        }
        String ret = "";
        for (Map.Entry<String, String> me : mcstatushealth.entrySet()) {
            ret += getPrettyStatus(me.getKey(), true);
        }
        if (ret.endsWith("|")) {
            ret = removeLastChar(ret);
        }
        return ret;
    }

    private static Map<String, String> mcstatusmappings = null;
    private static Map<String, String> mcstatushealth = null;

    public static @Nonnull String updateMCHealth () {
        try {
            String json = Jsoup.connect("http://status.mojang.com/check").ignoreContentType(true).get().text();
            JsonParser p = new JsonParser();
            JsonArray report = p.parse(json).getAsJsonArray();
            if (mcstatusmappings == null) {
                initMappings();
            }
            if (mcstatushealth == null) {
                mcstatushealth = Maps.newHashMap();
            }
            String changelist = "";
            for (int i = 0; i < report.size(); i++) {
                for (Map.Entry<String, JsonElement> a : report.get(i).getAsJsonObject().entrySet()) {
                    //log.info(a.getKey() + " " + mcstatusmappings.get(a.getKey()) + " " + a.getValue().getAsString());
                    String cstatus = mcstatushealth.get(mcstatusmappings.get(a.getKey()));
                    mcstatushealth.put(mcstatusmappings.get(a.getKey()), a.getValue().getAsString());
                    if (cstatus != null && !cstatus.isEmpty() && !cstatus.equals(a.getValue().getAsString())) {
                        changelist += getPrettyStatus(a.getKey(), true);
                    }
                }
            }
            if (!changelist.isEmpty()) {
                if (changelist.endsWith("|")) {
                    changelist = removeLastChar(changelist);
                }
                return "MC Status Has Changed to: " + changelist;
            }
        } catch (IOException e) {
            log.error("error getting mc status", e);
        }
        return "";
    }

    public static String getPrettyStatus (String item, boolean needPipe) {
        String ret = getMCStatusFor(item);
        if (!ret.isEmpty()) {
            String pretty = mcstatusmappings.containsKey(item) ? mcstatusmappings.get(item) : item;
            return pretty + ": " + ret + (needPipe ? "|" : "");
        }
        return "";
    }

    public static @Nonnull String getMCStatusFor (String s) {
        if (mcstatusmappings == null) {
            initMappings();
        }

        String ret = (mcstatushealth.containsKey(s) ? mcstatushealth.get(s) : mcstatushealth.get(mcstatusmappings.get(s)));
        if (ret == null) {
            ret = "";
        }
        return ret.replace("up", "green").replace("down", "red").replace("problem", "yellow").replace("red", ":negative_squared_cross_mark:").replace("yellow", ":construction:")
                .replace("green", ":white_check_mark:");

    }

    public static void initMappings () {
        mcstatusmappings = Maps.newHashMap();
        mcstatusmappings.put("mojang.com", "Mojang.com");
        mcstatusmappings.put("textures.minecraft.net", "Textures");
        mcstatusmappings.put("api.mojang.com", "Public API");
        mcstatusmappings.put("authserver.mojang.com", "Auth Server");
        mcstatusmappings.put("skins.minecraft.net", "Skins");
        mcstatusmappings.put("auth.mojang.com", "Mojang Auth");
        mcstatusmappings.put("account.mojang.com", "Accounts website");
        mcstatusmappings.put("minecraft.net", "Minecraft.net");
        mcstatusmappings.put("session.minecraft.net", "Legacy Session");
        mcstatusmappings.put("sessionserver.mojang.com", "Session");
        mcstatusmappings.put("login", "Auth Server");//xpaw
    }

    private static String removeLastChar (String str) {
        return str.substring(0, str.length() - 1);
    }

}
