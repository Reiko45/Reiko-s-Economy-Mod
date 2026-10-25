# Reiko's Economy Mod

An economy mod for Minecraft 1.21.1 on NeoForge, built for singleplayer and multiplayer alike.

**Status: early development (v0.4.0, Phase 3 in progress).** Bank accounts, money commands, player shops, the server shop, a transaction log, coins and bills, ATMs and debit cards work. Coin-based villager trading is planned next, and credit cards after that.

## What works now

- **Bank accounts.** Every player gets an account the first time they join, with a configurable starting balance. Accounts are saved with the world and work for offline players.
- **Exact money math.** Amounts use two decimal places and are stored as whole numbers of cents, so balances never drift.
- **Player shops.** Craft a Shop, place it and it's yours. Pick the item to sell, set a price per bundle and fill the stock. Other players buy from it and the money goes straight into your bank account, even while you're offline.
- **Server shop.** Admins place Server Shops with infinite stock that sell an item, buy it back, or both. This is how money first enters a new world.
- **Transaction log.** Every balance change is recorded, so players can check their own history and admins can look into anyone's.
- **Cash.** Coins for amounts under a dollar and bills from a dollar up, like real money. Withdraw and deposit them at an ATM.
- **Debit cards.** Get a card with a 4-digit PIN at any ATM. You need it to use your bank balance at ATMs and shops; without one, you pay in cash and get change back.

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

**Using an ATM:** right-click it, put your debit card in the card slot at the top left and type your PIN. Every ATM action except getting a new card needs both.

- **Balance:** press **Balance** to see what's in the card's account.
- **Withdraw:** type an amount (like `37.41`) and press **Withdraw**, or use the $20, $50 and $100 buttons. You get the fewest coins and bills, like a real ATM. The ATM refuses if your inventory can't hold the cash, so nothing gets dropped on the ground.
- **Deposit:** put coins and bills in the deposit slots (shift-click works) and press **Deposit**, or press **Deposit all** to bank every coin and bill in your inventory. Anything left in the slots comes back to you when you close the screen.

### Debit cards

Your bank balance is only reachable with a debit card. Coins and bills in your inventory work without one.

- **Getting one:** at an ATM, type the PIN you want (exactly 4 digits) and press **New card**. Your first card is free. After that, each new card costs $5 from your account (the `atm.cardReplacementFee` setting).
- **One card at a time:** each account has one working card. Getting a new card cancels the old one, so a lost or stolen card can be shut off from any ATM.
- **Wrong PINs:** three wrong PINs in a row lock the card. A right PIN before then resets the count. The owner gets a new card at an ATM to unlock their account.
- **Lending:** a card pays from its owner's account, not from whoever holds it. Anyone who knows the PIN can use it, so you can lend your card to a friend. Only give the PIN to people you trust.
- Hover over a card to see whose it is and its last four digits.

### Paying at shops

The buy screen shows the card you're carrying and how much cash you have, with two ways to pay:

- **Card:** type the card's PIN and press **Card**. The money moves from the card's account to the shop owner.
- **Cash:** press **Cash**. Your coins and bills are handed over, and any change comes back as the fewest coins and bills. The shop owner still gets paid into their bank account. If your inventory can't hold the change, the purchase is refused.

The server shop takes card or cash the same way. When you sell to it, it pays you in cash.

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

**Buying (everyone else):** right-click the shop, choose how many bundles with **-** and **+** (hold Shift for steps of 10), then pay by **Card** (with your PIN) or **Cash** (see [Paying at shops](#paying-at-shops)). If the owner changes the price while your screen is open, the purchase is refused so you never pay a price you didn't see.

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
| `atm.cardReplacementFee` | `5.0` | Cost of each debit card after the first (0 = always free) |

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
├── core/                    EconomyService, Ledger and TransactionLog (saved data), Money, cash and card logic
├── bank/                    ATM withdrawals and deposits, paying with cash
├── item/                    coins, bills and debit cards
├── block/                   shop and ATM blocks, shop block entities
├── menu/                    shop and ATM menus (shared by client and server)
├── shop/                    access rules, card and cash payments, inventory sync for shops
├── network/                 packets between client and server
├── client/                  screens, the floating-item renderer, client-only handlers
├── command/                 /bal /pay /baltop /transactions /eco and permission nodes
├── config/                  server settings
├── event/                   login, shop protection and transaction-log hooks
├── registry/                blocks, items, data components, block entities, menus, creative tab
└── gametest/                in-game tests
```

## License

GPL-3.0. See [LICENSE](LICENSE).
