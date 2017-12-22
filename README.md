Working conversations plugin, to implement [jmp.chat](https://jmp.chat/)-like SMS gateway on a per phone/account basis.

Requires Conversations (or other XMPP client) implementing [xmpp-api](https://github.com/moparisthebest/xmpp-api) which is
currently only [this custom fork](https://github.com/moparisthebest/Conversations/tree/xmpp-api)

This also requires a running [xmpp-echo-self](https://github.com/moparisthebest/xmpp-echo-self) component and is currently
hard-coded to echo.burtrum.org so all your texts would go through me for now, be aware.
