# Reiko's Economy Mod

An economy mod for Minecraft 1.21.1 on NeoForge, built for singleplayer and multiplayer alike.

**Status: early development (v0.3.0, Phase 3 in progress).** Bank accounts, money commands, player shops, the server shop, a transaction log, coins and bills, and ATMs work. Debit cards and coin-based villager trading are planned next.

## What works now

- **Bank accounts.** Every player gets an account the first time they join, with a configurable starting balance. Accounts are saved with the world and work for offline players.
- **Exact money math.** Amounts use two decimal places and are stored as whole numbers of cents, so balances never drift.
- **Player shops.** Craft a Shop, place it and it's yours. Pick the item to sell, set a price per bundle and fill the stock. Other players buy from it and the money goes straight into your bank account, even while you're offline.
- **Server shop.** Admins place Server Shops with infinite stock that sell an item, buy it back, or both. This is how money first enters a new world.
- **Transaction log.** Every balance change is recorded, so players can check their own history and admins can look into anyone's.
- **Cash.** Coins for amounts under a dollar and bills from a dollar up, like real money. Withdraw and deposit them at an ATM.

### Cash and ATMs

| Coins | Bills |
| --- | --- |
| Penny (1¢), Nickel (5¢), Dime (10¢), Quarter (25¢), Half Dollar (50¢) | $1, $2, $5, $10, $20, $50, $100 |

Cash moves between your bank balance and your inventory at an ATM, so it never creates or destroys money. Hover over any coin or bill to see what it (and the whole stack) is worth.

**Crafting an ATM:** iron ingots on both sides, with a glass pane, redstone and a gold ingot down the middle.

```text
I G I
I R I
I K I
```

**Using an ATM:** right-click it.

- **Withdraw:** type an amount (like `37.41`) and press **Withdraw**, or use the $20, $50 and $100 buttons. You get the fewest coins and bills, like a real ATM. The ATM refuses if your inventory can't hold the cash, so nothing gets dropped on the ground.
- **Deposit:** put coins and bills in the deposit slots (shift-click works) and press **Deposit**, or press **Deposit all** to bank every coin and bill in your inventory. Anything left in the slots comes back to you when you close the screen.

Shops still charge your bank balance; paying with cash or a debit card comes later in Phase 3.

### Shops

**Crafting a Shop:** a chest with a gold ingot above it, surrounded by planks.

```text
P G P
P C P
P P P
```

**Setting one up (owner):** right-click your shop.

1. Click the gold slot with an item to sell it. The stack size you hold is one bundle, so clicking with 16 cobblestone sells 16 at a time. Right-click instead to sell them one at a time.
2. Type a price per bundle and press **Save**.
3. Put stock in the slots below. Only the item for sale fits.

The item and its price float above the shop, with "Out of stock" when it runs dry. You can't switch to a different item while stock remains.

**Buying (everyone else):** right-click the shop, choose how many bundles with **-** and **+** (hold Shift for steps of 10) and press **Buy**. If the owner changes the price while your screen is open, the purchase is refused so you never pay a price you didn't see.

**Server shops (admins):** place a Server Shop from the creative tab and right-click it to set the item, the price players pay to buy, and the price the shop pays to buy it back. Leave either price blank or 0 to turn that side off. After that, everyone gets the trade screen. Admins sneak + right-click with an empty hand to change it again.

**Protection:**
- Only the owner (or an admin) can break a shop.
- Shops survive explosions, withers and the ender dragon.
- Hoppers and pipes can't pull stock out.

Admins can buy from other players' shops like anyone else, and sneak + right-click with an empty hand to manage them.

### Commands

Permission nodes are in brackets:

| Command | Who | What it does |
| --- | --- | --- |
| `/balance` or `/bal` | Everyone `[reikoeconomy.command.balance]` | Shows your balance |
| `/bal <player>` | Everyone `[reikoeconomy.command.balance.others]` | Shows another player's balance |
| `/pay <player> <amount>` | Everyone `[reikoeconomy.command.pay]` | Sends money, e.g. `/pay Steve 12.50`. Works when they're offline |
| `/baltop [page]` | Everyone `[reikoeconomy.command.baltop]` | Richest players, plus the total in all accounts |
| `/transactions [page]` | Everyone `[reikoeconomy.command.history]` | Your recent transactions |
| `/eco give\|take\|set <players> <amount>` | Ops `[reikoeconomy.command.admin]` | Changes balances |
| `/eco reset <players>` | Ops `[reikoeconomy.command.admin]` | Resets balances to the starting amount |
| `/eco history <player> [page]` | Ops `[reikoeconomy.command.admin]` | A player's recent transactions |

Shop permissions:
- `[reikoeconomy.shop.create]` places player shops. Everyone has it by default.
- `[reikoeconomy.shop.admin]` places and sets up server shops, and manages or breaks anyone's shop. Ops have it by default.

Admin permissions need op level 2, or cheats turned on in singleplayer. Permission mods can override any node.

## Settings

Settings live in `config/reikoeconomy-server.toml` and apply to every world. To give one world different settings, copy the file into that world's `serverconfig/` folder and edit the copy. You can also change them from the Mods screen.

| Setting | Default | Meaning |
| --- | --- | --- |
| `currency.symbol` | `$` | Symbol shown with amounts |
| `currency.symbolAfterAmount` | `false` | `100.00 Coins` instead of `$100.00` |
| `accounts.startingBalance` | `100.0` | Money in every new account |
| `commands.baltopPageSize` | `10` | Players per `/baltop` page |
| `log.maxEntries` | `5000` | Recent transactions kept for the history commands |
| `atm.maxWithdrawal` | `10000.0` | Most cash one ATM withdrawal can pay out |

## For other mods

Use `EconomyApi.get()` (in `com.reiko.economy.api`) to read balances and deposit, withdraw or transfer money. Every change fires a cancelable `TransactionEvent.Pre` and a `TransactionEvent.Post` on `NeoForge.EVENT_BUS`. Amounts are `long`s in cents (`1250` = 12.50).

## Building and testing

You need JDK 21. Gradle downloads everything else.

```sh
./gradlew build             # compile, run unit tests, build the jar into build/libs/
./gradlew runClient         # start a dev client with the mod
./gradlew runGameTestServer # run the in-game tests, then exit
```

In IntelliJ IDEA, open the folder as a Gradle project and use the generated run configurations. The first build takes a few minutes while it sets up Minecraft.

The dev server and the game-test server need `eula=true` in `run/eula.txt` (Minecraft's EULA) before they start.

## Project layout

```text
src/main/java/com/reiko/economy/
├── ReikoEconomy.java        mod entry point
├── api/                     public API and events for other mods
├── core/                    EconomyService, Ledger and TransactionLog (saved data), Money, Denomination
├── bank/                    ATM withdrawals and deposits
├── item/                    coins and bills
├── block/                   shop and ATM blocks, shop block entities
├── menu/                    shop and ATM menus (shared by client and server)
├── shop/                    access rules, results and inventory sync for shops
├── network/                 packets between client and server
├── client/                  screens, the floating-item renderer, client-only handlers
├── command/                 /bal /pay /baltop /transactions /eco and permission nodes
├── config/                  server settings
├── event/                   login, shop protection and transaction-log hooks
├── registry/                blocks, items, block entities, menus, creative tab
└── gametest/                in-game tests
```

## License

GPL-3.0. See [LICENSE](LICENSE).
