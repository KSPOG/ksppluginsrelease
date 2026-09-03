from pathlib import Path
import re

CONFIG_PATHS = [
    "KSPGELooter/KSPGELooterConfig.java",
    "KSPTradeReceiver/KSPTradeReceiverConfig.java",
    "KspBoneAshPlugin/KspBoneAshConfig.java",
    "f2pprocessingfactory/F2PProcessingFactoryConfig.java",
    "kspaiofighter/KspAioFighterConfig.java",
    "kspautorun/KspAutoRunConfig.java",
    "kspbankorganizer/KspBankOrganizerConfig.java",
    "kspbondgoal/KspBondGoalConfig.java",
    "kspbryophyta/KspBryophytaConfig.java",
    "kspdirectfishing/KspDirectFishingConfig.java",
    "kspf2phighalchtrader/KspF2PHighAlchTraderConfig.java",
    "kspfleshcrawlers/KspFleshCrawlerConfig.java",
    "kspjewelrycrafter/KspJewelryCrafterConfig.java",
    "kspkaramjafishing/KspKaramjaFishingConfig.java",
    "kspmadcow/KspMadCowConfig.java",
    "ksprenderdisable/KspDisableRenderConfig.java",
    "kspsmartsmelter/KspSmartSmelterConfig.java",
    "kspsmartsuperheat/KspSmartSuperheatConfig.java",
    "kspwillowchopper/KspWillowChopperConfig.java",
    "mining/AutoMiningConfig.java",
]

SUPPORT_IMPORT = "import net.runelite.client.plugins.microbot.kspsupport.KspSupportConfig;"

SUPPORT_CONFIG = '''package net.runelite.client.plugins.microbot.kspsupport;

import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigItem;

/**
 * Shared configuration mixin that exposes the same support button on every KSP plugin.
 */
public interface KspSupportConfig
{
    String SUPPORT_KEY = "kspSupportDiscord";
    String SUPPORT_URL = "https://discord.gg/mTBVf5FKB2";

    @ConfigItem(
            keyName = SUPPORT_KEY,
            name = "Support",
            description = "Open the KSP Plugins support Discord.",
            position = 10_000
    )
    default ConfigButton kspSupportDiscord()
    {
        return new ConfigButton();
    }
}
'''

SUPPORT_PLUGIN = '''package net.runelite.client.plugins.microbot.kspsupport;

import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.LinkBrowser;

/**
 * Hidden always-on listener for the shared Support config button.
 * Keeping the listener independent means the Support button also works while the
 * plugin whose configuration is being viewed is disabled.
 */
@PluginDescriptor(
        name = "KSP Support",
        description = "Shared KSP Plugins support-link handler.",
        tags = {"ksp", "support", "discord"},
        authors = {"KSP"},
        version = "1.0.0",
        enabledByDefault = true,
        alwaysOn = true,
        hidden = true,
        isExternal = true
)
public class KspSupportPlugin extends Plugin
{
    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (event != null && KspSupportConfig.SUPPORT_KEY.equals(event.getKey()))
        {
            LinkBrowser.browse(KspSupportConfig.SUPPORT_URL);
        }
    }
}
'''


def add_import(text: str) -> str:
    if SUPPORT_IMPORT in text:
        return text
    match = re.search(r"^package\s+[^;]+;\s*", text, re.MULTILINE)
    if not match:
        raise RuntimeError("No package declaration found")
    return text[:match.end()] + "\n" + SUPPORT_IMPORT + "\n" + text[match.end():].lstrip("\n")


def add_support_parent(text: str, path: str) -> str:
    if re.search(r"public\s+interface\s+\w+\s+extends\s+[^\{]*\bKspSupportConfig\b", text, re.DOTALL):
        return text

    extends_pattern = re.compile(r"(public\s+interface\s+\w+\s+extends\s+)([^\{]+?)(\s*\{)", re.DOTALL)
    match = extends_pattern.search(text)
    if match:
        parents = match.group(2).rstrip()
        replacement = match.group(1) + parents + ", KspSupportConfig" + match.group(3)
        return text[:match.start()] + replacement + text[match.end():]

    plain_pattern = re.compile(r"(public\s+interface\s+\w+)(\s*\{)")
    match = plain_pattern.search(text)
    if not match:
        raise RuntimeError(f"Could not locate config interface declaration in {path}")
    replacement = match.group(1) + " extends KspSupportConfig" + match.group(2)
    return text[:match.start()] + replacement + text[match.end():]


def main():
    support_dir = Path("kspsupport")
    support_dir.mkdir(exist_ok=True)
    (support_dir / "KspSupportConfig.java").write_text(SUPPORT_CONFIG, encoding="utf-8")
    (support_dir / "KspSupportPlugin.java").write_text(SUPPORT_PLUGIN, encoding="utf-8")

    changed = 0
    for path_str in CONFIG_PATHS:
        path = Path(path_str)
        if not path.is_file():
            raise RuntimeError(f"Missing expected config: {path_str}")
        original = path.read_text(encoding="utf-8")
        updated = add_support_parent(add_import(original), path_str)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed += 1

    if changed != len(CONFIG_PATHS):
        raise RuntimeError(f"Expected to patch {len(CONFIG_PATHS)} configs, patched {changed}")

    for path_str in CONFIG_PATHS:
        text = Path(path_str).read_text(encoding="utf-8")
        if SUPPORT_IMPORT not in text or "KspSupportConfig" not in text:
            raise RuntimeError(f"Support mixin validation failed for {path_str}")

    Path(".github/workflows/add-ksp-support-buttons.yml").unlink(missing_ok=True)
    Path(".github/scripts/add_ksp_support_buttons.py").unlink(missing_ok=True)


if __name__ == "__main__":
    main()
