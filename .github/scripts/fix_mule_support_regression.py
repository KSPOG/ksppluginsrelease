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

SUPPORT_METHOD = '''\n    @ConfigItem(\n            keyName = "kspSupportDiscord",\n            name = "Support",\n            description = "Open the KSP Plugins support Discord.",\n            position = 10_000\n    )\n    default ConfigButton kspSupportDiscord()\n    {\n        return new ConfigButton();\n    }\n'''


def ensure_import(text: str, import_line: str) -> str:
    if import_line in text:
        return text
    package = re.search(r"^package\s+[^;]+;\s*", text, re.MULTILINE)
    if not package:
        raise RuntimeError("Missing package declaration")
    return text[:package.end()] + "\n" + import_line + "\n" + text[package.end():].lstrip("\n")


def patch_config(path: Path):
    text = path.read_text(encoding="utf-8")
    text = text.replace("import net.runelite.client.plugins.microbot.kspsupport.KspSupportConfig;\n", "")
    text = text.replace("import net.runelite.client.plugins.microbot.kspsupport.KspSupportConfig;\r\n", "")

    # Restore the pre-support inheritance tree. This is deliberately conservative:
    # support is a local config item now and no longer participates in config proxy inheritance.
    text = re.sub(r",\s*KspSupportConfig(?=\s*\{)", "", text)
    text = re.sub(r"\s+extends\s+KspSupportConfig(?=\s*\{)", "", text)

    text = ensure_import(text, "import net.runelite.client.config.ConfigButton;")

    if "default ConfigButton kspSupportDiscord()" not in text:
        idx = text.rfind("}")
        if idx < 0:
            raise RuntimeError(f"Missing closing brace: {path}")
        text = text[:idx].rstrip() + "\n" + SUPPORT_METHOD + "}\n"

    path.write_text(text, encoding="utf-8")


def main():
    for p in CONFIG_PATHS:
        path = Path(p)
        if not path.exists():
            raise RuntimeError(f"Missing config: {p}")
        patch_config(path)

    support_plugin = Path("kspsupport/KspSupportPlugin.java")
    text = support_plugin.read_text(encoding="utf-8")
    text = text.replace("KspSupportConfig.SUPPORT_KEY", '"kspSupportDiscord"')
    text = text.replace("KspSupportConfig.SUPPORT_URL", '"https://discord.gg/mTBVf5FKB2"')
    support_plugin.write_text(text, encoding="utf-8")

    support_config = Path("kspsupport/KspSupportConfig.java")
    if support_config.exists():
        support_config.unlink()

    # Validate mule-enabled configs are back to exactly their previous shared parent relationship.
    mule_configs = []
    for p in CONFIG_PATHS:
        text = Path(p).read_text(encoding="utf-8")
        if "KspMuleConfig" in text:
            mule_configs.append(p)
            if "KspSupportConfig" in text:
                raise RuntimeError(f"Support inheritance still present in mule config: {p}")
        if "default ConfigButton kspSupportDiscord()" not in text:
            raise RuntimeError(f"Support button missing: {p}")

    if not mule_configs:
        raise RuntimeError("No mule-enabled configs found during validation")

    Path(".github/workflows/fix-mule-support-regression.yml").unlink(missing_ok=True)
    Path(".github/scripts/fix_mule_support_regression.py").unlink(missing_ok=True)


if __name__ == "__main__":
    main()
