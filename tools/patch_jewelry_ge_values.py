from pathlib import Path
import re

path = Path("kspjewelrycrafter/KspJewelryCrafterScript.java")
text = path.read_text(encoding="utf-8")

old = "import net.runelite.api.gameval.InterfaceID;\n"
new = "import net.runelite.api.gameval.InterfaceID;\nimport net.runelite.api.gameval.VarbitID;\n"
assert text.count(old) == 1, "InterfaceID import anchor changed"
text = text.replace(old, new, 1)

old = (
    "    private static final int GE_SELECTED_PRICE_CHILD = 41;\n"
    "    private static final int GE_PRICE_CLICK_DELAY_MIN_MS = 650;\n"
    "    private static final int GE_PRICE_CLICK_DELAY_MAX_MS = 950;"
)
new = (
    "    private static final int GE_SELECTED_PRICE_CHILD = 41;\n"
    "    private static final int GE_OFFER_PRICE_VARBIT = 4398;\n"
    "    private static final int GE_VALUE_ENTRY_ATTEMPTS = 3;\n"
    "    private static final int GE_PRICE_CLICK_DELAY_MIN_MS = 650;\n"
    "    private static final int GE_PRICE_CLICK_DELAY_MAX_MS = 950;"
)
assert text.count(old) == 1, "GE constants anchor changed"
text = text.replace(old, new, 1)

method_lines = [
    "    private boolean setGeOfferValue(int child, int value, String label)",
    "    {",
    "        if (value <= 0) return false;",
    "        if (geOfferValueMatches(child, value)) return true;",
    "",
    "        for (int attempt = 1; attempt <= GE_VALUE_ENTRY_ATTEMPTS; attempt++)",
    "        {",
    "            if (!Rs2GrandExchange.isOpen() || !geSetupOpen())",
    "            {",
    "                status = \"GE closed before setting \" + label;",
    "                return false;",
    "            }",
    "            if (!geSetupChildVisible(child))",
    "            {",
    "                status = \"Waiting for GE \" + label + \" control\";",
    "                sleep(250, 450);",
    "                continue;",
    "            }",
    "",
    "            status = attempt == 1",
    "                ? \"Setting GE \" + label + \": \" + value",
    "                : \"Retrying GE \" + label + \" (\" + attempt + \"/\" + GE_VALUE_ENTRY_ATTEMPTS + \"): \" + value;",
    "",
    "            if (child == GE_PRICE_X_CHILD)",
    "                sleep(GE_PRICE_CLICK_DELAY_MIN_MS, GE_PRICE_CLICK_DELAY_MAX_MS);",
    "            else",
    "                sleep(300, 500);",
    "",
    "            if (!clickGeSetupChildSafely(child))",
    "            {",
    "                sleep(250, 450);",
    "                continue;",
    "            }",
    "            if (!sleepUntil(() -> gePriceInputOpen() || !Rs2GrandExchange.isOpen(), 3_000))",
    "            {",
    "                status = \"Waiting for GE \" + label + \" input\";",
    "                sleep(250, 450);",
    "                continue;",
    "            }",
    "            if (!Rs2GrandExchange.isOpen()) return false;",
    "",
    "            // Give the chatbox input the same settling time used by Microbot's GE utilities.",
    "            sleep(600, 1_000);",
    "            Rs2GrandExchange.setChatboxValue(value);",
    "            sleep(500, 750);",
    "            Rs2Keyboard.enter();",
    "",
    "            if (!sleepUntil(() -> !gePriceInputOpen() || !Rs2GrandExchange.isOpen(), 3_000))",
    "            {",
    "                status = \"Waiting for GE \" + label + \" entry\";",
    "                sleep(300, 500);",
    "                continue;",
    "            }",
    "            if (!Rs2GrandExchange.isOpen()) return false;",
    "",
    "            sleep(800, 1_100);",
    "            if (sleepUntil(() -> geOfferValueMatches(child, value), 2_000)) return true;",
    "        }",
    "",
    "        status = \"GE \" + label + \" did not update to \" + value;",
    "        return false;",
    "    }",
    "",
    "    private boolean geOfferValueMatches(int child, int value)",
    "    {",
    "        if (child == GE_PRICE_X_CHILD)",
    "            return Microbot.getVarbitValue(GE_OFFER_PRICE_VARBIT) == value;",
    "        if (child == GE_QUANTITY_X_CHILD)",
    "            return Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY) == value;",
    "        return false;",
    "    }",
    "",
    "    private boolean submitGeOfferSafely",
]
replacement = "\n".join(method_lines)
pattern = r"    private boolean setGeOfferValue\(int child, int value, String label\)\n    \{.*?\n    \}\n\n    private boolean submitGeOfferSafely"
text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
assert count == 1, "setGeOfferValue method anchor changed"

modify_lines = [
    "        if (!setGeOfferValue(GE_PRICE_X_CHILD, newPrice, \"modified price\"))",
    "        {",
    "            status = Rs2GrandExchange.isOpen()",
    "                ? \"Unable to set modified GE price\"",
    "                : \"GE closed during modified price entry - recovering\";",
    "            return false;",
    "        }",
    "",
]
replacement = r"\1" + "\n".join(modify_lines)
pattern = (
    r"(        if \(!Rs2GrandExchange\.isOpen\(\)\)\n"
    r"        \{\n"
    r"            status = \"GE closed before price edit - recovering\";\n"
    r"            return false;\n"
    r"        \}\n\n).*?"
    r"(?=        status = \"Confirming modified GE price\";)"
)
text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
assert count == 1, "modifyGeOffer price-entry anchor changed"

# Focused source validation.
assert "import net.runelite.api.gameval.VarbitID;" in text
assert "GE_OFFER_PRICE_VARBIT = 4398" in text
assert "GE_VALUE_ENTRY_ATTEMPTS = 3" in text
assert "Microbot.getVarbitValue(GE_OFFER_PRICE_VARBIT) == value" in text
assert "Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY) == value" in text
assert "sleep(600, 1_000);" in text
assert "sleep(500, 750);" in text
assert "setGeOfferValue(GE_PRICE_X_CHILD, newPrice, \"modified price\")" in text

path.write_text(text, encoding="utf-8")
print("Jewelry GE value-entry patch applied and verified")
