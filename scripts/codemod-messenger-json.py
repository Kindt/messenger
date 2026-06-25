#!/usr/bin/env python3
"""Migrate new ObjectMapper() to MessengerJson.mapper() in target production files."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(".")
IMPORT = "import com.avandocmsg.messenger.common.json.MessengerJson;"

TARGETS = [
    # coordinators / publishers
    "modules/core-api/src/main/java/com/avandocmsg/messenger/core/application/MessageSendCoordinator.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/core/application/UserPresencePublisher.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/core/application/MessageMentionCoordinator.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/core/application/MessagePinCoordinator.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/core/application/MessageDeleteCoordinator.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/core/application/IndexerEventPublisher.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/core/application/MessageReactionCoordinator.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/api/export/ExportCancelPublisher.java",
    # resources
    "modules/core-api/src/main/java/com/avandocmsg/messenger/api/admin/AdminResource.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/api/files/FileResource.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/api/plugins/PluginAdminResource.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/api/scim/ScimUsersResource.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/api/scim/ScimGroupsResource.java",
    # persistence json
    "modules/core-api/src/main/java/com/avandocmsg/messenger/core/adapter/persistence/JdbcPluginJdbcRepository.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/core/adapter/persistence/JdbcAuthPolicyJdbcRepository.java",
    "modules/core-api/src/main/java/com/avandocmsg/messenger/core/adapter/persistence/JdbcChatPollAdapter.java",
    # ws-gateway
    "modules/ws-gateway/src/main/java/com/avandocmsg/messenger/ws/MessagingWebSocket.java",
    # common hotplug / plugin hot path
    "modules/common/src/main/java/com/avandocmsg/messenger/common/hotplug/HotPlugHeartbeat.java",
    "modules/common/src/main/java/com/avandocmsg/messenger/common/hotplug/HotPlugRegistry.java",
    "modules/common/src/main/java/com/avandocmsg/messenger/common/plugin/integration/OneCODataClient.java",
    "modules/common/src/main/java/com/avandocmsg/messenger/common/plugin/integration/WebDavStorageClient.java",
    "modules/common/src/main/java/com/avandocmsg/messenger/common/plugin/integration/GraphCalendarClient.java",
    "modules/common/src/main/java/com/avandocmsg/messenger/common/plugin/integration/OpenAiCompatibleLlmClient.java",
    "modules/common/src/main/java/com/avandocmsg/messenger/common/plugin/integration/OcrHttpClient.java",
]


def patch(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    orig = text
    text = text.replace("new ObjectMapper().findAndRegisterModules()", "MessengerJson.mapper()")
    text = re.sub(r"new ObjectMapper\(\)", "MessengerJson.mapper()", text)
    if IMPORT not in text and "MessengerJson.mapper()" in text:
        m = re.search(r"(package [^\n]+\n\n)", text)
        if m:
            text = text[: m.end()] + IMPORT + "\n" + text[m.end() :]
    if text != orig:
        path.write_text(text, encoding="utf-8")
        return True
    return False


def main() -> None:
    for rel in TARGETS:
        p = ROOT / rel
        if p.exists() and patch(p):
            print(f"patched {rel}")


if __name__ == "__main__":
    main()
