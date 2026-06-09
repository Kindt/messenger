from pathlib import Path
import re

ROOT = Path(r"D:\proj\korus_messenger\modules\workers\retention\src\test\java\com\avandocmsg\messenger\worker\retention")

HELPER = '''
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
'''

def wm_expr():
    return 'WorkerMessageSources.forWorker(RetentionWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_retention")'


def add_runonce_worker_messages(content: str) -> str:
    # Append workerMessages to runOnce calls that end with useAdvisoryLock boolean
    return re.sub(
        r'(RetentionHotBodyJanitor\.runOnce\([\s\S]*?)(\n\s+(?:true|false)\n\s+\);)',
        lambda m: m.group(1) + f",\n            {wm_expr()}" + m.group(2).replace(");", "").rstrip() + "\n        );",
        content,
    )


def patch_file(name: str, extra_replacements: list[tuple[str, str]] | None = None) -> None:
    p = ROOT / name
    text = p.read_text(encoding="utf-8")
    if "WorkerMessageSources" not in text:
        text = text.replace("import org.junit.jupiter.api.Test;", HELPER + "import org.junit.jupiter.api.Test;")
    for old, new in extra_replacements or []:
        text = text.replace(old, new)
    text = add_runonce_worker_messages(text)
    p.write_text(text, encoding="utf-8")
    print("patched", name)


patch_file("RetentionShutdownTest.java", [
    ("RetentionShutdown.runCloseables(\n            List.of(() -> order.add(1), () -> order.add(2), () -> order.add(3))\n        );",
     f"RetentionShutdown.runCloseables(\n            List.of(() -> order.add(1), () -> order.add(2), () -> order.add(3)),\n            {wm_expr()}\n        );"),
    ("RetentionShutdown.runCloseables(\n            List.of(",
     f"RetentionShutdown.runCloseables(\n            List.of("),
])
# manual for shutdown test - simpler full file replace
p = ROOT / "RetentionShutdownTest.java"
t = p.read_text(encoding="utf-8")
wm = wm_expr()
t = t.replace(
    "RetentionShutdown.runCloseables(\n            List.of(() -> order.add(1), () -> order.add(2), () -> order.add(3))\n        );",
    f"RetentionShutdown.runCloseables(\n            List.of(() -> order.add(1), () -> order.add(2), () -> order.add(3)),\n            {wm}\n        );",
)
t = t.replace(
    "                () -> order.add(3)\n            )\n        );",
    f"                () -> order.add(3)\n            ),\n            {wm}\n        );",
)
t = t.replace("RetentionShutdown.runCloseables(list);", f"RetentionShutdown.runCloseables(list, {wm});")
if "WorkerMessageSources" not in t:
    t = t.replace("import org.junit.jupiter.api.Test;", HELPER + "import org.junit.jupiter.api.Test;")
p.write_text(t, encoding="utf-8")

p = ROOT / "RetentionExportSuggesterTest.java"
t = p.read_text(encoding="utf-8")
if "WorkerMessageSources" not in t:
    t = t.replace("import org.junit.jupiter.api.Test;", HELPER + "import org.junit.jupiter.api.Test;")
t = t.replace(
    "RetentionExportSuggester.publishForChatCounts(nats, Map.of(chatId, 2));",
    f"RetentionExportSuggester.publishForChatCounts(nats, Map.of(chatId, 2), {wm});",
)
p.write_text(t, encoding="utf-8")

for fname in ["RetentionHotBodyJanitorDryRunTest.java", "RetentionHotBodyPassGaugesTest.java"]:
    p = ROOT / fname
    t = p.read_text(encoding="utf-8")
    if "WorkerMessageSources" not in t:
        t = t.replace("import org.junit.jupiter.api.Test;", HELPER + "import org.junit.jupiter.api.Test;")
    t = re.sub(
        r"(RetentionHotBodyJanitor\.runOnce\([\s\S]*?\n\s+(?:true|false))\n(\s+\);)",
        rf"\1,\n            {wm}\n\2",
        t,
    )
    p.write_text(t, encoding="utf-8")
    print("patched", fname)

print("done")
