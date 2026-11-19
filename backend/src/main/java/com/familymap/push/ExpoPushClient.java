package com.familymap.push;

import java.util.List;

/** Sends messages to Expo's push service. An interface so tests never touch the network. */
public interface ExpoPushClient {

    /**
     * @return one ticket per message, in the same order
     */
    List<Ticket> send(List<PushMessage> messages);

    /**
     * Expo's per-message result. {@code error} is e.g. {@code DeviceNotRegistered} when the
     * app was uninstalled, in which case the token should be deleted.
     */
    record Ticket(String status, String error) {
        public boolean deviceGone() {
            return "error".equals(status) && "DeviceNotRegistered".equals(error);
        }
    }
}
