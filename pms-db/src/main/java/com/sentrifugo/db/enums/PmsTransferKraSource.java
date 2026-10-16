package com.sentrifugo.db.enums;

/** Where a transfer sheet KRA came from. */
public enum PmsTransferKraSource {
    /** From a role template (previous or new role). */
    TEMPLATE,
    /** Added by the manager as an exception. */
    MANAGER
}
