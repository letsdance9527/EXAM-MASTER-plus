"""
Question bank configuration manager.

Reads and writes bank_config.json to support switching between
different question banks at runtime.
"""

import json
import os

# Path to the bank config file, relative to this module's location
_BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(_BASE_DIR, "bank_config.json")


def load_bank_config():
    """Load the full bank configuration from bank_config.json."""
    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def save_bank_config(config):
    """Save the bank configuration to bank_config.json."""
    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(config, f, ensure_ascii=False, indent=2)


def get_active_bank():
    """Return the currently active bank dict, or None if not found."""
    config = load_bank_config()
    active_id = config.get("active_bank")
    for bank in config.get("banks", []):
        if bank["id"] == active_id:
            return bank
    # Fallback to first bank if active not found
    banks = config.get("banks", [])
    if banks:
        return banks[0]
    return None


def get_all_banks():
    """Return a list of all available bank dicts."""
    config = load_bank_config()
    return config.get("banks", [])


def switch_bank(bank_id):
    """
    Switch the active bank to the given bank_id.

    Returns the new active bank dict.
    Raises ValueError if bank_id is not found.
    """
    config = load_bank_config()
    bank_ids = [b["id"] for b in config.get("banks", [])]
    if bank_id not in bank_ids:
        raise ValueError(f"题库 '{bank_id}' 不存在。可用题库: {', '.join(bank_ids)}")

    config["active_bank"] = bank_id
    save_bank_config(config)

    # Return the new active bank
    for bank in config["banks"]:
        if bank["id"] == bank_id:
            return bank
    raise ValueError(f"题库 '{bank_id}' 配置异常")


def get_csv_path(bank_id=None):
    """
    Return the absolute path to a bank's CSV file.

    Args:
        bank_id: Bank ID, or None to use the active bank.

    Returns:
        str: Absolute path to the CSV file.
    """
    if bank_id is None:
        bank = get_active_bank()
    else:
        config = load_bank_config()
        bank = next((b for b in config["banks"] if b["id"] == bank_id), None)

    if bank is None:
        raise ValueError("未找到题库配置")

    csv_file = bank["csv_file"]
    # If already absolute, use as-is; otherwise resolve relative to project root
    if os.path.isabs(csv_file):
        return csv_file
    return os.path.join(_BASE_DIR, csv_file)
