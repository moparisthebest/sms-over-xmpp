package com.moparisthebest.sms2xmpp;

import android.util.LruCache;

import java.net.IDN;

/**
 * The `Jid' class provides an immutable representation of a JID.
 */
public final class Jid {

    private static LruCache<String,Jid> cache = new LruCache<>(1024);

    private final String localpart;
    private final String domainpart;
    private final String resourcepart;

    private static final char[] JID_ESCAPING_CHARS = {' ','"','&','\'','/',':','<','>','@','\\'};

    // It's much more efficient to store the full JID as well as the parts instead of figuring them
    // all out every time (since some characters are displayed but aren't used for comparisons).
    private final String displayjid;

    public String getLocalpart() {
        return localpart;
    }

    public String getUnescapedLocalpart() {
        if (localpart == null || !localpart.contains("\\")) {
            return localpart;
        } else {
            String localpart = this.localpart;
            for(char c : JID_ESCAPING_CHARS) {
                localpart = localpart.replace(String.format ("\\%02x", (int)c),String.valueOf(c));
            }
            return localpart;
        }
    }

    public String getDomainpart() {
        return IDN.toUnicode(domainpart);
    }

    public String getResourcepart() {
        return resourcepart;
    }

    public static Jid fromParts(final String localpart,
                                final String domainpart,
                                final String resourcepart) {
        String out;
        if (localpart == null || localpart.isEmpty()) {
            out = domainpart;
        } else {
            out = localpart + "@" + domainpart;
        }
        if (resourcepart != null && !resourcepart.isEmpty()) {
            out = out + "/" + resourcepart;
        }
        return fromString(out);
    }

    public static Jid fromString(final String jid) {
        Jid ret = Jid.cache.get(jid);
        if(ret != null)
            return ret;
        ret = new Jid(jid);
        Jid.cache.put(jid, ret);
        return ret;
    }

    private Jid(final String jid) {
        if (jid == null) throw new NullPointerException();

        // Hackish Android way to count the number of chars in a string... should work everywhere.
        final int atCount = jid.length() - jid.replace("@", "").length();
        final int slashCount = jid.length() - jid.replace("/", "").length();

        // Throw an error if there's anything obvious wrong with the JID...
        if (jid.isEmpty() || jid.length() > 3071) {
            throw new IllegalArgumentException("invalid length");
        }

        // Go ahead and check if the localpart or resourcepart is empty.
        if (jid.startsWith("@") || (jid.endsWith("@") && slashCount == 0) || jid.startsWith("/") || (jid.endsWith("/") && slashCount < 2)) {
            throw new IllegalArgumentException("invalid character");
        }

        String finaljid;

        final int domainpartStart;
        final int atLoc = jid.indexOf("@");
        final int slashLoc = jid.indexOf("/");
        // If there is no "@" in the JID (eg. "example.net" or "example.net/resource")
        // or there are one or more "@" signs but they're all in the resourcepart (eg. "example.net/@/rp@"):
        if (atCount == 0 || (atCount > 0 && slashLoc != -1 && atLoc > slashLoc)) {
            localpart = "";
            finaljid = "";
            domainpartStart = 0;
        } else {
            localpart = jid.substring(0, atLoc);
            if (localpart.isEmpty() || localpart.length() > 1023) {
                throw new IllegalArgumentException("invalid part length");
            }
            domainpartStart = atLoc + 1;
            finaljid = localpart + "@";
        }

        final String dp;
        if (slashCount > 0) {
            resourcepart = jid.substring(slashLoc + 1, jid.length());
            if (resourcepart.isEmpty() || resourcepart.length() > 1023) {
                throw new IllegalArgumentException("invalid part length");
            }
            dp = IDN.toUnicode(jid.substring(domainpartStart, slashLoc), IDN.USE_STD3_ASCII_RULES);
            finaljid = finaljid + dp + "/" + resourcepart;
        } else {
            resourcepart = "";
            dp = IDN.toUnicode(jid.substring(domainpartStart, jid.length()), IDN.USE_STD3_ASCII_RULES);
            finaljid = finaljid + dp;
        }

        // Remove trailing "." before storing the domain part.
        if (dp.endsWith(".")) {
            domainpart = IDN.toASCII(dp.substring(0, dp.length() - 1), IDN.USE_STD3_ASCII_RULES);
        } else {
            domainpart = IDN.toASCII(dp, IDN.USE_STD3_ASCII_RULES);
        }

        // TODO: Find a proper domain validation library; validate individual parts, separators, etc.
        if (domainpart.isEmpty() || domainpart.length() > 1023) {
            throw new IllegalArgumentException("invalid part length");
        }

        this.displayjid = finaljid;
    }

    public Jid toBareJid() {
        return resourcepart.isEmpty() ? this : fromParts(localpart, domainpart, null);
    }

    public Jid toDomainJid() {
        return resourcepart.isEmpty() && localpart.isEmpty() ? this : fromString(getDomainpart());
    }

    @Override
    public String toString() {
        return displayjid;
    }

    public String toPreppedString() {
        String out;
        if (hasLocalpart()) {
            out = localpart + '@' + domainpart;
        } else {
            out = domainpart;
        }
        if (!resourcepart.isEmpty()) {
            out += '/'+resourcepart;
        }
        return out;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Jid jid = (Jid) o;

        if (localpart != null ? !localpart.equals(jid.localpart) : jid.localpart != null) return false;
        if (domainpart != null ? !domainpart.equals(jid.domainpart) : jid.domainpart != null) return false;
        return resourcepart != null ? resourcepart.equals(jid.resourcepart) : jid.resourcepart == null;
    }

    @Override
    public int hashCode() {
        int result = localpart.hashCode();
        result = 31 * result + domainpart.hashCode();
        result = 31 * result + resourcepart.hashCode();
        return result;
    }

    public boolean hasLocalpart() {
        return !localpart.isEmpty();
    }

    public boolean isBareJid() {
        return this.resourcepart.isEmpty();
    }

    public boolean isDomainJid() {
        return !this.hasLocalpart();
    }
}

