package com.feed_the_beast.ftbcurseappbot.globalCommands;

import com.feed_the_beast.ftbcurseappbot.Main;
import com.feed_the_beast.ftbcurseappbot.api.nighttwitchstatus.Server;
import com.feed_the_beast.ftbcurseappbot.api.nighttwitchstatus.Status;
import com.feed_the_beast.ftbcurseappbot.utils.JsonFactory;
import com.feed_the_beast.ftbcurseappbot.utils.NetworkingUtils;
import com.feed_the_beast.javacurselib.websocket.WebSocket;
import com.feed_the_beast.javacurselib.websocket.messages.notifications.ConversationMessageNotification;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import javax.annotation.Nonnull;

/**
 * Created by progwml6 on 8/16/16.
 */
@Slf4j
public class TwitchStatus extends StatusCommandBase {
    private boolean CHAT_ENABLED = true;
    private static String NIGHT_STATUS_URL = "https://twitchstatus.com/api/status/";
    public static StatusCommandBase instance;
    private Map<String, Server> componentStatuses;
    private Map<String, String> types;
    private boolean changed = false;

    public TwitchStatus () {
        instance = this;
    }

    @Override
    public String getService () {
        return "twitch";
    }

    @Nonnull @Override
    public String getServiceStatus () {
        updateServiceHealth();
        String status = "unknown";
        StringBuilder buf = new StringBuilder();
        for (Map.Entry<String, Server> me : componentStatuses.entrySet()) {
            Server c = me.getValue();
            if(c.status.equals("online"))
                status = "online";
            if (!c.status.equals("online")) {
                buf.append(getStatusUpdate(c, types.get(c.server.toLowerCase()))).append(", ");
            }
        }
        String bufString = buf.toString();
        String ret = "Twitch Status: " + (StringUtils.isNotEmpty(bufString) ? bufString : status);//TODO is this done??

        if (ret.endsWith(", ")) {
            ret = removeLastTwoChars(ret);
        }
        return ret.replace("offline", ":negative_squared_cross_mark:").replace("slow", ":construction:").replace("online", ":white_check_mark:").replace("unknown", ":question:");
    }

    @Nonnull @Override
    public String updateServiceHealth () {
        String ret = "";
        boolean init = false;
        changed = false;
        try {
            Status status = JsonFactory.GSON.fromJson(NetworkingUtils.getSynchronous(NIGHT_STATUS_URL), Status.class);
            if (componentStatuses == null) {
                componentStatuses = Maps.newHashMap();
                types = Maps.newHashMap();
                init = true;
            }
            for (Server nw : status.web.servers) {
                Server old = componentStatuses.get(nw.server.toLowerCase());
                if (old == null) {
                    ret += init ? "" : getStatusUpdate(nw, "web") + ", ";
                    componentStatuses.put(nw.server.toLowerCase(), nw);
                    types.put(nw.server.toLowerCase(), "web");
                } else {//put it in an update if its changed
                    if (!old.status.equalsIgnoreCase(nw.status)) {
                        ret += init ? "" : getStatusUpdate(nw, "web") + ", ";
                        changed = true;
                        componentStatuses.replace(nw.server.toLowerCase(), nw);
                    }
                }
            }
            for (Server nw : status.ingest.servers) {
                Server old = componentStatuses.get(nw.server.toLowerCase());
                if (old == null) {
                    ret += init ? "" : getStatusUpdate(nw, "ingest") + ", ";
                    componentStatuses.put(nw.server.toLowerCase(), nw);
                    types.put(nw.server.toLowerCase(), "ingest");
                } else {//put it in an update if its changed
                    if (!old.status.equalsIgnoreCase(nw.status)) {
                        ret += init ? "" : getStatusUpdate(nw, "ingest") + ", ";
                        changed = true;
                        componentStatuses.replace(nw.server.toLowerCase(), nw);
                    }
                }
            }
            if (CHAT_ENABLED) {
                for (Server nw : status.chat.servers) {
                    Server old = componentStatuses.get(nw.server.toLowerCase());
                    if (old == null) {
                        ret += init ? "" : getStatusUpdate(nw, "chat") + ", ";
                        componentStatuses.put(nw.server.toLowerCase(), nw);
                        types.put(nw.server.toLowerCase(), "chat");
                    } else {//put it in an update if its changed
                        if (!old.status.equalsIgnoreCase(nw.status)) {
                            ret += init ? "" : getStatusUpdate(nw, "chat") + ", ";
                            changed = true;
                            componentStatuses.replace(nw.server.toLowerCase(), nw);
                        }
                    }
                }
            }

        } catch (IOException e) {
            log.error("error getting " + getService() + " status", e);
            ret = "Error getting " + getService() + " status";
        }
        if (ret.endsWith(", ")) {
            ret = removeLastTwoChars(ret);
        }
        return ret.replace("offline", ":negative_squared_cross_mark:").replace("slow", ":construction:").replace("online", ":white_check_mark:").replace("unknown", ":question:");
    }

    @Override
    public boolean hasChanged () {
        return changed;
    }

    @Override
    public void onMessage (WebSocket webSocket, ConversationMessageNotification msg) {
        log.info(new Date().toString() + " twitchstatus preupdate");
        log.info(getService() + "status " + msg.body.replace(Main.getBotTrigger() + "twitchstatus", ""));
        webSocket.sendMessage(msg.conversationID, getServiceStatus());
        log.info(new Date().toString() + " twitchstatus postsend");
    }

    @Override
    public String getHelp () {
        return "gets the health status of MC services from https://twitchstatus.com/";
    }

    private static String getStatusUpdate (Server c, String type) {
        if (type.equalsIgnoreCase("ingest")) {
            return c.description.replace(":", "-") + ": " + c.status;
        } else if (type.equalsIgnoreCase("web")) {
            return (c.host != null ? c.host : c.server.toLowerCase()) + ": " + c.status;
        } else if (type.equalsIgnoreCase("chat")) {
            return (c.host != null ? c.host : c.server.toLowerCase()) + ": " + c.status;
        }
        return "UNKNOWN_TYPE " + type;
    }

    private static String removeLastTwoChars (String str) {
        return str.substring(0, str.length() - 2);
    }

}
