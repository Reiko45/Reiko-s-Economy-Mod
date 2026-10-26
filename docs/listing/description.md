<!--
Listing text for Modrinth and CurseForge. Both accept Markdown, so paste everything below the line into the
project's description. The images load from GitHub, so they work on both sites.

Short summary (Modrinth's "Summary" field, CurseForge's "Summary"):
A complete economy: bank accounts, cash and cards, ATMs, player shops, an auction house, safe trading, jobs, bounties and more.

Suggested categories: Economy, Utility, Game Mechanics. Environment: required on both client and server.
-->

![A market with player shops, an ATM, an auction house and a job board](https://raw.githubusercontent.com/Reiko45/Reiko-s-Economy-Mod/main/docs/listing/screenshots/market.png)

**Reiko's Economy Mod** is a complete economy for Minecraft 1.21.1 on NeoForge, built for singleplayer and multiplayer alike. Everything is in one mod, with no other mods needed.

## What's in it

**Money that works like money**
- Every player has a **bank account** with a starting balance. Pay people with `/pay`, even while they're offline.
- **Coins and bills** from 1¢ to $100 that you can hold, drop and trade, and a **wallet** that keeps them all in one slot.
- **ATMs** to withdraw and deposit cash, with **debit cards** protected by a 4-digit PIN.
- **Credit cards** with monthly statements, interest and limits that grow when you pay on time.
- **Savings accounts** that earn interest, and **bank loans** paid back in weekly payments.

**Buying and selling**
- **Player shops**: pick an item, set a price, fill the stock and get paid even while you're offline. Shops can buy items from players too, and hoppers can restock them.
- An **auction house** (`/ah`) with buy-now listings and timed auctions.
- **Safe trading** between players: both put in items and money, and nothing moves until both accept the same offer.
- **Villagers** trade for money instead of emeralds, by card or cash.
- `/shops find <item>` shows where to buy or sell anything, cheapest first.

**Earning**
- Six **jobs** (Miner, Lumberjack, Digger, Farmer, Hunter, Fisher) that pay as you work, with levels and **daily contracts**.
- **Bounties**: put money on another player, and whoever takes them out in PvP collects it.

**For groups and servers**
- **Shared accounts** for towns and teams, with their own cards and team shops.
- **Taxes and fees** that take money out of the economy, so prices don't creep up forever. Every rate can be changed or turned off.
- **Admin tools**: give, take and set balances, see anyone's transaction history, and manage shops, auctions, shared accounts and bounties.
- **Permission nodes** for every command, so permission mods can control who can do what.
- An **API** with transaction events for other mods.

## Screenshots

![Buying from a player shop](https://raw.githubusercontent.com/Reiko45/Reiko-s-Economy-Mod/main/docs/listing/screenshots/shop.png)
![The ATM](https://raw.githubusercontent.com/Reiko45/Reiko-s-Economy-Mod/main/docs/listing/screenshots/atm.png)
![The auction house](https://raw.githubusercontent.com/Reiko45/Reiko-s-Economy-Mod/main/docs/listing/screenshots/auction.png)
![A trade between two players](https://raw.githubusercontent.com/Reiko45/Reiko-s-Economy-Mod/main/docs/listing/screenshots/trade.png)
![Daily job contracts](https://raw.githubusercontent.com/Reiko45/Reiko-s-Economy-Mod/main/docs/listing/screenshots/contracts.png)
![A villager trading for money](https://raw.githubusercontent.com/Reiko45/Reiko-s-Economy-Mod/main/docs/listing/screenshots/villager.png)

## Installing

Install NeoForge 21.1.265 or newer for Minecraft 1.21.1, then put the mod in your `mods` folder. On a server, the server and everyone who joins need it.

All the settings are in `config/reikoeconomy-server.toml`, or in-game under **Mods → Reiko's Economy Mod → Config**. The full guide, with every command and setting, is on [GitHub](https://github.com/Reiko45/Reiko-s-Economy-Mod#readme).

## Links

- [Full guide and source code](https://github.com/Reiko45/Reiko-s-Economy-Mod)
- [Changelog](https://github.com/Reiko45/Reiko-s-Economy-Mod/blob/main/CHANGELOG.md)
- [Report a bug](https://github.com/Reiko45/Reiko-s-Economy-Mod/issues)

Licensed under GPL-3.0.
