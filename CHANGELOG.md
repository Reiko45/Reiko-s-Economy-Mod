# Changelog

Every release of Reiko's Economy Mod, newest first. The mod is for Minecraft 1.21.1 with NeoForge, and every
version works in singleplayer and on servers.

## 1.0.0

The first stable release. Everything from the 0.x versions below is in it.

- A mod icon: a gold coin stamped with an R, shown in the mods list.
- An up-to-date description in the mods list.
- Shop price labels no longer lose half their text when you look at them from an angle.
- The job board's "Contracts" tab label fits its button.
- `/taxes` no longer says the next wealth tax is "in 0.0 days" on a brand-new world.
- Checked on a real NeoForge dedicated server, as well as in singleplayer.
- Releases are now built by GitHub and attached to each release automatically.

## 0.17.0: Bounties

- Put money on another player with `/bounty place <player> <amount>`. Whoever kills them in PvP collects it.
- The bounty is held from your bank account, plus a 5% posting fee that leaves the economy.
- Kills with arrows, tamed pets, or by knocking someone into lava or off a cliff count.
- You can't collect or take back your own bounty. Unclaimed bounties are refunded after 7 days (the fee isn't).
- `/bounty` shows the anonymous board; `/bounty mine` shows what's on you and what you posted. Targets are
  warned, even if they were offline, and collections are announced.
- Admins can `/eco bounty clear <player>`. New `bounties` settings.

## 0.16.0: Shared accounts

- `/account create <name>` makes a bank account for a town or team. The owner adds and removes members, hands
  it over, or closes it.
- Every member can deposit, withdraw and pay other players from it.
- Members get their own debit card and PIN for it at the ATM.
- Team shops: a shop can send its sales to a shared account and pay from it when it buys.
- The account's history shows which member spent what.
- Shared accounts pay the wealth tax and stay off `/baltop`. Admins get `/eco account list|info|history|close`.

## 0.15.0: Safe trading

- Sneak and right-click a player, or type `/trade <player>`, to open a trade window. Both sides put in items and
  money, and nothing moves until both accept.
- Any change clears both accepts and locks Accept for 3 seconds; changed slots light up orange.
- Money and inventory room are checked before the swap. Closing, leaving, dying or walking away cancels the
  trade and returns everything.
- Money in a trade pays the same 2% fee as `/pay`.

## 0.14.0: Auction house

- `/ah` (or an Auction House block) to buy and sell with everyone: buy-now listings or timed auctions with bids.
- Search, sort and pages; bids are held and refunded when outbid; a last-minute bid adds time.
- Items go straight to your inventory, or wait in the Collect tab.
- A $1 listing fee and a 5% cut leave the economy.

## 0.13.0: Job contracts

- Three new contracts every real day, from the jobs you have: deliveries handed in at a job board, or work like
  "Break Iron Ore x 16". Finishing one pays a bonus and job XP.
- Selected tabs now get a gold frame instead of looking greyed out.

## 0.12.0: Savings and loans

- A savings account that earns interest every 7 Minecraft days.
- Bank loans paid back in weekly payments, with a limit that grows as you repay on time.
- `/bank` shows your balance, savings and loan.

## 0.11.0: Shop finder

- `/shops find <item>` lists where to buy (cheapest first) and where to sell (best price first), anywhere in the
  world. `/shops near` and `/shops mine` too.

## 0.10.0: Taxes

- A 5% sales tax at shops, a 2% fee on `/pay`, a $1 ATM fee and a weekly 1% wealth tax over $10,000. Tax money
  leaves the economy to keep prices from creeping up. All of them can be changed or turned off.
- `/taxes` for players, `/eco taxes` for admins.

## 0.9.0: Shop upgrades

- Shops can buy items from players too, paid from the owner's bank.
- Owners are told when a shop sells out, fills up or can't afford to buy, including while they were offline.
- Hoppers and pipes can restock shops (but never take stock out).
- Credit card bills list every purchase.

## 0.8.0: Jobs

- Six jobs (Miner, Lumberjack, Digger, Farmer, Hunter, Fisher) at a job board, paid straight into the bank.
- Levels raise your pay. Blocks you placed yourself, spawner mobs and creative mode don't pay.
- Jobs are datapack files, so servers can change them or add new ones.

## 0.7.0: Wallets

- A wallet holds all your coins and bills in one slot, and shops, villagers and ATMs pay from it and put change
  back in it.

## 0.6.1

- The ATM is now two columns wide instead of tall, so it fits on every screen.

## 0.6.0: Credit cards

- Spend now, pay later: statements every 7 Minecraft days, interest only on what you don't pay off, a limit that
  grows when you pay on time, and cash advances at the ATM.

## 0.5.0: Villagers use money

- Villagers and wandering traders take card or cash instead of emeralds and pay cash for what they buy.
  Everything else about villager trading works as before.

## 0.4.0: Debit cards

- Get a card with a 4-digit PIN at an ATM. Pay at shops by card or with cash (and get change).
- Three wrong PINs lock the card; a new card cancels the old one.

## 0.3.0: Cash and ATMs

- Coins and bills from 1¢ to $100, and a craftable ATM to withdraw and deposit them.

## 0.2.0: Shops

- Player shops: pick an item, set a price, fill the stock, and get paid even while offline.
- Server shops for admins, with infinite stock.
- A transaction log: `/transactions` for players, `/eco history` for admins.

## 0.1.0: Bank accounts

- Every player gets a bank account with a starting balance.
- `/balance`, `/pay`, `/baltop`, and `/eco give|take|set|reset` for admins.
- An API with transaction events for other mods.
