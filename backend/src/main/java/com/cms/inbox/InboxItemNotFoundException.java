package com.cms.inbox;

import java.util.UUID;

public class InboxItemNotFoundException extends RuntimeException {

    public InboxItemNotFoundException(UUID itemId) {
        super("No Inbox Item with id " + itemId);
    }
}
