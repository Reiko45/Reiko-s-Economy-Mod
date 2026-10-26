# Reiko's Economy Mod

An economy mod for Minecraft 1.21.1 on NeoForge, built for singleplayer and multiplayer alike.

**Status: early development (v0.11.0).** Bank accounts, money commands, player shops that sell and buy, the server shop, a transaction log, coins and bills, wallets, ATMs, debit cards, villagers that trade for money, credit cards, jobs and taxes all work.

## What works now

- **Bank accounts.** Every player gets an account the first time they join, with a configurable starting balance. Accounts are saved with the world and work for offline players.
- **Exact money math.** Amounts use two decimal places and are stored as whole numbers of cents, so balances never drift.
- **Player shops.** Craft a Shop, place it and it's yours. Pick the item, set a price per bundle and fill the stock, by hand or with a hopper. Other players buy from it and the money goes straight into your bank account, even while you're offline. Set a "shop pays" price too and players can sell the item to you. You hear when a shop sells out, and what your shops did while you were away. `/shops find <item>` shows where to buy or sell anything.
- **Server shop.** Admins place Server Shops with infinite stock that sell an item, buy it back, or both. This is how money first enters a new world.
- **Transaction log.** Every balance change is recorded, so players can check their own history and admins can look into anyone's.
- **Cash.** Coins for amounts under a dollar and bills from a dollar up, like real money. Withdraw and deposit them at an ATM.
- **Wallets.** Keep all your coins and bills in one slot. Shops, villagers and ATMs pay from it and put change back in it.
- **Debit cards.** Get a card with a 4-digit PIN at any ATM. You need it to use your bank balance at ATMs and shops; without one, you pay in cash and get change back.
- **Jobs.** Sign up at a job board as a Miner, Lumberjack, Digger, Farmer, Hunter or Fisher and get paid for the work. Levels raise your pay.
- **Villagers use money.** Villagers and wandering traders take card or cash instead of emeralds, and pay cash for what they buy from you.
- **Credit cards.** Spend now and pay later. Bills come every 7 Minecraft days; pay in full to avoid interest. Paying on time raises your limit, and missed payments cost you.
- **Taxes.** A small sales tax at shops, a fee on `/pay`, an ATM fee and a weekly wealth tax on big balances. Tax money disappears, which keeps prices from creeping up as everyone gets richer. Admins can change or turn off each one.

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

### Wallets

A wallet holds any number of coins and bills in one inventory slot, so cash stops cluttering your inventory.

**Crafting a wallet:** five leather and a string.

```text
L S L
L L L
```

- **Putting cash in:** click coins or bills onto the wallet in your inventory, or right-click cash with the wallet on your cursor (like a bundle). Cash you pick up off the ground goes straight into your wallet too.
- **Taking cash out:** hold the wallet and right-click to open it. Each coin and bill shows how many are inside: click one to take one out, or Shift-click to take a stack. **Take all** empties it into your inventory. You can also drop cash into the **Add** slot, or Shift-click cash in your inventory to put it in.
- **Paying:** when you pay cash at a shop, the server shop or a villager, loose cash in your inventory is spent first, then cash from your wallet. Change goes back into the wallet, so you never need a free slot for it.
- **Getting paid:** ATM withdrawals, cash advances and anything a villager or the server shop pays you go into your wallet.
- **Depositing:** **Deposit all** at an ATM (or **Pay all cash** on a credit card) uses your wallet's cash too.
- Hover over a wallet to see what's inside. A wallet with cash in it shows a bill sticking out. Like any item, it drops when you die, with the cash still inside.

**Using an ATM:** right-click it.

- **No card inserted:** type the PIN you want and press **Debit** or **Credit** to get a new card (see below).
- **Card inserted:** put a card in the slot at the top left and type its PIN. Every other action needs both. With a credit card inserted, the buttons work the credit card instead (see [Credit cards](#credit-cards)).

With a debit card:

- **Balance:** press **Balance** to see what's in the card's account.
- **Withdraw:** type an amount (like `37.41`) and press **Withdraw**, or use the $20, $50 and $100 buttons. You get the fewest coins and bills, like a real ATM. The ATM refuses if your inventory can't hold the cash, so nothing gets dropped on the ground.
- **Deposit:** put coins and bills in the deposit slots (shift-click works) and press **Deposit**, or press **Deposit all** to bank every coin and bill in your inventory. Anything left in the slots comes back to you when you close the screen.

### Debit cards

Your bank balance is only reachable with a debit card. Coins and bills in your inventory work without one.

- **Getting one:** at an ATM with no card inserted, type the PIN you want (exactly 4 digits) and press **Debit**. Your first card is free. After that, each new card costs $5 from your account (the `atm.cardReplacementFee` setting).
- **One card at a time:** each account has one working card. Getting a new card cancels the old one, so a lost or stolen card can be shut off from any ATM.
- **Wrong PINs:** three wrong PINs in a row lock the card. A right PIN before then resets the count. The owner gets a new card at an ATM to unlock their account.
- **Lending:** a card pays from its owner's account, not from whoever holds it. Anyone who knows the PIN can use it, so you can lend your card to a friend. Only give the PIN to people you trust.
- Hover over a card to see whose it is and its last four digits.

### Credit cards

A credit card lets you spend money you don't have yet, then pay the bill later. What you owe on it is separate from your bank balance.

- **Getting one:** at an ATM with no card inserted, type the PIN you want and press **Credit**. The first one is free and starts with a **$500 limit**. A replacement cancels the old card and adds the $5 card fee to your bill. Like debit cards, a credit card works for anyone with its PIN, and three wrong PINs lock it.
- **Spending:** pay by card at player shops, the server shop and villagers (see [Paying at shops](#paying-at-shops)). Player shop owners are paid straight away; you owe the amount on your card. You can't go over your limit.
- **Statements:** every 7 Minecraft days (about 2 hours 20 minutes of play) you get a statement in chat showing what you owe and a minimum payment (10%, at least $10). The payment is due by the next statement.
  - **Pay it all** and there's no interest, and your limit goes up by $100 (up to $5,000).
  - **Pay at least the minimum** and you're charged 5% interest on the rest, but your limit still goes up.
  - **Pay nothing** and the minimum is taken from your bank account automatically. No fee, but no limit increase either.
  - **If your bank can't cover it**, it's a missed payment: a $25 late fee plus interest, and your limit drops by $100 (never below $250). After **3 missed payments in a row** the card is frozen until you pay it off.
  - New purchases after a statement aren't charged interest until they show up on a statement and go unpaid.
- **Paying the bill:** insert the credit card at an ATM and type its PIN:
  - **Pay bill** pays from your bank account. Type an amount first, or leave it blank to pay everything.
  - **Pay in cash** uses the coins and bills in the slots; **Pay all cash** uses all the cash you're carrying. Only what you owe is taken; the rest comes back as change.
  - **Balance** shows what you owe, your limit, and what to pay by when.
- **Cash advances:** with a credit card inserted, **Cash advance** (or the $20/$50/$100 buttons) gives you cash on credit. Each advance adds a 5% fee (at least $5) to your bill.
- Type `/credit` any time for the full picture: what you owe, the last statement, the minimum and when the next statement comes. You also get a reminder when you join if a payment is due.
- Type `/credit history` for the itemized bill: every purchase (with the shop or villager and what you bought), cash advance, fee, interest charge, payment and statement, newest first. Add a page number to see older lines; the last 60 are kept.

Statement times use the world's game time, which keeps counting while the world runs (sleeping and `/time set` don't skip it). In singleplayer it only passes while you play.

### Paying at shops

The buy screen shows the card you'll pay with and how much cash you have. If you carry more than one card (say a debit and a credit card), press the small **⇄** button next to the card to switch. Then pay one of two ways:

- **Card:** type the card's PIN and press **Card**. A debit card moves the money from its bank account to the shop owner; a credit card puts it on the card's bill.
- **Cash:** press **Cash**. Your coins and bills are handed over, and any change comes back as the fewest coins and bills. The shop owner still gets paid into their bank account. If your inventory can't hold the change, the purchase is refused.

The server shop takes card or cash the same way. When you sell to it, it pays you in cash.

### Jobs

Jobs pay you for ordinary work: mining, chopping, digging, farming, hunting and fishing.

**Crafting a job board:** three paper over three planks, on two sticks.

```text
P P P
W W W
S   S
```

**Joining:** right-click a job board to see every job, your level in each and what it pays (hover a job). Press **Join** to sign up, up to **3 jobs at once**, or **Quit** to make room. You keep your level when you quit, so coming back picks up where you left off.

| Job | Gets paid for |
| --- | --- |
| Miner | Stone and ores. Diamond ore pays $5.00, ancient debris $15.00, plain stone 2 cents |
| Lumberjack | Logs from trees (10 cents each) |
| Digger | Dirt, sand, gravel and clay |
| Farmer | Fully grown crops, melons and pumpkins, sugar cane, and breeding animals |
| Hunter | Hostile mobs. Zombies pay $1.00, endermen $2.50, the Ender Dragon $250 |
| Fisher | Fish and fishing treasure |

- **Pay:** each block, mob or catch pays straight into your bank account, and a "+$5.00 Miner" note pops up above the hotbar. Pay is deposited every few seconds as one transaction per job, so your transaction history stays readable.
- **Levels:** work earns job XP. Level 2 needs 100 XP, level 3 needs 200 more, and so on. Each level adds 2% to that job's pay, up to level 50 (+98%). You get a message and a sound when you level up.
- **Fair play:** blocks you placed yourself never pay, so placing and breaking the same ore over and over earns nothing. Crops are the exception: they pay when fully grown, even though you planted them. Mobs from monster spawners don't pay (you can turn that on), and creative mode earns nothing.
- `/jobs` shows your jobs, levels and XP; `/jobs info <job>` shows what a job pays at your level.

**Making your own jobs:** jobs are datapack files, so servers can change the pay or add new jobs. Put a file at `data/<namespace>/jobs/<name>.json`:

```json
{
  "name": "Miner",
  "icon": "minecraft:iron_pickaxe",
  "order": 1,
  "break": [ { "block": "#c:ores/diamond", "pay": 5.00, "xp": 30 },
             { "block": "minecraft:wheat", "pay": 0.10, "xp": 2, "mature": true } ],
  "kill":  [ { "entity": "minecraft:creeper", "pay": 1.50, "xp": 10 },
             { "entity": "category:monster", "pay": 0.75, "xp": 5 } ],
  "fish":  [ { "item": "minecraft:cod", "pay": 0.50, "xp": 5 } ],
  "breed": [ { "entity": "minecraft:cow", "pay": 0.50, "xp": 5 } ]
}
```

Targets can be an id, a `#tag`, or for mobs `category:<name>`. The first matching line in each list wins, so put specific lines before catch-alls. `"mature": true` means "only fully grown crops". Lines that mention blocks, mobs or items from mods that aren't installed are skipped. To replace a built-in job, use the same path (`data/reikoeconomy/jobs/miner.json`) in your datapack. Run `/reload` after editing.

### Villagers and wandering traders

Villagers and wandering traders no longer deal in emeralds. Right-click one as usual and you get a money version of the trading screen:

- **Prices in dollars.** Every emerald in a vanilla trade is worth $10 (the `villagers.emeraldValue` setting), so a trade that cost 3 emeralds costs $30.
- **Buying:** pick a trade on the left, choose how many times with **-** and **+** (hold Shift to jump to the most or least), then pay by **Card** (type your PIN) or **Cash** (change comes back in coins and bills). Villagers keep the money, so buying from them takes money out of the economy.
- **Selling:** trades where the villager used to give you emeralds (like 20 wheat for 1 emerald) now pay cash. Press **Sell for $X cash**.
- **Other items:** trades that also want an item, like the book for an enchanted book, still take that item from your inventory.
- **Vanilla still applies.** Discounts from curing zombie villagers, Hero of the Village and good reputation lower the price (shown in green, with the usual price on hover). Popular trades cost more (shown in red). Villagers still restock, gain experience, level up and unlock new trades.

Trades that are sold out are greyed out until the villager restocks at its job site. To keep vanilla emerald trading instead, turn off `villagers.useMoney`.

### Shops

**Crafting a Shop:** a chest with a gold ingot above it, surrounded by planks.

```text
P G P
P C P
P P P
```

**Setting one up (owner):** right-click your shop. The left side has the item and prices; the right side has the stock and your inventory.

1. Click the gold slot with an item to sell it. The stack size you hold is one bundle, so clicking with 16 cobblestone sells 16 at a time. Right-click instead to sell them one at a time.
2. Type the prices per bundle and press **Save**:
   - **Players pay** is what players pay to buy a bundle from you.
   - **Shop pays** is what you pay players who sell you a bundle.

   Either can be blank to turn that side off, so a shop can sell, buy, or both. The shop can't pay more than it charges, or anyone could buy a bundle and sell it straight back to you at a profit.
3. Put stock in the slots on the right. Only the item for sale fits.

The item and its prices float above the shop, with "Out of stock" when it runs dry. You can't switch to a different item while stock remains.

**Restocking with hoppers:** a hopper (or a pipe from another mod) pointing into a shop adds stock. Only the shop's item goes in; anything else stays in the hopper. Nothing can pull stock out, so a hopper under someone's shop gets nothing.

**Shops that buy:** when you set a "shop pays" price, other players can sell the item to your shop. They get cash (into their wallet if they carry one), the money comes out of your bank account, and the items go into the stock, ready to sell again. The shop stops buying when its stock is full or you can't afford another bundle.

**Shop alerts:** you get a message when one of your shops sells out, fills up, or stops buying because your bank balance is too low. If you're offline, the alerts wait until you join, along with how much your shops made and spent while you were away.

**Buying (everyone else):** right-click the shop, choose how many bundles with **-** and **+** (hold Shift for steps of 10), then pay by **Card** (with your PIN) or **Cash** (see [Paying at shops](#paying-at-shops)). If the owner changes the price while your screen is open, the purchase is refused so you never pay a price you didn't see.

**Selling (everyone else):** if the shop buys, its screen has a sell section showing what it pays, how many bundles you have and how many it wants. Press **Sell 1** for one bundle or **Sell all** for as many as the shop will take. Only exact matches count, so damaged or enchanted copies aren't accepted.

**Finding shops:** `/shops find <item>` lists every shop that sells or buys an item, anywhere in the world, even in areas nobody has loaded. Type part of the name (`/shops find iron` finds Iron Ingot, Iron Ore and so on) or press Tab to pick from the items shops actually trade. With no item, it searches for what you're holding.

- **Where to buy** lists the cheapest first, comparing the price per item, so 2 for $12.00 counts as $6.00 each. Shops that are sold out go last.
- **Where to sell** lists the best price first. Shops that are full, or whose owner can't afford more, go last.
- Each line shows the owner, how far away the shop is and in which direction (like "120 blocks NE"). Hover over a line for the coordinates and stock, and click it to copy the coordinates.

`/shops near` (or just `/shops`) lists the shops in your dimension, nearest first, and `/shops mine` lists your own shops with what's sold out or full. Shops from before v0.11.0 show up once their area has been loaded.

**Server shops (admins):** place a Server Shop from the creative tab and right-click it to set the item, the price players pay to buy, and the price the shop pays to buy it back. Leave either price blank or 0 to turn that side off. After that, everyone gets the trade screen. Admins sneak + right-click with an empty hand to change it again.

**Protection:**
- Only the owner (or an admin) can break a shop.
- Shops survive explosions, withers and the ender dragon.
- Hoppers and pipes can add stock but can't pull it out.

Admins can buy from other players' shops like anyone else, and sneak + right-click with an empty hand to manage them.

### Taxes and fees

Money taken as tax disappears from the economy; nobody receives it. Jobs and selling keep adding money to a world, so without something taking it back out, prices tend to rise over time. Taxes are on by default with low rates, and admins can change or turn off each one (see [Settings](#settings)).

| Tax | Default | How it works |
| --- | --- | --- |
| Sales tax | 5% | Added on top of the price at player shops and server shops, however you pay. The shop owner still gets the full price. The buy screen shows the price plus tax; hover over **Card** or **Cash** for the breakdown. On a credit card, the tax is its own line on the bill. Selling to a shop and villager trades aren't taxed. |
| `/pay` fee | 2% | The sender pays it on top, so `/pay Alex 100` gives Alex exactly $100 and costs you $102. |
| ATM fee | $1.00 | Taken from your bank for each debit card withdrawal. Hover over **Withdraw** to see it. Credit card cash advances have their own fee instead. |
| Wealth tax | 1% over $10,000, weekly | Every 7 Minecraft days, 1% of the part of each bank balance above $10,000 is taken, from online and offline players alike. A $25,000 balance pays $150. Players who were offline are told when they next join. |

Type `/taxes` to see the server's taxes, when the next wealth tax comes, and what you'd pay at your current balance. Admins can type `/eco taxes` to see how much each tax has taken out of the economy and how much money is in bank accounts, or `/eco taxes collect` to take the wealth tax right away.

Taxes and fees show up in `/transactions` like any other balance change.

### Commands

Permission nodes are in brackets:

| Command | Who | What it does |
| --- | --- | --- |
| `/balance` or `/bal` | Everyone `[reikoeconomy.command.balance]` | Shows your balance |
| `/bal <player>` | Everyone `[reikoeconomy.command.balance.others]` | Shows another player's balance |
| `/pay <player> <amount>` | Everyone `[reikoeconomy.command.pay]` | Sends money, e.g. `/pay Steve 12.50`. Works when they're offline |
| `/baltop [page]` | Everyone `[reikoeconomy.command.baltop]` | Richest players, plus the total in all accounts |
| `/transactions [page]` | Everyone `[reikoeconomy.command.history]` | Your recent transactions |
| `/credit` | Everyone `[reikoeconomy.command.credit]` | Your credit card: what you owe, the limit and what's due |
| `/credit history [page]` | Everyone `[reikoeconomy.command.credit]` | Your itemized credit card bill, newest first |
| `/jobs` | Everyone `[reikoeconomy.command.jobs]` | Your jobs, levels and XP |
| `/jobs info <job>` | Everyone `[reikoeconomy.command.jobs]` | What a job pays at your level |
| `/taxes` | Everyone `[reikoeconomy.command.taxes]` | The server's taxes and your next wealth tax |
| `/shops find [item]` | Everyone `[reikoeconomy.command.shops]` | Where to buy and sell an item (the held item if none is named) |
| `/shops near [page]` | Everyone `[reikoeconomy.command.shops]` | Shops in your dimension, nearest first |
| `/shops mine [page]` | Everyone `[reikoeconomy.command.shops]` | Your shops, with what's sold out or full |
| `/eco give\|take\|set <players> <amount>` | Ops `[reikoeconomy.command.admin]` | Changes balances |
| `/eco reset <players>` | Ops `[reikoeconomy.command.admin]` | Resets balances to the starting amount |
| `/eco history <player> [page]` | Ops `[reikoeconomy.command.admin]` | A player's recent transactions |
| `/eco credit <player>` | Ops `[reikoeconomy.command.admin]` | A player's credit card account |
| `/eco credit <player> limit <amount>` | Ops `[reikoeconomy.command.admin]` | Sets a player's credit limit |
| `/eco credit <player> forgive` | Ops `[reikoeconomy.command.admin]` | Clears a player's credit card debt and unfreezes the card |
| `/eco credit <player> statement` | Ops `[reikoeconomy.command.admin]` | Makes the player's statement now instead of waiting |
| `/eco taxes` | Ops `[reikoeconomy.command.admin]` | Tax rates, what each tax has taken, and the money in bank accounts |
| `/eco taxes collect` | Ops `[reikoeconomy.command.admin]` | Takes the wealth tax now instead of waiting |

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
| `villagers.useMoney` | `true` | Villagers trade for card or cash instead of emeralds |
| `villagers.emeraldValue` | `10.0` | What one emerald in a vanilla trade is worth |
| `credit.enabled` | `true` | Players can get and use credit cards |
| `credit.startingLimit` | `500.0` | Limit of a new credit card |
| `credit.maxLimit` | `5000.0` | Highest a limit can grow to |
| `credit.limitIncrease` | `100.0` | Limit increase for each statement paid on time |
| `credit.limitDecrease` | `100.0` | Limit decrease for each missed payment (never below half the starting limit) |
| `credit.statementDays` | `7` | Minecraft days between statements |
| `credit.interestRate` | `5.0` | Interest, in percent, on the unpaid part of a statement |
| `credit.minimumPaymentPercent` | `10.0` | Minimum payment, in percent of the statement |
| `credit.minimumPayment` | `10.0` | Smallest minimum payment |
| `credit.lateFee` | `25.0` | Fee for a missed payment the bank couldn't cover |
| `credit.freezeAfterMissedPayments` | `3` | Missed payments in a row before the card freezes |
| `credit.cashAdvanceFeePercent` | `5.0` | Cash advance fee, in percent |
| `credit.cashAdvanceMinimumFee` | `5.0` | Smallest cash advance fee |
| `jobs.enabled` | `true` | Players can join jobs and get paid |
| `jobs.maxJobs` | `3` | Jobs a player can have at once |
| `jobs.maxLevel` | `50` | Highest job level |
| `jobs.payBonusPerLevel` | `2.0` | Extra pay per level above 1, in percent |
| `jobs.payMultiplier` | `1.0` | Multiplies all job pay, to fit your server's prices |
| `jobs.paySpawnerMobs` | `false` | Pay for mobs from monster spawners |
| `taxes.enabled` | `true` | Turns all taxes and fees on or off at once |
| `taxes.salesTaxPercent` | `5.0` | Sales tax added on top of shop prices, in percent |
| `taxes.payFeePercent` | `2.0` | Fee the sender pays on `/pay`, in percent |
| `taxes.wealthTaxPercent` | `1.0` | Wealth tax on the part of a balance above the threshold, in percent |
| `taxes.wealthTaxThreshold` | `10000.0` | Balances up to this pay no wealth tax |
| `taxes.wealthTaxDays` | `7` | Minecraft days between wealth taxes |
| `taxes.atmWithdrawalFee` | `1.0` | Flat fee for each debit card ATM withdrawal |
| `taxes.atmWithdrawalFeePercent` | `0.0` | Extra ATM fee, in percent of the amount |

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
├── core/                    EconomyService, Ledger, CreditLedger, TaxLedger and TransactionLog (saved data), Money, cash, card, credit and tax logic
├── bank/                    ATM withdrawals and deposits, paying with cash and wallets
├── item/                    coins, bills, wallets, debit and credit cards
├── block/                   shop, ATM and job board blocks, shop block entities
├── menu/                    shop and ATM menus (shared by client and server)
├── shop/                    access rules, card and cash payments, owner alerts, the list of shops, hopper access, inventory sync
├── villager/                villager prices in money and paying for villager trades
├── jobs/                    job files, levels, pay and the placed-block tracker
├── network/                 packets between client and server
├── client/                  screens (including the villager trading screen), the floating-item renderer, client-only handlers
├── command/                 /bal /pay /baltop /transactions /eco and permission nodes
├── config/                  server settings
├── event/                   login, shop protection, hopper access, transaction-log hooks, credit statements and the wealth tax
├── registry/                blocks, items, data components, block entities, menus, creative tab
└── gametest/                in-game tests
```

## License

GPL-3.0. See [LICENSE](LICENSE).
